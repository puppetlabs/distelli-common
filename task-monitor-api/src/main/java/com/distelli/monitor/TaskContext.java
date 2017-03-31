package com.distelli.monitor;

public interface TaskContext {
    public TaskInfo getTaskInfo();
    public MonitorInfo getMonitorInfo();
    public void commitState(byte[] newState);
}
