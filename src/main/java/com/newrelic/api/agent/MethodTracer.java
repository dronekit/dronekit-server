package com.newrelic.api.agent;

/**
 * A {@link MethodTracerFactory} can return a MethodTracer to be notified when a method invocation finishes.
 * 
 * @author sdaubin
 * @deprecated
 */
public interface MethodTracer {

    /**
     * Called if a method exits successfully.
     * 
     * @param returnValue The return value of the method invocation, or null if the return value is void.
     */
    void methodFinished(Object returnValue);

    /**
     * Called if a method exits because of an uncaught exception.
     * 
     * @param exception The uncaught exception thrown during the method invocation.
     */
    void methodFinishedWithException(Throwable exception);
}
