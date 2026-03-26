package com.github.tgo18.zrpc.core.protocol;

/**
 * Represents an exported service. Calling {@link #unexport()} removes the service
 * from the server so it no longer accepts incoming requests.
 *
 * @param <T> service interface type
 */
public interface Exporter<T> {

    /** The underlying invoker this exporter wraps */
    Invoker<T> getInvoker();

    /** Stop accepting requests for this service */
    void unexport();
}
