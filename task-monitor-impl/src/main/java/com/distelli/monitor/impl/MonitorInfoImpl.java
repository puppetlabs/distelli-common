package com.distelli.monitor.impl;

import com.distelli.monitor.MonitorInfo;
import java.util.Set;
import java.util.HashSet;
import com.distelli.utils.CompactUUID;
import java.util.Collections;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

public class MonitorInfoImpl implements MonitorInfo {
    // Persisted:
    private String monitorId;
    private String nodeName;
    private String version;
    private long heartbeat;

    // Transient:
    private boolean hasFailedHeartbeat;
    private Set<Thread> runningThreads = Collections.synchronizedSet(new HashSet<Thread>());
    private long lastHeartbeatMillis;
    private long maxTimeBetweenHeartbeat;

    public MonitorInfoImpl(String version, long maxTimeBetweenHeartbeat) {
        this.monitorId = CompactUUID.randomUUID().toString();
        this.hasFailedHeartbeat = false;
        this.nodeName = ManagementFactory.getRuntimeMXBean().getName();
        this.version = version;
        this.heartbeat = 1;
        this.lastHeartbeatMillis = milliTime();
    }

    public String getMonitorId() {
        return monitorId;
    }

    public synchronized boolean hasFailedHeartbeat() {
        if ( milliTime() - lastHeartbeatMillis > maxTimeBetweenHeartbeat ) {
            hasFailedHeartbeat = true;
        }
        return hasFailedHeartbeat;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getVersion() {
        return version;
    }

    public synchronized long getHeartbeat() {
        return heartbeat;
    }

    public synchronized void forceHeartbeatFailure() {
        hasFailedHeartbeat = true;
    }

    public Set<Thread> getRunningThreads() {
        return runningThreads;
    }

    public synchronized boolean heartbeatWasPerformed() {
        lastHeartbeatMillis = milliTime();
        heartbeat++;
        return hasFailedHeartbeat;
    }

    public synchronized long getLastHeartbeatMillis() {
        return lastHeartbeatMillis;
    }

    private static long milliTime() {
        return TimeUnit.NANOSECONDS.convert(System.nanoTime(), TimeUnit.MILLISECONDS);
    }
}
