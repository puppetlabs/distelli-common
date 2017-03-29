package com.distelli.monitor.impl;

public class Lock {
    public Lock() {}
    public Lock(String lockId, String taskId) {
        this.lockId = lockId;
        this.taskId = taskId;
    }
    // The string lock id:
    public String lockId;
    // The id of the task that is queued:
    public String taskId;

    //////////////////////////////////////////////////////////////////////
    // These fields only apply where taskId=TASK_ID_NONE:
    public Long runningTaskId;
    public String monitorId;
    // The number of tasks which have been queued for this lock. Used for
    // determining if tasks have been queued since the last iteration:
    public Long tasksQueued;
}
