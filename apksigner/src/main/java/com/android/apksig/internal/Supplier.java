package com.android.apksig.internal;

/**
 * @author Windysha
 */
public interface Supplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();
}
