package com.distelli.monitor;

/**
 * A functional interface used to implement code execution which is
 *  monitored.
 *
 * @see Monitor interface for details.
 */
@FunctionalInterface
public interface Monitored {
    /**
     * @param info contains information about the active monitor
     *        being used.
     */
    public void run(MonitorInfo info);
}
