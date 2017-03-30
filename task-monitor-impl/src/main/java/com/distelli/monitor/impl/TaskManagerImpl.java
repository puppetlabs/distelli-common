package com.distelli.monitor.impl;

import java.util.Arrays;
import java.util.HashMap;
import com.distelli.monitor.Monitor;
import com.distelli.monitor.MonitorInfo;
import com.distelli.monitor.TaskManager;
import com.distelli.monitor.TaskState;
import com.distelli.monitor.TaskBuilder;
import com.distelli.monitor.TaskInfo;
import com.distelli.monitor.TaskFunction;
import com.distelli.monitor.TaskContext;
import java.util.Set;
import com.distelli.persistence.PageIterator;
import java.util.List;
import com.distelli.persistence.TableDescription;
import com.distelli.persistence.AttrType;
import com.distelli.persistence.Index;
import com.distelli.persistence.UpdateItemBuilder;
import javax.persistence.RollbackException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.distelli.utils.LongSortKey.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.distelli.jackson.transform.TransformModule;
import java.util.Collections;
import com.distelli.utils.CompactUUID;
import java.io.StringWriter;
import java.io.PrintWriter;
import javax.inject.Inject;
import com.distelli.monitor.Sequence;
import com.fasterxml.jackson.core.type.TypeReference;
import javax.inject.Singleton;

/**
 * If we loose DB connection or the JVM is `kill -9`ed, then tasks in these states will
 * move out of these states by these mechanisms:
 *
 *  QUEUED: startRunnableTasks()
 *  RUNNING: releaseLocksForMonitorId() when broken monitor is found.
 *  WAITING_FOR_PREREQUISITE: releaseLocks("_TASK"+longToSortKey(prerequisiteId))
 *  WAITING_FOR_INTERVAL: releaseLocksForMonitorId() when broken monitor is found.
 *  WAITING_FOR_LOCK: releaseLocks(lockId)
 */
@Singleton
public class TaskManagerImpl implements TaskManager {
    private static final int POLL_INTERVAL_MS = 10000;
    private static final int MAX_TASKS_IN_INTERVAL = 100;
    private static final String TASK_ID_NONE = "#";
    private static final String MONITOR_ID_QUEUED = "#";
    private static final String MONITOR_ID_WAITING = "$";
    private static final Logger LOG = LoggerFactory.getLogger(TaskManagerImpl.class);

    @Inject
    private Monitor _monitor;
    @Inject
    private ScheduledExecutorService _executor;
    @Inject
    private Map<String, TaskFunction> _taskFunctions;
    @Inject
    private Sequence _sequence;

    private Index<Lock> _locks;
    private Index<Lock> _locksForMonitor;
    private Index<Task> _tasks;
    private Index<Task> _tasksForMonitor;
    private Index<Task> _tasksForEntity;
    private final ObjectMapper _om = new ObjectMapper();
    private final Map<Long, DelayedTask> _delayedTasks =
        new ConcurrentHashMap<>();

    private AtomicInteger _spawnedCount = new AtomicInteger(0);

    // synchronized(this):
    private ScheduledFuture<?> _monitorTasks;
    // synchronized(this):
    private Set<Future<?>> _spawnedFutures = new HashSet<>();
    // synchronized(this):
    private boolean _hasShutdownHook = false;

    private static class DelayedTask {
        private DelayedTask(long millisRemaining) {
            this.millisTimeBegin = milliTime();
            this.millisRemaining = millisRemaining;
        }
        public long millisTimeBegin;
        public long millisRemaining;
    }

    public static class TasksTable {
        public static TableDescription getTableDescription() {
            return TableDescription.builder()
                .tableName("monitor-tasks")
                // Query on task id:
                .index((idx) -> idx
                       .hashKey("id", AttrType.NUM))
                // Query on monitor ids, or "special" state:
                //    '#' - Runnable
                //    '$' - Waiting on time remaining
                .index((idx) -> idx
                       .indexName("mid-index")
                       .hashKey("mid", AttrType.STR))
                // Query on entities:
                .index((idx) -> idx
                       .indexName("ety-eid-index")
                       .hashKey("ety", AttrType.STR)
                       .rangeKey("eid", AttrType.STR))
                .build();
        }
    }

