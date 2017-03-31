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
     * @param checkpointData which should be saved for this TaskContext.
     *
     * Note that your TaskFunction can also return a new TaskInfo
     * object with updated checkpointData which will be saved.
     */
    public void commitCheckpointData(byte[] checkpointData);
}
