package com.distelli.monitor.impl;

import com.distelli.monitor.Monitored;
import com.distelli.monitor.Monitor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.distelli.monitor.TaskManager;
import com.distelli.monitor.ProductVersion;
import com.distelli.persistence.TableDescription;
import com.distelli.persistence.Index;
import com.distelli.persistence.AttrType;
import com.distelli.persistence.PageIterator;
import com.distelli.persistence.IndexDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import com.distelli.jackson.transform.TransformModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;
import javax.persistence.RollbackException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.NoSuchElementException;

public class MonitorImpl implements Monitor {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorImpl.class);
    private static final int HEARTBEAT_INTERVAL_MS = 10000;
    private static final int REAP_INTERVALS = 6;

    @Inject
    private ScheduledExecutorService _executor;
    @Inject
    private TaskManager _taskManager;
    @Inject
    private ProductVersion _productVersion;
    @Inject
    private ReapMonitorTask _reapMonitorTask;

    private Index<MonitorInfoImpl> _monitors;
    private final ObjectMapper _om = new ObjectMapper();
    private Map<String, Long> _heartbeats = new HashMap<>();
    private MonitorInfoImpl _activeMonitorInfo;
    private ScheduledFuture<?> _reaper;
    private ScheduledFuture<?> _heartbeat;
    private Set<MonitorInfoImpl> _monitorsToShutdown = new HashSet<>();

    public static TableDescription getTableDescription() {
        return TableDescription.builder()
            .tableName("monitors")
            .index((idx) -> idx
                   .hashKey("id", AttrType.STR)
                   .build())
            .build();
    }

    private TransformModule createTransforms(TransformModule module) {
        module.createTransform(MonitorInfoImpl.class)
            .put("id", String.class, "monitorId")
            .put("nam", String.class, "nodeName")
            .put("ver", String.class, "version")
            .put("hb", Long.class, "heartbeat");
        return module;
    }

    @Inject
    protected MonitorImpl() {}
    @Inject
    protected void init(Index.Factory indexFactory) {
        _om.registerModule(createTransforms(new TransformModule()));

        _monitors = indexFactory.create(MonitorInfoImpl.class)
            .withNoEncrypt("hb")
            .withTableDescription(getTableDescription())
            .withConvertValue(_om::convertValue)
            .build();

        long reapInterval = HEARTBEAT_INTERVAL_MS * REAP_INTERVALS;
        _reaper = _executor.scheduleAtFixedRate(
            this::reaper,
            ThreadLocalRandom.current().nextLong(reapInterval),
            reapInterval,
            TimeUnit.MILLISECONDS);
        _heartbeat = _executor.scheduleAtFixedRate(
            this::heartbeat,
            ThreadLocalRandom.current().nextLong(HEARTBEAT_INTERVAL_MS),
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        shutdown();
                    } catch ( Throwable ex ) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            });
    }

    @Override
    public void monitor(Monitored task) {
        MonitorInfoImpl oldMonitor = null;
        MonitorInfoImpl newMonitor;
        synchronized ( this ) {
            if ( null == _reaper ) {
                throw new ShuttingDownException();
            }
            if ( null == _activeMonitorInfo || _activeMonitorInfo.hasFailedHeartbeat() ) {
                oldMonitor = _activeMonitorInfo;
                newMonitor = new MonitorInfoImpl(
                    _productVersion.toString(),
                    HEARTBEAT_INTERVAL_MS * REAP_INTERVALS);
                _monitors.putItem(newMonitor);
                _monitorsToShutdown.add(newMonitor);
                _activeMonitorInfo = newMonitor;
            } else {
                newMonitor = _activeMonitorInfo;
            }
        }
        if ( null != oldMonitor ) shutdown(oldMonitor);
        try {
            newMonitor.getRunningThreads().add(Thread.currentThread());
            task.run(newMonitor);
        } finally {
            newMonitor.getRunningThreads().remove(Thread.currentThread());
            if ( newMonitor.hasFailedHeartbeat() ) shutdown(newMonitor);
        }
    }

    private static long milliTime() {
        return TimeUnit.NANOSECONDS.convert(System.nanoTime(), TimeUnit.MILLISECONDS);
    }

    private synchronized void shutdown() {
        if ( null == _reaper ) return;
        _activeMonitorInfo = null;
        _reaper.cancel(false);
        _heartbeat.cancel(false);
        _reaper = null;
        _heartbeat = null;
        while ( ! _monitorsToShutdown.isEmpty() ) {
            shutdown(_monitorsToShutdown.iterator().next());
        }
    }

    private synchronized void shutdown(MonitorInfoImpl monitor) {
        if ( _activeMonitorInfo == monitor ) {
            // Make sure this is NOT the active monitor:
            _activeMonitorInfo = null;
        }

        // Interrupt all running threads:
        for ( Thread thread : monitor.getRunningThreads() ) {
            thread.interrupt();
        }

        while ( true ) {
            Thread thread = null;
            try {
                thread = monitor.getRunningThreads()
                    .iterator().next();
            } catch ( NoSuchElementException ex ) {
                break; // runningthreads is empty!
            }
            long waitTime = monitor.getLastHeartbeatMillis()
                + ( HEARTBEAT_INTERVAL_MS * REAP_INTERVALS )
                - milliTime();
            if ( waitTime <= 0 ) {
                LOG.error("Failed to halt threads, failing this monitor");
                monitor.forceHeartbeatFailure();
            }
            try {
                thread.join(waitTime);
            } catch ( InterruptedException ex ) {
                Thread.currentThread().interrupt();
                continue;
            }
        }

        if ( ! monitor.hasFailedHeartbeat() ) {
            _monitors.deleteItem(monitor.getMonitorId(), null);
        }
        _monitorsToShutdown.remove(monitor);
    }

    private List<MonitorInfoImpl> listMonitors(PageIterator iter) {
        return _monitors.scanItems(iter);
    }

    private void reaper() {
        synchronized ( _heartbeats ) {
            Set<String> monitorIds = new HashSet<>(_heartbeats.keySet());
            for ( PageIterator iter : new PageIterator().pageSize(100) ) {
                for ( MonitorInfoImpl monitor : listMonitors(iter) ) {
                    String monitorId = monitor.getMonitorId();
                    monitorIds.remove(monitorId);
                    Long lastHB = _heartbeats.get(monitorId);
                    _heartbeats.put(monitorId, monitor.getHeartbeat());
                    // New monitor observed:
                    if ( null == lastHB ) {
                        continue;
                    }
                    // Heartbeats observed:
                    if ( monitor.getHeartbeat() != lastHB ) {
                        continue;
                    }
                    // Dead monitor observed:
                    reap(monitor);
                }
            }
            // Keep the _heartbeats map tidy:
            for ( String monitorId : monitorIds ) {
                _heartbeats.remove(monitorId);
            }
        }
    }

    private synchronized void reap(MonitorInfoImpl monitor) {
        // Add task to delete references:
        _taskManager.addTask(
            _reapMonitorTask.build(
                _taskManager.createTask(), monitor.getMonitorId()));
        _monitors.deleteItem(monitor.getMonitorId(), null);
    }

    private synchronized void heartbeat() {
        // We are already shutting down:
        if ( null == _activeMonitorInfo || _activeMonitorInfo.hasFailedHeartbeat() ) return;

        try {
            _activeMonitorInfo.heartbeatWasPerformed();
            _monitors.updateItem(_activeMonitorInfo.getMonitorId(), null)
                .increment("hb", 1)
                .when((expr) -> expr.exists("id"));
            return;
        } catch ( Throwable ex ) {
            if ( ex instanceof RollbackException ) {
                // This could happen if the computer is put to sleep:
                LOG.warn("Detected monitor deletion, forcing all tasks to stop (perhaps computer sleeped).");
            } else {
                LOG.error(ex.getMessage(), ex);
            }
            _activeMonitorInfo.forceHeartbeatFailure();
            shutdown(_activeMonitorInfo);
        }
    }
}