    public static class LocksTable {
        // Entries in this table are either locks or tasks waiting
        // on a lock.
        public static TableDescription getTableDescription() {
            return TableDescription.builder()
                .tableName("monitor-locks")
                .index((idx) -> idx
                       .hashKey("lid", AttrType.STR)
                       // "actual" locks have tid=TASK_ID_NONE:
                       .rangeKey("tid", AttrType.STR))
                // Query on monitor ids:
                .index((idx) -> idx
                       .indexName("mid-index")
                       .hashKey("mid", AttrType.STR))
                .build();
        }
    }

    @Override
    public List<? extends TaskInfo> getTasksByEntityType(String entityType, PageIterator iter) {
        return _tasksForEntity.queryItems(entityType, iter).list();
    }

    @Override
    public TaskInfo getTask(Long taskId) {
        return _tasks.getItem(taskId, null);
    }

    @Override
    public TaskBuilder createTask() {
        return new TaskBuilderImpl() {
            @Override
            public TaskInfo build() {
                Task task = (Task)super.build();
                task.taskId = _sequence.next(_tasks.getTableName());
                return task;
            }
        };
    }

    @Override
    public void addTask(TaskInfo taskInfo) {
        if ( ! (taskInfo instanceof Task) ) {
            throw new IllegalArgumentException("TaskInfo must be created from TaskManager.createTask()");
        }
        Task task = (Task)taskInfo;
        if ( null == task.getEntityType() ) {
            throw new IllegalArgumentException("missing task.entityType");
        }
        if ( null == task.getEntityId() ) {
            throw new IllegalArgumentException("missing task.entityId");
        }
        if ( null == task.getTaskId() ) {
            throw new IllegalStateException("_sequence.next("+_tasks.getTableName()+") returned null!");
        }
        TaskFunction taskFunction =
            _taskFunctions.get(task.getEntityType());
        if ( null == taskFunction ) {
            throw new IllegalArgumentException(
                "missing TaskFunction for task.entityType="+task.getEntityType());
        }
        if ( null == task.getMillisecondsRemaining() ) {
            task.taskState = TaskState.QUEUED;
        } else {
            task.taskState = TaskState.WAITING_FOR_INTERVAL;
        }
        task.monitorId = MONITOR_ID_QUEUED;
        // Reset the task (just in case):
        task.startTime = null;
        task.endTime = null;
        task.errorMessage = null;
        task.errorId = null;
        task.errorMessageStackTrace = null;
        task.runCount = 0L;
        task.canceledBy = null;
        // Save the task:
        _tasks.putItemOrThrow(task);
        // Dispatch:
        submitRunTask(task.getTaskId());
    }

    // Marks a task as "to be canceled":
    @Override
    public void cancelTask(String canceledBy, long taskId) {
        try {
            _tasks.updateItem(taskId, null)
                .set("cancel", canceledBy)
                .when((expr) -> expr.exists("mid"));
        } catch ( RollbackException ex ) {
            LOG.debug("Attempt to cancel taskId="+taskId+" that is in a final state, ignoring");
            return;
        }
        try {
            _tasks.updateItem(taskId, null)
                .set("mid", MONITOR_ID_QUEUED)
                .when((expr) -> expr.beginsWith("mid", "$"));
        } catch ( RollbackException ex ) {
            return;
        }
        // We moved the task out of waiting, so let's execute it:
        submitRunTask(taskId);
    }

