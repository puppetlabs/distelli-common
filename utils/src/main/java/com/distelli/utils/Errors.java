package com.distelli.utils;

import java.util.Arrays;
import java.util.concurrent.Callable;

public class Errors {
    /**
     * Usage:
     *
     * ... = Errors.rethrow(() -&gt; uncheckedMethod());
     *
     * Rethrows ONLY non-RuntimeExceptions as a new
     * chained RuntimeException.
     *
     * @param <V> is the type of the value returned by fn.
     *
     * @param fn is the function to immediately execute.
     *
     * @return the result of the fn, note that a VoidCallable
     *    variant exists if your function returns no result.
     */
    public static <V> V rethrow(Callable<V> fn) {
        try {
            return fn.call();
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    public static void rethrow(VoidCallable fn) {
        rethrow(fn.toCallable());
    }

    /**
     * Usage:
     *
     * try {
     *     future.get();
     * } catch ( ExecutionException ee ) {
     *     throw Errors.stitchCause(ee);
     * }
     *
     * The original exception type will be retained. Note that checked
     * exceptions will be wrapped in a RuntimeException. So, if you
     * want to handle a checked exception, you will need to do this:
     *
     * try {
     *     future.get();
     * } catch ( ExecutionException ee ) {
     *     RuntimeException ex = Errors.stitchCause(ee);
     *     Errors.throwIf(ex.getCause(), IOException.class);
     *     throw ex;
     * }
     *
     * @param ex an exception with a cause to stitch. If there is no
     *     cause, no stitching occurs and the original ex is returned
     *     (perhaps wrapped in a RuntimeException).
     *
     * @return either the original cause of ex with the stack trace
     *     stitched or the original cause wrapped in a RuntimeException
     *     if it is an unchecked exception.
     */
    public static RuntimeException stitchCause(Throwable ex) {
        Throwable cause = ex.getCause();
        if ( null != cause ) {
            cause = stitchStackTrace(ex.getCause(), ex.getStackTrace());
        }
        if ( cause instanceof RuntimeException ) {
            return (RuntimeException)cause;
        }
        return new RuntimeException(cause);
    }

    /**
     * @param <T> is the type that will be thrown.
     *
     * @param ex is the exception to throw.
     *
     * @param type is the type (or supertype) of ex.
     *
     * @throws T ex if it can be cast into type, otherwise nothing is done.
     */
    public static <T extends Throwable> void throwIf(Throwable ex, Class<T> type) throws T {
        if ( type.isInstance(ex) ) {
            throw type.cast(ex);
        }
    }

    /**
     * Appends stackTrace to the end of the passed in exception.
     *
     * @param <T> is the type of ex
     *
     * @param ex is the exception to stitch.
     *
     * @param stackTrace is the stack trace to append, call
     *    otherException.getStackTrace() to obtain this.
     *
     * @return ex
     */
    public static <T extends Throwable> T stitchStackTrace(T ex, StackTraceElement[] stackTrace) {
        ex.setStackTrace(concatenate(ex.getStackTrace(), stackTrace));
        return ex;
    }

    private static <T> T[] concatenate(T[] a, T[] b) {
        if ( null == a ) return b;
        if ( null == b ) return a;
        int aLen = a.length;
        int bLen = b.length;
        T[] c = Arrays.copyOf(a, aLen+bLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }
}
