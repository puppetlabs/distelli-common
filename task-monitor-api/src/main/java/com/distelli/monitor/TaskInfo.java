package com.distelli.monitor;

import java.util.Set;

/**
 * Obtain information about a task.
 */
public interface TaskInfo {
    /**
     * @return the globally unique task identifier that acts as the
     *     primary key in the monitor-tasks table.
     */
    public Long getTaskId();
    /**
     * The entityType and entityId act as an alternative key into the
     * monitor-tasks table. The entity type must coorespond with a
     * key in the DI frameworks Map&lt;String, TaskFunction&gt;. Otherwise
     * the task will never run successfully (but will be perpetually
     * retried).
     *
     * @return the entity type of the task used to identify the
     *     TaskFunction which should be used to run the task.
     */
    public String getEntityType();
    /**
     * The entityType and entityId act as an alternative key into the
     * monitor-tasks table.
     *
     * @return the entity id of the task.
     */
    public String getEntityId();
    /**
     * @return the state of this task.
     *
     * @see TaskState
     */
    public TaskState getTaskState();

    /**
     * @return a set of strings that act as lock identifiers. The task
     *     manager ensures that only one task will run at a time for a
     *     given lock id.
     */
    public Set<String> getLockIds();

    /**
     * @return a set of prerequisite task identifiers that must be in a
     *     terminal state before this task is ran.
     */
    public Set<Long> getPrerequisiteTaskIds();

    /**
     * @return the monitor id locked to this task, or null if this task
     *     is in a terminal state. Note that a few "special" monitor ids
     *     exist to indicate that a task is ready to be ran or is
     *     waiting on a lock.
     */
    public String getMonitorId();

    /**
     * @return the checkpoint data associated with this task.
     */
    public byte[] getCheckpointData();

    /**
     * @return Exception.getMessage() if the task threw an exception.
     */
    public String getErrorMessage();

    /**
     * @return Exception.printStackTrace(StringWriter) if the task
     *     threw an exception.
     */
    public String getErrorStackTrace();

    /**
     * @return a compact UUID that matches a log entry used to diagnose
     *     failures if the task threw an exception.
     */
    public String getErrorId();

    /**
     * @return epoch milliseconds of when the last time the task ran.
     */
    public Long getStartTime();

    /**
     * @return epoch milliseconds of when the last time the task
     *     terminated. Note that this may be earlier than the start
     *     time if the task is currently running.
     */
    public Long getEndTime();

    /**
     * @return the number of times the task was "locked" by a
     *     TaskManager. Note that this count is incremented even
     *     when a tasks prerequisites are found to not be
     *     satisified or a lock is failed to be acquired. This is
     *     purely for informational purposes.
     */
    public Long getRunCount();

    /**
     * @return the number of milliseconds which must be observed to
     *     to be elapsed before this task will be attempted to run.
     *     Note that the actual elapsed time may be much more if a
     *     monitor heartbeat fails, a prerequisite is not satisified,
     *     or a lock fails to be acquired.
     */
    public Long getMillisecondsRemaining();

    /**
     * @return a TaskBuilder initialized to this TaskInfo's
     *     state.
     */
    public TaskBuilder toBuilder();

    /**
     * @return the string passed to `TaskManager.cancelTask()`.
     *
     * @see TaskManager
     */
    public String getCanceledBy();
}
