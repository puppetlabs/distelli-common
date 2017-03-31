package com.distelli.monitor.impl;

import com.distelli.monitor.MonitorInfo;
import java.util.Map;
import java.util.HashMap;
import com.distelli.utils.CompactUUID;
import java.util.Collections;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorInfoImpl implements MonitorInfo {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorInfoImpl.class);
    // Persisted:
    private String monitorId;
    private String nodeName;
    private String version;
    private long heartbeat;

    // Transient:
    private boolean hasFailedHeartbeat;
    private Map<Thread, AtomicInteger> runningThreads = new HashMap<>();
    private long lastHeartbeatMillis;
    private long maxTimeBetweenHeartbeat;

    public MonitorInfoImpl() {}
    public MonitorInfoImpl(String version, long maxTimeBetweenHeartbeat) {
        this.monitorId = CompactUUID.randomUUID().toString();
        this.hasFailedHeartbeat = false;
        this.nodeName = ManagementFactory.getRuntimeMXBean().getName();
        this.version = version;
        this.heartbeat = 1;
        this.lastHeartbeatMillis = milliTime();
        this.maxTimeBetweenHeartbeat = maxTimeBetweenHeartbeat;
    }

    @Override
    public String getMonitorId() {
        return monitorId;
    }

    @Override
    public synchronized boolean hasFailedHeartbeat() {
        if ( ! hasFailedHeartbeat && milliTime() - lastHeartbeatMillis > maxTimeBetweenHeartbeat ) {
            LOG.error("Forcing heartbeat failure due to max time between heartbeat");
            forceHeartbeatFailure();
        }
        return hasFailedHeartbeat;
    }

    @Override
    public String getNodeName() {
        return nodeName;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public synchronized long getHeartbeat() {
        return heartbeat;
    }

    @Override
    public synchronized void forceHeartbeatFailure() {
        hasFailedHeartbeat = true;
    }

    public synchronized boolean isRunningInMonitoredThread() {
        AtomicInteger count = runningThreads.get(Thread.currentThread());
        return null != count && count.get() > 0;
    }

    public synchronized void captureRunningThread() {
        Thread thread = Thread.currentThread();
        AtomicInteger count = runningThreads.get(thread);
        if ( null == count ) {
            count = new AtomicInteger(0);
            runningThreads.put(thread, count);
        }
        count.incrementAndGet();
    }

    // Returns true if the count for this thread drops to zero:
    public synchronized boolean releaseRunningThread() {
        Thread thread = Thread.currentThread();
        AtomicInteger count = runningThreads.get(thread);
        if ( null == count || 0 == count.decrementAndGet() ) {
            runningThreads.remove(thread);
            if ( runningThreads.isEmpty() ) notifyAll();
            return true;
        }
        return false;
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
        return TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    public synchronized String dumpThreads() {
        StringBuilder sb = new StringBuilder();
        for ( Thread thread : runningThreads.keySet() ) {
            sb.append(thread.getName()).append(":\n");
            for ( StackTraceElement ste : thread.getStackTrace() ) {
                sb.append("\t").append(ste.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    // Returns true if all threads got shutdown in the millisWaitMax time.
    public synchronized boolean interruptAndWaitForRunningThreads(long millisWaitMax) {
        Thread cur = Thread.currentThread();
        for ( Thread thread : runningThreads.keySet() ) {
            if ( cur == thread ) {
                throw new IllegalStateException("interruptAndWaitForRunningThreads() called within a monitor()");
            }
            thread.interrupt();
        }
        long start = milliTime();
        while ( ! runningThreads.isEmpty() ) {
            long now = milliTime();
            long max = millisWaitMax - (now - start);
            if ( max <= 0 ) return false;
            try {
                wait(max);
            } catch ( InterruptedException ex ) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "MonitorInfo{"
            +"monitorId="+monitorId
            +",nodeName="+nodeName
            +",heartbeat="+heartbeat
            +",hasFailedHeartbeat="+hasFailedHeartbeat
            +",lastHeartbeatMillis="+lastHeartbeatMillis
            +",maxTimeBetweenHeartbeat="+maxTimeBetweenHeartbeat
            +",runningThreads="+runningThreads
            +"}";
    }
}
