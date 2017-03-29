package com.distelli.monitor;

import java.util.Set;

public interface TaskInfo {
    // PK:
    public Long getTaskId();
    // AK:
    public String getEntityType();
    public String getEntityId();

    // The state of this task:
    public TaskState getTaskState();

    // Locks that need to be acquired before running this task:
    public Set<String> getLockIds();

    // Tasks that need to be ran before running this task:
    public Set<Long> getPrerequisiteTaskIds();

    // The monitor id this task is running in if the task is currently running:
    public String getMonitorId();

    // Checkpoint information stored for this task:
    public byte[] getCheckpointData();

    // Exception.getMessage():
    public String getErrorMessage();

    // Exception.printStackTrace():
    public String getErrorStackTrace();

    // When the first instance of this task began:
    public Long getStartTime();

    // When the final instance of this task finished:
    public Long getEndTime();

    // How many times this task has ran:
    public Long getRunCount();

    // How many milliseconds need to elapse before running this task again:
    public Long getMillisecondsRemaining();

    public TaskBuilder toBuilder();

    // Task may have still ran to completion:
    public String getCanceledBy();
}
