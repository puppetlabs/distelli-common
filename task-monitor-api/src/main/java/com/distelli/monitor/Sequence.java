package com.distelli.monitor;

/**
 * Implement this interface if you already have a sequence generator. Alternatively,
 * you can use the SequenceImpl that is provided by the task-monitor-impl.
 */
public interface Sequence {
    /**
     * Returns the next unique identifier for the named sequence. This method
     * should never return the same identifier for the same named sequence.
     *
     * @param name - the name of the sequence.
     *
     * @return a unique identifier for the sequence.
     */
    public long next(String name);
}
