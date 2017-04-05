package com.distelli.monitor;

/**
 * Usage:
 *
 * <pre>
 *   {@literal @}Inject private Monitor _monitor;
 *
 *       _monitor.monitor((monitorInfo) -&gt; {
 *           // This function is passed a MonitorInfo object:
 *           doSomethingWith(monitorInfo.getMonitorId());
 *       });
 * </pre>
 *
 * When a task is monitored, the thread will be interrupted if the
 * heartbeat for that monitor fails. Heartbeats are saved to
 * the database every 10 seconds for a monitor.
 *
 * All monitor participants will look for monitors that have
 * not performed a heartbeat for 60 seconds, and in that scenario
 * a monitor reaping task is added to the TaskManager.
 *
 * The monitor reaping task will scan for tasks and locks that
 * were using that monitor id, and they will be forcefully
 * released.
 *
 * This mechanism is akin to the SQS message visibility timeout:
 *
 *  http://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ChangeMessageVisibility.html
 *
 * ...but the implementation handles performing heartbeats
 * on your behalf.
 */
public interface Monitor {
    /**
     * Obtain the current active monitor, and run the task with that
     * active monitor. This method returns when the task function is
     * done.
     *
     * @param task - The function to run with the current active monitor.
     *
     * @throws IllegalStateException if shutdownMonitor() was called.
     */
    public void monitor(Monitored task) throws IllegalStateException;

    /**
     * Obtain information about a particular monitor id.
     *
     * @param monitorId - The monitor id to obtain information about.
     *
     * @return null if no monitor with that id exists, otherwise return
     *     information about this monitor id.
     */
    public MonitorInfo getMonitorInfo(String monitorId);

    /**
     * Stops all threads being monitored, causes monitor() to throw
     * IllegalStateException. This method will block until all running
     * tasks are stopped.
     *
     * @param mayInterruptIfRunning determines if `Thread.interrupt()`
     *     is called on Monitored tasks that are in progress.
     */
    public void shutdownMonitor(boolean mayInterruptIfRunning);

    /**
     * Tests if the monitorInfo is still considered to be the active
     * monitor.
     *
     * @param monitorInfo is the monitor information to check if it is
     *        active.
     *
     * @return true if this monitor is still considered to be the
     *         active monitor.
     */
    public boolean isActiveMonitor(MonitorInfo monitorInfo);
}
