package com.distelli.monitor;

import com.distelli.persistence.PageIterator;
import java.util.List;

public interface TaskManager {
    public List<? extends TaskInfo> getTasksByEntityType(String entityType, PageIterator iter);

    public TaskInfo getTask(Long taskId);

    // Creates a taskBuilder with a taskId assigned when .build() is called:
    public TaskBuilder createTask();

    public void addTask(TaskInfo taskInfo);

    // Marks a task as "to be canceled":
    public void cancelTask(String canceledBy, long taskId);

    // Turn on task queue monitoring:
    public void monitorTaskQueue();

    // Turn off task queue monitoring and wait for any pending tasks
    // to complete:
    public void stopTaskQueueMonitor(boolean mayInterruptIfRunning);
}
