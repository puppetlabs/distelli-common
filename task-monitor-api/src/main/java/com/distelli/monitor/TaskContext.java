package com.distelli.monitor;

public interface TaskContext {
    public TaskInfo getTaskInfo();
    public void commitState(byte[] newState);
}
