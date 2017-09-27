package com.distelli.monitor.impl;

import com.distelli.monitor.TaskBuilder;
import com.distelli.monitor.TaskContext;
import com.distelli.monitor.TaskFunction;
import com.distelli.monitor.TaskInfo;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReapMonitorTask implements TaskFunction {
    private static final Logger LOG = LoggerFactory.getLogger(ReapMonitorTask.class);
    public static String ENTITY_TYPE = "gc:monitor";

    @Inject
    private TaskManagerImpl _taskManager;

    @Inject
    protected ReapMonitorTask() {}

    @Override
    public TaskInfo run(TaskContext ctx) {
        String monitorId = ctx.getTaskInfo().getEntityId();
        try {
            _taskManager.releaseLocksForMonitorId(monitorId);
        } catch ( Throwable ex ) {
            LOG.error("ReapMonitorTaskFailed: "+ex.getCause(), ex);
            // Be sure to retry this in another minute:
            return ctx.getTaskInfo().toBuilder()
                .millisecondsRemaining(60000L)
                .build();
        }
        return null;
    }

    public TaskInfo build(TaskBuilder builder, String monitorId) {
        return builder.entityType(ENTITY_TYPE)
            .entityId(monitorId)
            .build();
    }
}
