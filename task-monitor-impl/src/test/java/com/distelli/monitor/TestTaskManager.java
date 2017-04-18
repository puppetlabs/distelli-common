package com.distelli.monitor;

import com.distelli.crypto.impl.CryptoModule;
import com.distelli.monitor.impl.MonitorTaskModule;
import com.distelli.monitor.impl.SequenceImpl;
import com.distelli.monitor.impl.MonitorImpl;
import com.distelli.persistence.TableDescription;
import com.distelli.persistence.impl.PersistenceModule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;
import com.distelli.utils.Log4JConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;

public class TestTaskManager {
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(TestTaskManager.class);
    private static PersistenceModule getPersistenceModule() {
        String fileName = System.getenv("TEST_PERSISTENCE_CONFIG");
        if ( null == fileName ) {
            throw new RuntimeException(
                "\nThe TEST_PERSISTENCE_CONFIG environment variable must be set to the location of\n"+
                "a configuration file with content similar to this:\n"+
                "\t{\"tableNameFormat\":\"%s.unittest\",\n"+
                "\t \"endpoint\":\"ddb://us-east-1\",\n"+
                "\t \"profileName\":\"beta\"}\n"+
                "OR if using mysql (instead of ddb):\n"+
                "\t{\"tableNameFormat\":\"%s.unittest\",\n"+
                "\t \"endpoint\":\"mysql://beta.4e56d7f.us-east-1.rds.amazonaws.com:3306/beta?useSSL=true\",\n"+
                "\t \"user\":\"unittest\",\n"+
                "\t \"password\":\"topsecret\"}\n");
        }
        try {
            return new PersistenceModule(fileName);
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }
    static {
        Log4JConfigurator.configure(true);
        Log4JConfigurator.setLogLevel("org.apache.http", "INFO");
        Log4JConfigurator.setLogLevel("com.amazonaws", "INFO");
    }
    private static Injector INJECTOR = Guice.createInjector(
        getPersistenceModule(),
        new CryptoModule(),
        new MonitorTaskModule(),
        new AbstractModule() {
            @Override
            protected void configure() {
                MapBinder taskFunctionBinder = MapBinder.newMapBinder(binder(), String.class, TaskFunction.class);
                taskFunctionBinder.addBinding(TestTask.ENTITY_TYPE).to(
                    TestTask.class);
                bind(ScheduledExecutorService.class).toInstance(new ScheduledThreadPoolExecutor(10));
                ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();
                bind(Sequence.class).to(SequenceImpl.class).in(Singleton.class);
                Multibinder tableBinder = Multibinder.newSetBinder(binder(), TableDescription.class);
                tableBinder.addBinding().toInstance(
                    SequenceImpl.getTableDescription());
                bind(ProductVersion.class).toInstance(new ProductVersion() {
                    @Override
                    public String toString() {
                        return "UnitTesting task-monitor-impl";
                    }
                });
            }
        });
    @Singleton
    private static class TestTask implements TaskFunction {
        public static String ENTITY_TYPE = "unittest:task-monitor-impl";

        @Inject
        private TaskManager _taskManager;

        public CountDownLatch _latch = null;
        public List<Long> _tasksRan = null;
        public String _monitorIdFailed = null;
        public boolean _failMonitor = false;

        @Override
        public TaskInfo run(TaskContext ctx) throws Exception {
            try {
                String methodName = ctx.getTaskInfo().getEntityId();
                return (TaskInfo)getClass().getMethod(methodName, TaskContext.class).invoke(this, ctx);
            } finally {
                _latch.countDown();
            }
        }

        public TaskInfo testNoop(TaskContext ctx) throws Exception {
            if ( null != _tasksRan ) _tasksRan.add(ctx.getTaskInfo().getTaskId());
            return null;
        }

        public TaskInfo testPrerequisite(TaskContext ctx) throws Exception {
            if ( null != _tasksRan ) _tasksRan.add(ctx.getTaskInfo().getTaskId());
            Set<Long> expectedPrerequisites = OM.readValue(ctx.getTaskInfo().getCheckpointData(),
                                                           new TypeReference<Set<Long>>(){});
            for ( Long taskId : ctx.getTaskInfo().getPrerequisiteTaskIds() ) {
                // If this is not in the expectedPrerequisites, then
                // we don't care if that task is in a terminal state.
                if ( ! expectedPrerequisites.remove(taskId) ) continue;
                assertEquals(
                    "taskId="+taskId,
                    TaskState.SUCCESS,
                    _taskManager.getTask(taskId).getTaskState());
            }
            if ( ! expectedPrerequisites.isEmpty() ) {
                fail("Expected prerequisiteTaskIds="+expectedPrerequisites);
            }
            return null;
        }

        public TaskInfo testDelayed(TaskContext ctx) throws Exception {
            int remaining = Integer.parseInt(new String(ctx.getTaskInfo().getCheckpointData()));
            if ( --remaining <= 0 ) return null;
            return ctx.getTaskInfo().toBuilder()
                .millisecondsRemaining(200)
                .checkpointData((""+remaining).getBytes())
                .build();
        }

        public TaskInfo testDelayedForever(TaskContext ctx) throws Exception {
            return ctx.getTaskInfo().toBuilder()
                .millisecondsRemaining(60*5*1000)
                .build();
        }

        public TaskInfo testFailedMonitor(TaskContext ctx) throws Exception {
            if ( ! _failMonitor ) return null;
            LOG.info("Testing a force heartbeat failure");
            _monitorIdFailed = ctx.getMonitorInfo().getMonitorId();
            ctx.getMonitorInfo().forceHeartbeatFailure();
            return null;
        }
    }

    @Inject
    private TaskManager _taskManager;
    @Inject
    private TestTask _testTask;
    @Inject
    private MonitorImpl _monitor;

    @Before
    public void beforeTest() {
        INJECTOR.injectMembers(this);
        Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    _monitor.shutdownMonitor(false);
                }
            });
    }

    // This is a manual test that requires substantial time to run:
    @Test @Ignore
    public void testSystemOverload() throws Exception {
        final int taskCount = 50;
        CountDownLatch latch = new CountDownLatch(taskCount+1);
        _testTask._latch = latch;
        _taskManager.monitorTaskQueue();
        TaskInfo blockingTask = _taskManager.createTask()
            .entityType(TestTask.ENTITY_TYPE)
            .entityId("testNoop")
            .build();
        List<Long> prereqs = new ArrayList<>();
        prereqs.add(blockingTask.getTaskId());
        for ( int i=0; i < 50; i++ ) {
            TaskInfo waitingTask = _taskManager.createTask()
                .entityType(TestTask.ENTITY_TYPE)
                .entityId("testNoop")
                .prerequisiteTaskIds(prereqs)
                .build();
            _taskManager.addTask(waitingTask);
            prereqs.add(waitingTask.getTaskId());
        }
        _taskManager.addTask(blockingTask);
        latch.await();
        _taskManager.stopTaskQueueMonitor(false);
    }
    // If tasks are already queued that you want to simply run:
    @Test @Ignore
    public void waitForCompletion() throws Exception {
        CountDownLatch latch = new CountDownLatch(47);
        _testTask._latch = latch;
        _taskManager.monitorTaskQueue();
        latch.await();
        _taskManager.stopTaskQueueMonitor(false);
    }

    @Test
    public void testPrerequisites() throws Exception {
        final int taskCount = 5;
        TaskInfo lastTask = null;
        CountDownLatch latch = new CountDownLatch(taskCount);
        _testTask._latch = latch;
        _testTask._tasksRan = new ArrayList<>();
        _taskManager.monitorTaskQueue();
        List<Long> taskIds = new ArrayList<>();
        for ( int i=0; i < taskCount; i++ ) {
            Set<Long> prereqs = ( null == lastTask ) ?
                Collections.emptySet() : Collections.singleton(lastTask.getTaskId());
            lastTask = _taskManager.createTask()
                .entityType(TestTask.ENTITY_TYPE)
                .entityId("testPrerequisite")
                .checkpointData(OM.writeValueAsBytes(prereqs))
                .prerequisiteTaskIds(prereqs)
                .build();
            _taskManager.addTask(lastTask);
            taskIds.add(lastTask.getTaskId());
        }
        latch.await();
        _taskManager.stopTaskQueueMonitor(false);
        assertEquals(_testTask._tasksRan, taskIds);
        for ( Long taskId : taskIds ) {
            TaskInfo task = _taskManager.getTask(taskId);
            assertEquals(task.getTaskState(), TaskState.SUCCESS);
        }
    }

    // Run 5 forever tasks + 1 noop <-- prereq task.
    @Test
    public void testAnyPrerequisite() throws Exception {
        final int foreverTaskCount = 5;
        CountDownLatch latch = new CountDownLatch(foreverTaskCount + 2);
        _testTask._latch = latch;
        _testTask._tasksRan = new ArrayList<>();
        _taskManager.monitorTaskQueue();
        List<Long> taskIds = new ArrayList<>();
        try {
            // Start the forever Tasks:
            for ( int i=0; i < foreverTaskCount; i++ ) {
                TaskInfo task = _taskManager.createTask()
                    .entityType(TestTask.ENTITY_TYPE)
                    .entityId("testDelayedForever")
                    .build();
                _taskManager.addTask(task);
                taskIds.add(task.getTaskId());
            }
            // Create the noop task:
            TaskInfo noopTask = _taskManager.createTask()
                .entityType(TestTask.ENTITY_TYPE)
                .entityId("testNoop")
                .build();
            taskIds.add(noopTask.getTaskId());
            // Create the prerequisite task:
            TaskInfo prereqTask = _taskManager.createTask()
                .entityType(TestTask.ENTITY_TYPE)
                .entityId("testPrerequisite")
                // Only the noop task should be terminal:
                .checkpointData(OM.writeValueAsBytes(
                                    Collections.singleton(noopTask.getTaskId())))
                .prerequisiteTaskIds(taskIds)
                .anyPrerequisiteTaskId(true)
                .build();
            taskIds.add(prereqTask.getTaskId());
            _taskManager.addTask(prereqTask);
            _taskManager.addTask(noopTask);
            latch.await();
            _taskManager.stopTaskQueueMonitor(false);
            List<Long> tasksThatShouldHaveRan =
                Arrays.asList(prereqTask.getTaskId(), noopTask.getTaskId());
            assertEquals(
                new HashSet<>(_testTask._tasksRan),
                new HashSet<>(tasksThatShouldHaveRan));
            for ( Long taskId : tasksThatShouldHaveRan ) {
                TaskInfo task = _taskManager.getTask(taskId);
                assertEquals(task.getTaskState(), TaskState.SUCCESS);
            }
        } finally {
            // Cleanup:
            for ( Long taskId : taskIds ) {
                _taskManager.deleteTask(taskId);
            }
        }
    }

    private static long milliTime() {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Test
    public void testDelayed() throws Exception {
        final int delayCount = 5;

        CountDownLatch latch = new CountDownLatch(delayCount);
        _testTask._latch = latch;

        _taskManager.monitorTaskQueue();

        TaskInfo task = _taskManager.createTask()
            .entityType(TestTask.ENTITY_TYPE)
            .entityId("testDelayed")
            .checkpointData((""+delayCount).getBytes())
            .build();

        long t0 = milliTime();
        _taskManager.addTask(task);

        latch.await();
        _taskManager.stopTaskQueueMonitor(false);

        long duration = milliTime() - t0;
        assertTrue("duration = "+duration, duration >= 1000);
        assertEquals(_taskManager.getTask(task.getTaskId()).getTaskState(), TaskState.SUCCESS);
    }

    @Test
    public void testStartRunnableTasks() throws Exception {
        // Add a task when the task queue is NOT monitoring:
        CountDownLatch latch = new CountDownLatch(1);
        _testTask._latch = latch;

        TaskInfo task = _taskManager.createTask()
            .entityType(TestTask.ENTITY_TYPE)
            .entityId("testNoop")
            .build();
        _taskManager.addTask(task);

        // NOW start monitoring:
        _taskManager.monitorTaskQueue();

        latch.await();
        _taskManager.stopTaskQueueMonitor(false);

        assertEquals(_taskManager.getTask(task.getTaskId()).getTaskState(), TaskState.SUCCESS);
    }

    @Test
    public void testFailedMonitor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        _testTask._latch = latch;
        _testTask._failMonitor = true;

        _taskManager.monitorTaskQueue();

        TaskInfo task = _taskManager.createTask()
            .entityType(TestTask.ENTITY_TYPE)
            .entityId("testFailedMonitor")
            .lockIds("_BROKE")
            .build();
        _taskManager.addTask(task);

        latch.await();
        _taskManager.stopTaskQueueMonitor(false);

        // Aborted, so from the outside it appears to be running:
        assertEquals(TaskState.RUNNING, _taskManager.getTask(task.getTaskId()).getTaskState());

        // but the "_BROKE" lock should still be around, preventing other tasks from running
        // until it is cleaned-up. Task that failed the monitor should be ran again.
        latch = new CountDownLatch(2);
        _testTask._latch = latch;
        _testTask._failMonitor = false;

        task = _taskManager.createTask()
            .entityType(TestTask.ENTITY_TYPE)
            .entityId("testNoop")
            .lockIds("_BROKE")
            .build();

        _taskManager.monitorTaskQueue();
        _taskManager.addTask(task);

        // Verify that we are waiting for lock:
      POLL:
        while ( true ) {
            TaskState state =
                _taskManager.getTask(task.getTaskId()).getTaskState();
            switch ( state ) {
            case QUEUED:
            case RUNNING: // in this state when scanning for locks.
                Thread.sleep(100);
                continue;
            case SUCCESS: // monitor may have already been cleaned-up:
            case WAITING_FOR_LOCK:
                break POLL;
            default:
                fail("Unexpected taskState="+state+" for taskId="+task.getTaskId());
            }
        }

        String monitorId = _testTask._monitorIdFailed;
        _testTask._monitorIdFailed = null;
        assertNotNull(monitorId);

        // Cleanup:
        _monitor.reapMonitorId(monitorId);
        // Verify that task is now ran since the lock should have been cleaned up:
        latch.await();
        _taskManager.stopTaskQueueMonitor(false);
    }
}
