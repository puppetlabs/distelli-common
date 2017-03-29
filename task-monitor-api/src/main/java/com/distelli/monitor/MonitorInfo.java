package com.distelli.monitor;

public interface MonitorInfo {
    // The current monitor id used by this thread, or null if this has expired:
    public String getMonitorId(); // Compact UUID.
    // True if the heartbeat for this monitor has failed (and thus the
    // thread is being interrupted).
    public boolean hasFailedHeartbeat();
    public String getNodeName(); // ManagementFactory.getRuntimeMXBean().getName()
    public String getVersion();  // product version
    public long getHeartbeat();  // incremented on regular intervals.
    public void forceHeartbeatFailure();
}
