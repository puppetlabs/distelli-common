package com.distelli.monitor;

import com.distelli.persistence.PageIterator;
import java.util.List;

/**
 * The primary API into scheduling new tasks and obtaining information about tasks.
 *
 * Note that you MUST call `monitorTaskQueue()` if you want the current JVM to be
 * used for task execution.
 */
public interface TaskManager {
    /**
     * @param entityType of tasks to query for.
     *
     * @param iter used for iterating over all tasks.
     *
     * @return the list of tasks.
     */
    public List<? extends TaskInfo> getTasksByEntityType(String entityType, PageIterator iter);

    /**
     * @param taskId of the task to obtain.
     *
     * @return the cooresponding task or null if the task was not found.
     */
    public TaskInfo getTask(Long taskId);

    /**
     * @return a new TaskBuilder which should be used in conjunction with
     *     `TaskManager.addTask()`.
     */
    public TaskBuilder createTask();

    /**
     * Add a new task to dispatch. Note that the TaskInfo MUST be created
     * from a TaskBuilder returned by `TaskManager.createTask()`.
     *
     * The task MUST have these fields set on it:
     *
     *     - entityType which is used to identify the TaskFunction to run
     *       for this task. Specifically it is the key of the
     *       Map&lt;String, TaskFunction&gt; injected by the DI framework. Note
     *       that an entry in this map must exist even when the task
     *       queue is not actively monitored.
     *
     *     - entityId which must be non-null.
     *
     *     - taskId which will automatically be set by the TaskBuilder
     *       returned from `TaskManager.createTask()`.
     *
     * @param taskInfo contains the information of the task to be added.
     */
    public void addTask(TaskInfo taskInfo);

    /**
     * Indicate that a task is desired to be canceled. Cancelation does not
     * currently interrupt a running task. This API call is ignored if the
     * task is in a terminal state or if the task does not exist.
     *
     * @param canceledBy a string used to record who or why a task was
     *     canceled. The `TaskInfo.getCanceledBy()` method will return
     *     this string.
     *
     * @param taskId is the identifier of the task to cancel.
     */
    public void cancelTask(String canceledBy, long taskId);

    /**
     * Start monitoring for tasks in the current JVM. If we are already
     * monitoring for tasks in this JVM, then nothing happens.
     */
    public void monitorTaskQueue();

    /**
     * Stop monitoring for tasks in the current JVM. If the JVM is not
     * currently monitoring for tasks, nothing happens. This method
     * will block until all running tasks are stopped.
     *
     * @param mayInterruptIfRunning is passed into `Future.cancel(boolean)`:
     *     Set this to true if the thread executing this task should be
     *     interrupted; otherwise, in-progress tasks are allowed to complete.
     */
    public void stopTaskQueueMonitor(boolean mayInterruptIfRunning);
}
