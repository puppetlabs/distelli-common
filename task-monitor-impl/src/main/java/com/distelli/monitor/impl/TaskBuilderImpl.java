package com.distelli.monitor.impl;

import java.util.Collection;
import java.util.Arrays;
import com.distelli.monitor.TaskBuilder;
import com.distelli.monitor.TaskInfo;
import java.util.LinkedHashSet;

public class TaskBuilderImpl implements TaskBuilder {
    private Task proto;

    public TaskBuilderImpl() {
        this(new Task());
    }

    public TaskBuilderImpl(Task task) {
        this.proto = task;
    }

    @Override
    public TaskBuilder entityType(String entityType) {
        if ( null == entityType || entityType.length() >= 256 ) {
            throw new IllegalArgumentException("entityType="+entityType+" is not valid, must be non-null length < 256");
        }
        proto.entityType = entityType;
        return this;
    }

    @Override
    public TaskBuilder entityId(String entityId) {
        if ( null == entityId || entityId.length() >= 256 ) {
            throw new IllegalArgumentException("entityId="+entityId+" is not valid, must be non-null length < 256");
        }
        proto.entityId = entityId;
        return this;
    }

    @Override
    public TaskBuilder checkpointData(byte[] checkpointData) {
        if ( null != checkpointData ) {
            checkpointData = Arrays.copyOf(checkpointData, checkpointData.length);
        }
        proto.checkpointData = checkpointData;
        return this;
    }

    @Override
    public TaskBuilder lockIds(Collection<String> lockIds) {
        proto.lockIds = new LinkedHashSet<String>(lockIds);
        return this;
    }

    @Override
    public TaskBuilder lockIds(String... lockIds) {
        return lockIds(Arrays.asList(lockIds));
    }

    @Override
    public TaskBuilder prerequisiteTaskIds(Long... prerequisiteTaskIds) {
        return prerequisiteTaskIds(Arrays.asList(prerequisiteTaskIds));
    }

    @Override
    public TaskBuilder prerequisiteTaskIds(Collection<Long> prerequisiteTaskIds) {
        proto.prerequisiteTaskIds = new LinkedHashSet<Long>(prerequisiteTaskIds);
        return this;
    }

    @Override
    public TaskBuilder millisecondsRemaining(long millisecondsRemaining) {
        proto.millisecondsRemaining = millisecondsRemaining;
        return this;
    }

    @Override
    public TaskInfo build() {
        TaskInfo result = proto;
        proto = new Task(proto);
        return result;
    }
}