    @Override
    public synchronized void monitorTaskQueue() {
        if ( null != _monitorTasks ) return;
        _monitorTasks = _executor.scheduleAtFixedRate(
            this::startRunnableTasks,
            ThreadLocalRandom.current().nextLong(POLL_INTERVAL_MS),
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS);
        TaskManagerImpl taskManager = this;
        if ( _hasShutdownHook ) return;
        Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        stopTaskQueueMonitor(true);
                    } catch ( Throwable ex ) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            });
        _hasShutdownHook = true;
    }

    @Override
    public void stopTaskQueueMonitor(boolean mayInterruptIfRunning) {
        synchronized ( this ) {
            if ( null == _monitorTasks ) return;
            _monitorTasks.cancel(false);
            _monitorTasks = null;
        }
        for ( Long taskId : _delayedTasks.keySet() ) {
            updateDelayedTask(taskId, null);
        }
        synchronized ( this ) {
            if ( null != _monitorTasks ) return;
            for ( Future<?> future : _spawnedFutures ) {
                future.cancel(mayInterruptIfRunning);
            }
            _spawnedFutures.clear();
            while ( null == _monitorTasks && _spawnedCount.get() > 0 ) {
                try {
                    wait();
                } catch ( InterruptedException ex ) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }


    public void releaseLocksForMonitorId(String monitorId) {
        LOG.debug("Releasing locks for monitorId="+monitorId);
        // Release locks on the "locks" table:
        for ( PageIterator iter : new PageIterator() ) {
            for ( Lock lock : _locksForMonitor.queryItems(monitorId, iter).list() ) {
                // Remove the queued mark for the running task:
                if ( null != lock.runningTaskId ) {
                    _locks.deleteItem(lock.lockId, longToSortKey(lock.runningTaskId));
                }

                // Mark next task as runnable:
                Long nextTaskId = unblockWaitingTask(lock.lockId);
                try {
                    // Release the lock:
                    if ( null != nextTaskId ) {
                        _locks.updateItem(lock.lockId, TASK_ID_NONE)
                            .remove("mid")
                            .remove("rtid")
                            .when((expr) -> expr.eq("mid", monitorId));
                    }
                } catch ( RollbackException ex ) {
                    LOG.debug("LostLockException: releaseLocksForMonitorId="+monitorId+" lockId="+
                              lock.lockId+" runningTaskId="+lock.runningTaskId);
                    continue;
                }
                if ( null != nextTaskId ) submitRunTask(nextTaskId);
            }
        }
        // Release locks on individual "tasks":
        for ( PageIterator iter : new PageIterator() ) {
            for ( Task task : _tasksForMonitor.queryItems(monitorId, iter).list() ) {
                try {
                    _tasks.updateItem(task.getTaskId(), null)
                        .set("mid", MONITOR_ID_QUEUED)
                        .when((expr) -> expr.eq("mid", monitorId));
                } catch ( RollbackException ex ) {
                    LOG.debug("LostLockException: releaseLocksForMonitorId="+monitorId+" taskId="+
                              task.getTaskId());
                    continue;
                }
                submitRunTask(task.getTaskId());
            }
        }
    }

    @Inject
    protected TaskManagerImpl(Index.Factory indexFactory) {
        _om.registerModule(createTransforms(new TransformModule()));

        String[] noEncrypt = new String[]{"cnt", "tic"};
        _tasks = indexFactory.create(Task.class)
            .withTableDescription(TasksTable.getTableDescription())
            .withConvertValue(_om::convertValue)
            .withNoEncrypt(noEncrypt)
            .build();

        _tasksForMonitor = indexFactory.create(Task.class)
            .withTableDescription(TasksTable.getTableDescription(), "mid-index")
            .withConvertValue(_om::convertValue)
            .withNoEncrypt(noEncrypt)
            .build();

        _tasksForEntity = indexFactory.create(Task.class)
            .withTableDescription(TasksTable.getTableDescription(), "ety-eid-index")
            .withConvertValue(_om::convertValue)
            .withNoEncrypt(noEncrypt)
            .build();

        noEncrypt = new String[]{"mid", "agn"};
        _locks = indexFactory.create(Lock.class)
            .withNoEncrypt(noEncrypt)
            .withTableDescription(LocksTable.getTableDescription())
            .withConvertValue(_om::convertValue)
            .build();

        _locksForMonitor = indexFactory.create(Lock.class)
            .withNoEncrypt(noEncrypt)
            .withTableDescription(LocksTable.getTableDescription(), "mid-index")
            .withConvertValue(_om::convertValue)
            .build();
    }

    private TransformModule createTransforms(TransformModule module) {
        module.createTransform(Task.class)
            .put("id", Long.class, "taskId")
            .put("ety", String.class, "entityType")
            .put("eid", String.class, TaskManagerImpl::toEid, TaskManagerImpl::fromEid)
            .put("stat", String.class, TaskManagerImpl::toState, TaskManagerImpl::fromState)
            .put("lids", new TypeReference<Set<String>>(){}, "lockIds")
            .put("preq", new TypeReference<Set<Long>>(){}, "prerequisiteTaskIds")
            .put("mid", String.class, "monitorId")
            .put("st8", byte[].class, "checkpointData")
            .put("err", String.class, "errorMessage")
            .put("errT", String.class, "errorMessageStackTrace")
            .put("errId", String.class, "errorId")
            .put("ts", Long.class, "startTime")
            .put("tf", Long.class, "endTime")
            .put("cnt", Long.class, "runCount")
            .put("tic", Long.class, "millisecondsRemaining")
            .put("cancel", String.class, "canceledBy");
        module.createTransform(Lock.class)
            .put("lid", String.class, "lockId")
            .put("tid", String.class, "taskId")
            .put("rtid", Long.class, "runningTaskId")
            .put("mid", String.class, "monitorId")
            .put("agn", Long.class, "tasksQueued");
        return module;
    }

    private static String toState(Task task) {
        return toString(task.getTaskState());
    }

    private static void fromState(Task task, String state) {
        task.taskState = toTaskState(state);
    }

    private static String toString(TaskState taskState) {
        if ( null == taskState ) return null;
        switch ( taskState ) {
        case QUEUED: return "Q";
        case RUNNING: return "R";
        case WAITING_FOR_INTERVAL: return "T";
        case WAITING_FOR_PREREQUISITE: return "N";
        case WAITING_FOR_LOCK: return "L";
        case FAILED: return "F";
        case SUCCESS: return "S";
        case CANCELED: return "C";
        }
        throw new UnsupportedOperationException(
            "taskState="+taskState+" is not supported in TaskManagerImpl");
    }

    private static TaskState toTaskState(String state) {
        if ( null == state ) return null;
        switch ( state ) {
        case "Q": return TaskState.QUEUED;
        case "R": return TaskState.RUNNING;
        case "T": return TaskState.WAITING_FOR_INTERVAL;
        case "N": return TaskState.WAITING_FOR_PREREQUISITE;
        case "L": return TaskState.WAITING_FOR_LOCK;
        case "F": return TaskState.FAILED;
        case "S": return TaskState.SUCCESS;
        case "C": return TaskState.CANCELED;
        default:
            LOG.info("Unknown TaskState="+state);
        }
        return null;
    }

    // We add the taskId on the end to force sort order:
    private static String toEid(Task task) {
        return validEntityId(task.entityId) + "@" + longToSortKey(task.taskId);
    }

    private static void fromEid(Task task, String eid) {
        if ( null == eid || eid.length() < LONG_SORT_KEY_LENGTH ) return;
        task.entityId = eid.substring(0, eid.length()-LONG_SORT_KEY_LENGTH-1);
    }

    private static String validEntityId(String entityId) {
        if ( null == entityId ) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        return entityId;
    }

    private static long milliTime() {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    private synchronized void submitRunTask(long taskId) {
        if ( null == _monitorTasks ) return;
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        futureRef.set(
            _executor.submit(
                () -> {
                    try {
                        try {
                            _spawnedCount.incrementAndGet();
                            runTask(taskId);
                        } finally {
                            synchronized ( this ) {
                                _spawnedFutures.remove(futureRef.get());
                                if ( _spawnedCount.decrementAndGet() <= 0 ) {
                                    notifyAll();
                                }
                            }
                        }
                    } catch ( Throwable ex ) {
                        LOG.error("runTask("+taskId+"): "+ex.getMessage(), ex);
                    }
                }));
        _spawnedFutures.add(futureRef.get());
    }

    private synchronized void scheduleRunTask(long taskId, long interval) {
        if ( null == _monitorTasks ) return;
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        futureRef.set(
            _executor.schedule(
                () -> {
                    try {
                        try {
                            _spawnedCount.incrementAndGet();
                            runTask(taskId);
                        } finally {
                            synchronized ( this ) {
                                _spawnedFutures.remove(futureRef.get());
                                if ( _spawnedCount.decrementAndGet() <= 0 ) {
                                    notifyAll();
                                }
                            }
                        }
                    } catch ( Throwable ex ) {
                        LOG.error("runTask("+taskId+"): "+ex.getMessage(), ex);
                    }
                },
                interval,
                TimeUnit.MILLISECONDS));
        _spawnedFutures.add(futureRef.get());
    }

    private synchronized void scheduleDelayedTask(long taskId, long interval) {
        if ( null == _monitorTasks ) return;
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        futureRef.set(
            _executor.schedule(
                () -> {
                    try {
                        try {
                            _spawnedCount.incrementAndGet();
                            _monitor.monitor((monitorInfo) -> updateDelayedTask(taskId, monitorInfo));
                        } finally {
                            synchronized ( this ) {
                                _spawnedFutures.remove(futureRef.get());
                                if ( _spawnedCount.decrementAndGet() <= 0 ) {
                                    notifyAll();
                                }
                            }
                        }
                    } catch ( Throwable ex ) {
                        LOG.error("updateDelayedTask("+taskId+"): "+ex.getMessage(), ex);
                    }
                },
                interval,
                TimeUnit.MILLISECONDS));
        _spawnedFutures.add(futureRef.get());
    }

    private void monitorDelayedTask(Task task) {
        long taskId = task.getTaskId();
        DelayedTask delayedTask = new DelayedTask(task.getMillisecondsRemaining());
        synchronized ( delayedTask ) {
            if ( null != _delayedTasks.putIfAbsent(taskId, delayedTask) ) {
                LOG.debug("Already monitoring delayed taskId="+taskId);
                return;
            }
            long interval = Math.min(POLL_INTERVAL_MS, delayedTask.millisRemaining)
                - (milliTime() - delayedTask.millisTimeBegin);
            scheduleDelayedTask(taskId, interval);
            LOG.debug("Monitoring delayed taskId="+taskId);
        }
    }

    private void startRunnableTasks() {
        try {
            List<Task> tasks = _tasksForMonitor
                .queryItems(MONITOR_ID_QUEUED, new PageIterator().pageSize(MAX_TASKS_IN_INTERVAL))
                .list(Arrays.asList("id"));
            // Randomly distribute queued tasks:
            for ( Task task : tasks ) {
                long interval = ThreadLocalRandom.current().nextLong(POLL_INTERVAL_MS);
                scheduleRunTask(task.getTaskId(), interval);
            }
            // TODO: Occassionally find all MONITOR_ID_WAITING tasks to check if
            // cleanup needs to happen?

            // TODO: Poll for canceled tasks that we are running and interrupt them...
        } catch ( Throwable ex ) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void updateDelayedTask(final long taskId, MonitorInfo monitorInfo) {
        DelayedTask delayedTask = _delayedTasks.get(taskId);
        if ( null == delayedTask ) return;
        synchronized ( delayedTask ) {
            UpdateItemBuilder update = _tasks.updateItem(taskId, null);
            long now = milliTime();
            long newRemaining = delayedTask.millisRemaining - (now - delayedTask.millisTimeBegin);
            if ( newRemaining <= 0 || null == monitorInfo ) {
                update.remove("tic")
                    .set("mid", MONITOR_ID_QUEUED)
                    .set("stat", toString(TaskState.QUEUED));
            } else {
                update.set("tic", newRemaining)
                    .set("mid", monitorInfo.getMonitorId());
            }
            try {
                update.when((expr) -> expr.eq("tic", delayedTask.millisRemaining));
            } catch ( RollbackException ex ) {
                LOG.debug("Failed to update taskId="+taskId+" due to tic != "+delayedTask.millisRemaining);
                monitorInfo = null;
            }
            if ( newRemaining <= 0 || null == monitorInfo ) { // stop monitoring:
                _delayedTasks.remove(taskId, delayedTask);
                submitRunTask(taskId);
                return;
            }
            delayedTask.millisRemaining = newRemaining;
            delayedTask.millisTimeBegin = now;
            long interval = Math.min(POLL_INTERVAL_MS, delayedTask.millisRemaining)
                - (milliTime() - now);
            scheduleDelayedTask(taskId, interval);
        }
    }

    private void runTask(long taskId) {
        try {
            _monitor.monitor((monitorInfo) -> lockAndRunTask(taskId, monitorInfo));
        } catch ( Throwable ex ) {
            LOG.error("runTask("+taskId+") FAILED: "+ex.getMessage(), ex);
        }
    }

    private String getLockForTaskId(long taskId) {
        return "_TASK:"+longToSortKey(taskId);
    }

    private TaskState getTaskState(Task task) {
        if ( null == task ) return TaskState.FAILED;
        TaskState taskState = task.getTaskState();
        if ( null == taskState ) {
            LOG.error("Unexpected taskState=null for taskId="+task.getTaskId());
            return TaskState.FAILED;
        }
        return taskState;
    }

    private boolean acquireLocksAndPrerequisites(Task task, List<String> locksAcquired, MonitorInfo monitorInfo, AtomicReference<TaskState> finalTaskState) {
        Map<String, Long> lockIdToTaskId = new HashMap<>();
        List<String> lockIds = new ArrayList<>(task.getLockIds());
        lockIds.add(getLockForTaskId(task.getTaskId()));
        for ( Long prerequisiteId : task.getPrerequisiteTaskIds() ) {
            if ( null == prerequisiteId ) continue;
            String lockId = getLockForTaskId(prerequisiteId);
            lockIds.add(lockId);
            lockIdToTaskId.put(lockId, prerequisiteId);
        }
        Collections.sort(lockIds);
        for ( String lockId : lockIds ) {
            // Enqueue:
            _locks.putItem(new Lock(lockId, longToSortKey(task.getTaskId())));

            Long prerequisiteId = lockIdToTaskId.get(lockId);
            if ( null != prerequisiteId ) {
                if ( ! getTaskState(_tasks.getItem(prerequisiteId)).isTerminal() ) {
                    LOG.debug("Waiting on prerequisiteTaskId="+prerequisiteId+" for taskId="+task.getTaskId());
                    finalTaskState.set(TaskState.WAITING_FOR_PREREQUISITE);
                    return false;
                }
            }

            // Try to acquire:
            try {
                Lock lock = _locks.updateItem(lockId, TASK_ID_NONE)
                    .set("mid", monitorInfo.getMonitorId())
                    .set("rtid", task.getTaskId())
                    .increment("agn", 1)
                    .returnAllNew()
                    .when((expr) -> expr.not(expr.exists("mid")));
                locksAcquired.add(lockId);
            } catch ( RollbackException ex ) {
                LOG.debug("Unable to acquire lockId="+lockId+" for taskId="+task.getTaskId());

                // Failed to acquire, update "agn" to force a retry on unblockWaitingTask():
                _locks.updateItem(lockId, TASK_ID_NONE)
                    .increment("agn", 1)
                    .always();

                finalTaskState.set(TaskState.WAITING_FOR_LOCK);
                return false;
            }
        }
        return true;
    }

    private class TaskContextImpl implements TaskContext {
        private Task _task;
        private MonitorInfo _monitorInfo;
        private TaskContextImpl(Task task, MonitorInfo monitorInfo) {
            _task = task;
            monitorInfo = _monitorInfo;
        }
        @Override
        public TaskInfo getTaskInfo() {
            return _task;
        }
        @Override
        public void commitState(byte[] checkpointData) {
            if ( null == _monitorInfo.getMonitorId() ) {
                throw new IllegalStateException("commitState() can only be called within the context of TaskFunction.run()");
            }
            try {
                _tasks.updateItem(_task.getTaskId(), null)
                    .set("st8", checkpointData)
                    .when((expr) -> expr.eq("mid", _monitorInfo.getMonitorId()));
            } catch ( RollbackException ex ) {
                throw new LostLockException(_task.getTaskId());
            }
        }
    }

    private String getThreadName(TaskInfo task) {
        return String.format("TASK:0x%x", task.getTaskId());
    }

    private void lockAndRunTask(long taskId, MonitorInfo monitorInfo) {
        // Lock the task:
        Task task;
        List<String> locksAcquired = new ArrayList<>();
        String threadName = null;
        AtomicReference<TaskState> finalTaskState = new AtomicReference<>(TaskState.FAILED);
        try {
            task = _tasks.updateItem(taskId, null)
                .set("mid", monitorInfo.getMonitorId())
                .set("stat", toString(TaskState.RUNNING))
                .set("ts", System.currentTimeMillis())
                .increment("cnt", 1)
                .returnAllNew()
                .when((expr) -> expr.eq("mid", MONITOR_ID_QUEUED));
        } catch ( RollbackException ex ) {
            // Someone else already locked this task:
            LOG.debug("Something else is running taskId="+taskId);
            return;
        }
        boolean interrupted = false;
        boolean redispatch = false;
        try {
            if ( null != task.getCanceledBy() ) {
                setTaskToCanceled(taskId, monitorInfo.getMonitorId());
                finalTaskState.set(null);
                return;
            }
            if ( null != task.getMillisecondsRemaining() ) {
                monitorDelayedTask(task);
                finalTaskState.set(null);
                return;
            }

            if ( ! acquireLocksAndPrerequisites(task, locksAcquired, monitorInfo, finalTaskState) ) {
                return;
            }

            TaskFunction taskFunction = _taskFunctions.get(task.getEntityType());
            if ( null == taskFunction ) {
                LOG.info("Unsupported entityType="+task.getEntityType()+" taskId="+task.getTaskId());
                finalTaskState.set(TaskState.QUEUED);
                return;
            }

            threadName = Thread.currentThread().getName();
            Thread.currentThread().setName(getThreadName(task));

            TaskInfo taskInfo = null;
            Throwable err = null;
            try {
                LOG.debug("Running taskId="+task.getTaskId()+" entityType="+task.getEntityType()+" entityId="+task.getEntityId());
                taskInfo = taskFunction.run(new TaskContextImpl(task, monitorInfo));
                if ( null == taskInfo ) taskInfo = task;
            } catch ( Throwable ex ) {
                if ( ! Thread.interrupted() ) {
                    err = ex;
                } else {
                    interrupted = true;
                    LOG.debug("Ignoring interrupted thread exception "+ex.getMessage(), ex);
                    finalTaskState.set(TaskState.CANCELED);
                    return;
                }
            }
            redispatch = updateTaskStateTerminal(task, monitorInfo, taskInfo, err);
            finalTaskState.set(null);
        } catch ( LostLockException ex ) {
            LOG.error("Failing heartbeat "+monitorInfo.getMonitorId()+" due to taskId="+
                      taskId+": "+ex.getMessage(), ex);
            monitorInfo.forceHeartbeatFailure();
        } finally {
            if ( null != threadName ) {
                Thread.currentThread().setName(threadName);
            }
            if ( monitorInfo.hasFailedHeartbeat() ) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                interrupted |= updateTaskState(taskId, locksAcquired, finalTaskState.get(), monitorInfo, redispatch);
            } catch ( Throwable ex ) {
                LOG.error("Failing heartbeat "+monitorInfo.getMonitorId()+
                          " in updateTaskState due to taskId="+taskId+": "+
                          ex.getMessage(), ex);
                monitorInfo.forceHeartbeatFailure();
            }
            if ( interrupted || monitorInfo.hasFailedHeartbeat() ) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String monitorIdForState(TaskState state) {
        switch ( state ) {
        case QUEUED: return MONITOR_ID_QUEUED;
        case RUNNING:
        case WAITING_FOR_INTERVAL:
            // These states should have
            throw new IllegalStateException("TaskState="+state+" requires monitor locks!");
        case WAITING_FOR_PREREQUISITE: return MONITOR_ID_WAITING;
        case WAITING_FOR_LOCK: return MONITOR_ID_WAITING;
        case FAILED: return null;
        case SUCCESS: return null;
        case CANCELED: return MONITOR_ID_QUEUED;
        }
        throw new UnsupportedOperationException("TaskState="+state+" is not supported in monitorIdForState()");
    }

    private boolean updateTaskStateTerminal(Task task, MonitorInfo monitorInfo, TaskInfo taskInfo, Throwable ex) {
        UpdateItemBuilder update = _tasks.updateItem(task.getTaskId(), null);
        TaskState state = TaskState.SUCCESS;
        if ( null != ex ) {
            // Log a message with the full stack trace:
            String errorId = CompactUUID.randomUUID().toString();
            LOG.debug("Failed taskId="+task.getTaskId()+" errorId="+errorId+": "+ex.getMessage(), ex);

            StringWriter stackTrace = new StringWriter();
            stackTrace.append("on nodeName="+monitorInfo.getNodeName()+" ");
            ex.printStackTrace(new PrintWriter(stackTrace));
            update.set("err", ex.getMessage())
                .set("errId", errorId)
                .set("errT", stackTrace.toString());
            state = TaskState.FAILED;
        } else {
            if ( null != taskInfo.getCheckpointData() ) {
                update.set("st8", taskInfo.getCheckpointData());
            }
            if ( ! task.getLockIds().equals(taskInfo.getLockIds()) ) {
                state = TaskState.QUEUED;
                update.set("lids", taskInfo.getLockIds());
            }
            if ( ! task.getPrerequisiteTaskIds().equals(taskInfo.getPrerequisiteTaskIds()) ) {
                state = TaskState.QUEUED;
                update.set("preq", taskInfo.getPrerequisiteTaskIds());
            }
            if ( null != taskInfo.getMillisecondsRemaining() ) {
                state = TaskState.QUEUED;
                update.set("tic", taskInfo.getMillisecondsRemaining());
            }
            LOG.debug("taskId="+task.getTaskId()+" state="+state);
        }
        String monitorId = monitorIdForState(state);
        if ( null == monitorId ) {
            update.remove("mid");
        } else {
            update.set("mid", monitorId);
        }
        try {
            update.set("tf", System.currentTimeMillis())
                .set("stat", toString(state))
                .when((expr) -> expr.eq("mid", monitorInfo.getMonitorId()));
        } catch ( RollbackException rollbackEx ) {
            throw new LostLockException(task.getTaskId());
        }
        return TaskState.QUEUED == state;
    }

    private void setTaskToCanceled(long taskId, String monitorId) {
        try {
            _tasks.updateItem(taskId, null)
                .set("stat", toString(TaskState.CANCELED))
                .remove("mid")
                .when((expr) -> expr.eq("mid", monitorId));
        } catch ( RollbackException ex ) {
            throw new LostLockException(taskId);
        }
        LOG.debug("TaskId="+taskId+" is now in CANCELED state");
    }

    // Returns true if thread was interrupted...
    private boolean updateTaskState(long taskId, List<String> locksAcquired, TaskState finalState, MonitorInfo monitorInfo, boolean redispatch) {
        boolean interrupted = false;
        for ( int retry=0; retry < 3; retry++ ) {
            try {
                releaseLocks(locksAcquired, taskId, monitorInfo);
                if ( null != finalState ) {
                    String monitorId = monitorIdForState(finalState);

                    UpdateItemBuilder update = _tasks.updateItem(taskId, null);
                    if ( null == monitorId ) {
                        update.remove("mid");
                    } else {
                        update.set("mid", monitorId);
                    }
                    try {
                        update.set("stat", toString(finalState))
                            .when((expr) -> expr.eq("mid", monitorInfo.getMonitorId()));
                    } catch ( RollbackException ex ) {
                        throw new LostLockException(taskId);
                    }
                }
                if ( redispatch ) submitRunTask(taskId);
                return interrupted;
            } catch ( RuntimeException ex ) {
                if ( Thread.interrupted() || isInterruptedException(ex) ) {
                    interrupted = true;
                    LOG.debug("Interrupted in attempt to updateTaskState("+taskId+"): "+ex.getMessage(), ex);
                    continue;
                }
                throw ex;
            }
        }
        monitorInfo.forceHeartbeatFailure();
        LOG.error("Interrupted to many times, giving up on updateTaskState("+taskId+"), failing the monitor!");
        return interrupted;
    }

    private boolean isInterruptedException(Exception ex) {
        switch ( ex.getClass().getName() ) {
        case "com.amazonaws.AbortedException":
            return true;
        }
        return false;
    }

    private void releaseLocks(List<String> locks, Long taskId, MonitorInfo monitorInfo) {
        while ( ! locks.isEmpty() ) {
            String lockId = locks.get(locks.size()-1);

            // Remove our queued mark:
            if ( null != taskId ) {
                _locks.deleteItem(lockId, longToSortKey(taskId));
            }

            // Mark task as runnable:
            Long nextTaskId = unblockWaitingTask(lockId);
            try {
                // Release the lock:
                if ( null != nextTaskId ) {
                    _locks.updateItem(lockId, TASK_ID_NONE)
                        .remove("mid")
                        .remove("rtid")
                        .when((expr) -> expr.eq("mid", monitorInfo.getMonitorId()));
                }
            } catch ( RollbackException ex ) {
                throw new LostLockException(taskId);
            }
            locks.remove(locks.size()-1);
            if ( null == nextTaskId || ! _monitor.isActiveMonitor(monitorInfo) ) continue;
            submitRunTask(nextTaskId);
        }
    }

    private Long unblockWaitingTask(String lockId) {
        while ( true ) {
            Long tasksQueued = null;
            for ( PageIterator iter : new PageIterator().pageSize(2) ) {
                for ( Lock lock : _locks.queryItems(lockId, iter).list() ) {
                    if ( TASK_ID_NONE.equals(lock.taskId) ) {
                        tasksQueued = lock.tasksQueued;
                        continue;
                    }
                    Long taskId = sortKeyToLong(lock.taskId);
                    try {
                        _tasks.updateItem(taskId, null)
                            .set("mid", MONITOR_ID_QUEUED)
                            .set("stat", toString(TaskState.QUEUED))
                            .when((expr) -> expr.beginsWith("mid", "$"));
                    } catch ( RollbackException ex ) {
                        LOG.debug("taskId="+taskId+" was unable to set task as queued");
                        continue;
                    }
                    return taskId;
                }
            }
            if ( null == tasksQueued ) return null;
            try {
                Long finalTasksQueued = tasksQueued;
                _locks.deleteItem(lockId, TASK_ID_NONE, (expr) -> expr.eq("agn", finalTasksQueued));
            } catch ( RollbackException ex ) {
                LOG.debug("Retrying unblockWaitingTask("+lockId+")");
                continue;
            }
            return null;
        }
    }
}
