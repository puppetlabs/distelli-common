package com.distelli.monitor;

/**
 * The parameter to your TaskFunction which offers ways to introspect
 * the task being ran.
 */
public interface TaskContext {
    /**
     * @return the TaskInfo associated with this TaskContext.
     */
    public TaskInfo getTaskInfo();
    /**
     * @return the MonitorInfo associated with this TaskContext.
     */
    public MonitorInfo getMonitorInfo();
    /**
     * NOTE: If this is non-null then the task is being ran regardless
     * of locks / prerequisites / timeouts.
     *
     * @return the update data associated with this task.
     */
    public byte[] getUpdateData();
    /**
     * @param checkpointData which should be saved for this TaskContext.
     *
     * Note that your TaskFunction can also return a new TaskInfo
     * object with updated checkpointData which will be saved.
     */
    public void commitCheckpointData(byte[] checkpointData);
}
