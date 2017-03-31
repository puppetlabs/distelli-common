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
import static org.junit.Assert.*;
import com.distelli.utils.Log4JConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestTaskManager {
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

        @Override
        public TaskInfo run(TaskContext ctx) throws Exception {
            try {
                String methodName = ctx.getTaskInfo().getEntityId();
                return (TaskInfo)getClass().getMethod(methodName, TaskContext.class).invoke(this, ctx);
            } finally {
                _latch.countDown();
            }
        }

        public TaskInfo testPrerequisite(TaskContext ctx) throws Exception {
            Set<Long> prerequisisteTaskIds = ctx.getTaskInfo().getPrerequisiteTaskIds();
            Long taskId = ( prerequisisteTaskIds.isEmpty() ) ? null : prerequisisteTaskIds.iterator().next();
            if ( null != taskId ) {
                assertEquals(_taskManager.getTask(taskId)
                             .getTaskState(), TaskState.SUCCESS);
            }
            _tasksRan.add(ctx.getTaskInfo().getTaskId());
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

        public TaskInfo testFailedMonitor(TaskContext ctx) throws Exception {
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
    }

    @Test
    public void testPrerequisites() throws Exception {
        final int taskCount = 5;
        _taskManager.monitorTaskQueue();
        TaskInfo lastTask = null;
        CountDownLatch latch = new CountDownLatch(taskCount);
        _testTask._latch = latch;
        _testTask._tasksRan = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();
        for ( int i=0; i < taskCount; i++ ) {
            lastTask = _taskManager.createTask()
                .entityType(TestTask.ENTITY_TYPE)
                .entityId("testPrerequisite")
                .prerequisiteTaskIds(null == lastTask ? null : lastTask.getTaskId())
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

    private static long milliTime() {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Test
    public void testDelayed() throws Exception {
        final int delayCount = 5;
        _taskManager.monitorTaskQueue();

        CountDownLatch latch = new CountDownLatch(delayCount);
        _testTask._latch = latch;

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
            .entityId("testPrerequisite")
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
        _taskManager.monitorTaskQueue();

        CountDownLatch latch = new CountDownLatch(1);
        _testTask._latch = latch;

        TaskInfo task = _taskManager.createTask()
            .entityType(TestTask.ENTITY_TYPE)
            .entityId("testFailedMonitor")
            .lockIds("_BROKE")
            .build();
        _taskManager.addTask(task);

        latch.await();
        _taskManager.stopTaskQueueMonitor(false);

        // Should still report success:
        assertEquals(_taskManager.getTask(task.getTaskId()).getTaskState(), TaskState.SUCCESS);

        // but the "_BROKE" lock should still be around, preventing other tasks from running
        // until it is cleaned-up:
        latch = new CountDownLatch(1);
        _testTask._latch = latch;

        task = _taskManager.createTask()
            .entityType(TestTask.ENTITY_TYPE)
            .entityId("testPrerequisite")
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
