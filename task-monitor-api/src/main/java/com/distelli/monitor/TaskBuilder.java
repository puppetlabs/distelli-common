package com.distelli.monitor;

import java.util.Collection;

public interface TaskBuilder {
    public TaskBuilder entityType(String entityType);
    public TaskBuilder entityId(String entityId);
    public TaskBuilder checkpointData(byte[] checkpointData);
    public TaskBuilder lockIds(Collection<String> lockIds);
    public TaskBuilder lockIds(String... lockIds);
    public TaskBuilder prerequisiteTaskIds(Long... prerequisiteTaskIds);
    public TaskBuilder prerequisiteTaskIds(Collection<Long> prerequisiteTaskIds);
    public TaskBuilder millisecondsRemaining(long millisecondsRemaining);
    public TaskInfo build();
}
