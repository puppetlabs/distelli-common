package com.distelli.monitor;

/**
 * Implement this interface and configure the DI system's
 * Map&lt;String, TaskFunction&gt; so that the entityType of the
 * TaskInfo dispatches to your TaskFunction implementation.
 *
 * The task function will NOT be ran until ALL the following
 * conditions are met:
 *
 *     - All lockIds have been acquired for this TaskInfo.
 *     - All prerequisiteTaskIds have entered a terminal state.
 *     - The millisecondsRemaining drops to zero.
 *
 * @see TaskInfo
 */
@FunctionalInterface
public interface TaskFunction {
    /**
     * @param ctx is the task context for the current task being ran.
     *
     * @return null to indicate that the task completed successfully,
     *     otherwise return a new TaskInfo with any of these changes:
     *
     *     - A different set of lock ids to acquire the next
     *       time this task is ran.
     *     - A different set of prerequisite task ids that need to
     *       be in a terminal state before this task is ran.
     *     - A time interval that needs to elapse before this task
     *       is ran again.
     *
     * @throws Exception to indicate that the task failed. The following
     *     TaskInfo fields will be updated with information about the
     *     Exception:
     *
     *     - errorMessage: Exception.getMessage()
     *     - errorId: Compact UUID of the error for tracking
     *     - errorStackTrace: Exception.printStackTrace(PrintWriter
     */
    public TaskInfo run(TaskContext ctx) throws Exception;
}
