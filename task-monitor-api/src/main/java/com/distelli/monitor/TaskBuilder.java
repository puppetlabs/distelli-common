package com.distelli.monitor;

import java.util.Collection;

/**
 * Used to create a new TaskInfo with updated fields.
 *
 * @see TaskInfo
 */
public interface TaskBuilder {
    /**
     * @param entityType the entity type to use for this task. There
     *     must be an entry in the DI frameworks Map&lt;String, TaskFunction&gt;
     *     where the key of this map is the entity type. If no such
     *     key exists, then the task will purpetually be attempt to be
     *     ran until such a key exists.
     *
     * @return this for method chaining.
     */
    public TaskBuilder entityType(String entityType);
    /**
     * @param entityId the entity id to use for this task.
     *
     * @return this for method chaining.
     */
    public TaskBuilder entityId(String entityId);
    /**
     * @param checkpointData the checkpoint data to use for this task.
     *
     * @return this for method chaining.
     */
    public TaskBuilder checkpointData(byte[] checkpointData);
    /**
     * @param lockIds the lock identifiers to obtain before this task runs (again).
     *
     * @return this for method chaining.
     */
    public TaskBuilder lockIds(Collection<String> lockIds);
    /**
     * @param lockIds the lock identifiers to obtain before this task runs (again).
     *
     * @return this for method chaining.
     */
    public TaskBuilder lockIds(String... lockIds);
    /**
     * @param prerequisiteTaskIds the prerequisite tasks that must be in a
     *        terminal state before this task runs (again).
     *
     * @return this for method chaining.
     */
    public TaskBuilder prerequisiteTaskIds(Long... prerequisiteTaskIds);
    /**
     * @param prerequisiteTaskIds the prerequisite tasks that must be in a
     *        terminal state before this task runs (again).
     *
     * @return this for method chaining.
     */
    public TaskBuilder prerequisiteTaskIds(Collection<Long> prerequisiteTaskIds);
    /**
     * @param millisecondsRemaining the number of milliseconds which must
     *        elapse for this task runs (again).
     *
     * @return this for method chaining.
     */
    public TaskBuilder millisecondsRemaining(long millisecondsRemaining);
    /**
     * @return a new TaskInfo with the fields updated.
     */
    public TaskInfo build();
}
