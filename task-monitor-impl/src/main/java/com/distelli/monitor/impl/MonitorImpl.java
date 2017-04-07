package com.distelli.monitor.impl;

import com.distelli.jackson.transform.TransformModule;
import com.distelli.monitor.Monitor;
import com.distelli.monitor.MonitorInfo;
import com.distelli.monitor.Monitored;
import com.distelli.monitor.ProductVersion;
import com.distelli.monitor.TaskContext;
import com.distelli.monitor.TaskInfo;
import com.distelli.monitor.TaskManager;
import com.distelli.persistence.AttrType;
import com.distelli.persistence.Index;
import com.distelli.persistence.IndexDescription;
import com.distelli.persistence.PageIterator;
import com.distelli.persistence.TableDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.RollbackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    // synchronize(this):
    private boolean _shuttingDown = false;

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
    }

    private synchronized void scheduleHeartbeat() {
        if ( _shuttingDown ) throw new ShuttingDownException();
        if ( null == _reaper ) {
            long reapInterval = HEARTBEAT_INTERVAL_MS * REAP_INTERVALS;
            _reaper = _executor.scheduleAtFixedRate(
                this::reaper,
                ThreadLocalRandom.current().nextLong(reapInterval),
                reapInterval,
                TimeUnit.MILLISECONDS);
        }
        if ( null == _heartbeat ) {
            _heartbeat = _executor.scheduleAtFixedRate(
                this::heartbeat,
                ThreadLocalRandom.current().nextLong(HEARTBEAT_INTERVAL_MS),
                HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public MonitorInfo getMonitorInfo(String monitorId) {
        return _monitors.getItem(monitorId);
    }

    @Override
    public void monitor(Monitored task) {
        MonitorInfoImpl monitor = null;
        MonitorInfoImpl shutdownMonitor = null;
        synchronized ( this ) {
            scheduleHeartbeat();
            monitor = _activeMonitorInfo;

            if ( null == monitor || monitor.hasFailedHeartbeat() ) {
                monitor = new MonitorInfoImpl(
                    _productVersion.toString(),
                    HEARTBEAT_INTERVAL_MS * (REAP_INTERVALS - 1));
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
        if ( null != shutdownMonitor && ! shutdownMonitor.isRunningInMonitoredThread() ) {
            shutdown(shutdownMonitor, true);
        }
        try {
            monitor.captureRunningThread();
            task.run(monitor);
        } finally {
            if ( monitor.releaseRunningThread() &&
                 monitor.hasFailedHeartbeat() )
            {
                // Nothing should be running, so set mayInterruptIfRunning=false
                shutdown(monitor, false);
            }
        }
    }

    @Override
    public synchronized boolean isActiveMonitor(MonitorInfo monitorInfo) {
        return _activeMonitorInfo == monitorInfo;
    }

    private static long milliTime() {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void shutdownMonitor(boolean mayInterruptIfRunning) {
        synchronized ( this ) {
            _shuttingDown = true;
            _activeMonitorInfo = null;
            if ( null != _reaper ) {
                _reaper.cancel(false);
                _reaper = null;
            }
            if ( null != _heartbeat ) {
                _heartbeat.cancel(false);
                _heartbeat = null;
            }
        }
        while ( true ) {
            MonitorInfoImpl monitor = null;
            synchronized ( this ) {
                if ( ! _monitorsToShutdown.isEmpty() ) {
                    monitor = _monitorsToShutdown.iterator().next();
                }
            }
            if ( null == monitor ) break;
            shutdown(monitor, mayInterruptIfRunning);
        }
    }

    private void shutdown(MonitorInfoImpl monitor, boolean mayInterruptIfRunning) {
        monitor.forceHeartbeatFailure();
        synchronized ( this ) {
            if ( _activeMonitorInfo == monitor ) {
                // Make sure this is NOT the active monitor:
                _activeMonitorInfo = null;
            }
        }

        long waitTime = monitor.getLastHeartbeatMillis()
            + ( HEARTBEAT_INTERVAL_MS * REAP_INTERVALS )
            - milliTime();

        // Make sure we always have at least SOME wait time
        if ( waitTime < 200 ) {
            LOG.warn("Adjusting waitTime="+waitTime+"ms to 200ms when running shutdown("+monitor.getMonitorId()+")");
            waitTime = 200;
        }

        if ( ! monitor.interruptAndWaitForRunningThreads(waitTime, mayInterruptIfRunning) ) {
            String msg = "Failed to halt the following threads in "+waitTime+"ms. Halting the JVM:\n"+
                monitor.dumpThreads();
            LOG.error(msg);
            System.err.println(msg);
            System.exit(-1);
        }

        Task task = new Task();
        task.entityId = monitor.getMonitorId();
        try {
            // Try to run the reaper inline so we can keep the DB tidy:
            TaskInfo taskInfo = _reapMonitorTask.run(new TaskContext() {
                    @Override
                    public TaskInfo getTaskInfo() {
                        return task;
                    }
                    @Override
                    public MonitorInfo getMonitorInfo() {
                        return monitor;
                    }
                    @Override
                    public void commitCheckpointData(byte[] checkpointData) {}
                });
            if ( null == taskInfo ) {
                _monitors.deleteItem(monitor.getMonitorId(), null);
            } // else: let the reaper take care of it...
        } catch ( Throwable ex ) {
            LOG.error("ReapMonitorTaskFailed: "+ex.getMessage(), ex);
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
                        reapMonitorId(monitor.getMonitorId());
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

    // public only for testing purposes:
    public TaskInfo reapMonitorId(String monitorId) {
        LOG.debug("Adding task to reap monitorId="+monitorId);
        // Add task to delete references:
        TaskInfo task =
            _reapMonitorTask.build(_taskManager.createTask(), monitorId);
        _taskManager.addTask(task);
        _monitors.deleteItem(monitorId, null);
        return task;
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
                    shutdown(monitor, true);
                }
            }
        } catch ( Throwable ex ) {
            LOG.error(ex.getMessage(), ex);
        }
    }
}
