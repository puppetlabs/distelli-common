package com.distelli.monitor;

@FunctionalInterface
public interface Monitored {
    public void run(MonitorInfo info);
}
