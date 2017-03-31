package com.distelli.monitor;

/**
 * This interface exists so you can use Guice to inject an implementation.
 */
public interface ProductVersion {
    /**
     * Returns information about the product and version of that product. This metadata
     * information is stored in the monitors table.
     */
    public String toString();
}
