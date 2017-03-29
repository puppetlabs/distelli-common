package com.distelli.monitor.impl;

import com.distelli.monitor.TaskBuilder;
import com.distelli.monitor.TaskContext;
import com.distelli.monitor.TaskFunction;
import com.distelli.monitor.TaskInfo;
import javax.inject.Inject;

public class ReapMonitorTask implements TaskFunction {
    public static String ENTITY_TYPE = "gc:monitor";

    @Inject
    private TaskManagerImpl _taskManager;

    @Inject
    protected ReapMonitorTask() {}

    @Override
    public TaskInfo run(TaskContext ctx) {
        String monitorId = ctx.getTaskInfo().getEntityId();
        _taskManager.releaseLocksForMonitorId(monitorId);
        return null;
    }

    public TaskInfo build(TaskBuilder builder, String monitorId) {
        return builder.entityType(ENTITY_TYPE)
            .entityId(monitorId)
            .build();
    }
}
