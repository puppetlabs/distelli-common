package com.distelli.monitor;

import com.distelli.crypto.impl.CryptoModule;
import com.distelli.monitor.impl.MonitorTaskModule;
import com.distelli.monitor.impl.SequenceImpl;
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

public class TestTaskManager {
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

        @Override
        public TaskInfo run(TaskContext ctx) throws Exception {
            String methodName = ctx.getTaskInfo().getEntityId();
            return (TaskInfo)getClass().getMethod(methodName, TaskContext.class).invoke(this, ctx);
        }

        public TaskInfo testPrerequisite(TaskContext ctx) throws Exception {
            try {
                Set<Long> prerequisisteTaskIds = ctx.getTaskInfo().getPrerequisiteTaskIds();
                Long taskId = ( prerequisisteTaskIds.isEmpty() ) ? null : prerequisisteTaskIds.iterator().next();
                if ( null != taskId ) {
                    assertEquals(_taskManager.getTask(taskId)
                                 .getTaskState(), TaskState.SUCCESS);
                }
                _tasksRan.add(ctx.getTaskInfo().getTaskId());
                return null;
            } finally {
                _latch.countDown();
            }
        }
    }

    @Inject
    private TaskManager _taskManager;
    @Inject
    private TestTask _testTask;
    @Inject
    private ScheduledExecutorService _executor;

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
        _executor.shutdown();
        _executor.awaitTermination(60, TimeUnit.SECONDS);
        assertEquals(_testTask._tasksRan, taskIds);
        for ( Long taskId : taskIds ) {
            TaskInfo task = _taskManager.getTask(taskId);
            assertEquals(task.getTaskState(), TaskState.SUCCESS);
        }
    }
}
