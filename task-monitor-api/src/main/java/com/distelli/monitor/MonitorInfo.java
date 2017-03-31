package com.distelli.monitor;

public interface MonitorInfo {
    /**
     * @return the unique identifier of the monitor which is an 11 character
     *         compact UUID.
     */
    public String getMonitorId();
    /**
     * @return true if this heartbeat has either been marked as failure or
     *         has not been able to perform a heartbeat in 50 seconds.
     */
    public boolean hasFailedHeartbeat();
    /**
     * Current implementation obtains this from ManagementFactory.getRuntimeMXBean().getName().
     *
     * @return the name of the node that is performing the heartbeat.
     */
    public String getNodeName();
    /**
     * Current implementation obtains this from the injected ProductVersion.
     *
     * @return the product version that is performing the heartbeat.
     */
    public String getVersion();
    /**
     * The heartbeat is incremented every 10 seconds.
     *
     * @return the heartbeat count.
     */
    public long getHeartbeat();
    /**
     * Force the monitor to be marked as heartbeat failed. This method
     * should only be used as a last resort since it effects all tasks
     * running with this monitor.
     */
    public void forceHeartbeatFailure();
}
