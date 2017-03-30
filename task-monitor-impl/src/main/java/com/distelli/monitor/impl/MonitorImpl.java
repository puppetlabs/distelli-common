package com.distelli.monitor.impl;

import com.distelli.monitor.Monitored;
import com.distelli.monitor.Monitor;
import com.distelli.monitor.MonitorInfo;
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
import javax.inject.Singleton;

@Singleton
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

    // synchronize(_heartbeats):
    private Map<String, Long> _heartbeats = new HashMap<>();

    // synchronize(this):
    private MonitorInfoImpl _activeMonitorInfo;
    // synchronize(this):
    private ScheduledFuture<?> _reaper;
    // synchronize(this):
    private ScheduledFuture<?> _heartbeat;
    // synchronize(this):
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
        MonitorInfoImpl monitor = null;
        MonitorInfoImpl shutdownMonitor = null;
        synchronized ( this ) {
            if ( null == _reaper ) {
                throw new ShuttingDownException();
            }
            monitor = _activeMonitorInfo;

            if ( null == monitor || monitor.hasFailedHeartbeat() ) {
                monitor = new MonitorInfoImpl(
                    _productVersion.toString(),
                    HEARTBEAT_INTERVAL_MS * REAP_INTERVALS);
                shutdownMonitor = _activeMonitorInfo;
                _monitorsToShutdown.add(monitor);
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug("activeMonitor="+_activeMonitorInfo+" new="+monitor);
                }
                _monitors.putItem(monitor);
                _activeMonitorInfo = monitor;
            } else {
                monitor = _activeMonitorInfo;
            }
        }
        if ( null != shutdownMonitor ) {
            shutdown(shutdownMonitor);
        }
        try {
            monitor.captureRunningThread();
            task.run(monitor);
        } finally {
            monitor.releaseRunningThread();
            if ( monitor.hasFailedHeartbeat() ) shutdown(monitor);
        }
    }

    @Override
    public synchronized boolean isActiveMonitor(MonitorInfo monitorInfo) {
        return _activeMonitorInfo == monitorInfo;
    }

    private static long milliTime() {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    private void shutdown() {
        synchronized ( this ) {
            if ( null == _reaper ) return;
            _activeMonitorInfo = null;
            _reaper.cancel(false);
            _heartbeat.cancel(false);
            _reaper = null;
            _heartbeat = null;
        }
        while ( true ) {
            MonitorInfoImpl monitor = null;
            synchronized ( this ) {
                if ( ! _monitorsToShutdown.isEmpty() ) {
                    monitor = _monitorsToShutdown.iterator().next();
                }
            }
            if ( null == monitor ) break;
            shutdown(monitor);
        }
    }

    private void shutdown(MonitorInfoImpl monitor) {
        synchronized ( this ) {
            if ( _activeMonitorInfo == monitor ) {
                // Make sure this is NOT the active monitor:
                _activeMonitorInfo = null;
            }
        }

        long waitTime = monitor.getLastHeartbeatMillis()
            + ( HEARTBEAT_INTERVAL_MS * REAP_INTERVALS )
            - milliTime();
        if ( ! monitor.interruptAndWaitForRunningThreads(waitTime) ) {
            LOG.error("Failed to halt all threads, failing this monitor");
            monitor.forceHeartbeatFailure();
        }

        if ( ! monitor.hasFailedHeartbeat() ) {
            _monitors.deleteItem(monitor.getMonitorId(), null);
        }
        synchronized ( this ) {
            _monitorsToShutdown.remove(monitor);
        }
    }

    private List<MonitorInfoImpl> listMonitors(PageIterator iter) {
        return _monitors.scanItems(iter);
    }

    private void reaper() {
        try {
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
        } catch ( Throwable ex ) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void reap(MonitorInfoImpl monitor) {
        LOG.debug("Adding task to reap monitorId="+monitor.getMonitorId());
        // Add task to delete references:
        _taskManager.addTask(
            _reapMonitorTask.build(
                _taskManager.createTask(), monitor.getMonitorId()));
        _monitors.deleteItem(monitor.getMonitorId(), null);
    }

    private void heartbeat() {
        try {
            MonitorInfoImpl monitor = null;
            try {
                synchronized ( this ) {
                    monitor = _activeMonitorInfo;
                }
                // We are already shutting down:
                if ( null == monitor || monitor.hasFailedHeartbeat() ) return;
                _monitors.updateItem(monitor.getMonitorId(), null)
                    .increment("hb", 1)
                    .when((expr) -> expr.exists("id"));
                monitor.heartbeatWasPerformed();
                return;
            } catch ( Throwable ex ) {
                if ( ex instanceof RollbackException ) {
                    // This could happen if the computer is put to sleep:
                    LOG.warn("Detected monitor deletion, forcing all tasks to stop (perhaps computer sleeped).");
                } else {
                    LOG.error(ex.getMessage(), ex);
                }
                if ( null != monitor ) {
                    monitor.forceHeartbeatFailure();
                    shutdown(monitor);
                }
            }
        } catch ( Throwable ex ) {
            LOG.error(ex.getMessage(), ex);
        }
    }
}
