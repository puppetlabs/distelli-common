package com.distelli.monitor;

/**
 * Usage:
 *
 * <pre>
 *   {@literal @}Inject private Monitor _monitor;
 *
 *       _monitor.monitor((info) -&gt; {
 *           // This function will be interrupt()ed
 *           // if this monitors heartbeat fails.
 *           compute(info.getMonitorId());
 *       });
 * </pre>
 */
public interface Monitor {
    public void monitor(Monitored task);
    public boolean isActiveMonitor(MonitorInfo monitorInfo);
}
