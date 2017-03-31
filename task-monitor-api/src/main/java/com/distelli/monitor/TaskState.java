package com.distelli.monitor;

/**
 * Indicates what state a task is in.
 */
public enum TaskState {
    /**
     * All tasks are initially in the QUEUED state. Tasks will also enter
     * this state if something that this task is waiting should no longer
     * be waited for.
     */
    QUEUED(false),
    /**
     * Tasks enter this state when checking if the task meets the interval,
     * prerequisite, and lock requirements. It continues to be in this state
     * when the task is executed.
     */
    RUNNING(false),
    /**
     * Indicates that the task is waiting for an interval to elapse before
     * it is eligible for execution.
     */
    WAITING_FOR_INTERVAL(false),
    /**
     * Indicates that the task is waiting for a prerequisite task to enter
     * a terminal state before it is eligible for execution. Note that a
     * task could be forever in this state if there is a dependency cycle.
     */
    WAITING_FOR_PREREQUISITE(false),
    /**
     * Indicates that the task is waiting for a lock to be released before
     * it is eligible for execution. Tasks with lower task ids are given
     * priority for obtaining locks, so a task shouldn't be in this state
     * forever. Locks are also acquired in sorted order to avoid deadlock.
     */
    WAITING_FOR_LOCK(false),
    /**
     * Indicates the task threw an Exception.
     */
    FAILED(true),
    /**
     * Indicates the task completed successfully.
     */
    SUCCESS(true),
    /**
     * Indicates the task was canceled. Note that the call to
     * `TaskManager.cancelTask()` does not immediately set this field, but
     * rather the task has to run once in order to see the canceledBy field
     * is set.
     */
    CANCELED(true);

    private boolean terminal;
    private TaskState(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * @return true if this TaskState is a terminal state. Currently this
     *     is the FAILED, SUCCESS, and CANCELED states.
     */
    public boolean isTerminal() {
        return terminal;
    }
}
