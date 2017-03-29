package com.distelli.monitor;

// A task function is ran when the following conditions are met:
//  - The lock ids have been acquired.
//  - The prerequisites have ran.
//  - the interval milliseconds have passed.
// Any task can return a TaskInfo object that updates these fields:
//  - A different set of lock ids to acquire.
//  - A different set of prerequisites to run before this task.
//  - An interval in which this task should be ran again.
// ...or an exception is throw which fails the task.
// ...or return null to indicate task is complete.

@FunctionalInterface
public interface TaskFunction {
    public TaskInfo run(TaskContext ctx) throws Exception;
}
