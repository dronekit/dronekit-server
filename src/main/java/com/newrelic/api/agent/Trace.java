package com.newrelic.api.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If you annotate a method with the Trace annotation, it will be automatically measured by New Relic, with the
 * following metrics - call count - calls per minute - average call time - standard deviation call time - min and max
 * call time.
 * 
 * Also these metrics will be traced inside the call scope of a named http transaction (e.g. "/servlets/myservlet" so
 * that New Relic can "break out" the response time of a given transaction by specific called methods.
 * 
 * Be mindful when using this attribute. When placed on relatively heavyweight operations such as database access
 * webservice invocation, its overhead will be negligible. On the other hand, if you placed it on a tight, frequently
 * called method (e.g. an accessor that is called thousands of times per second), then the tracer will introduce higher
 * overhead to your application.
 * 
 * @author cirne
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {
    public static final String NULL = "";

    /**
     * Sets the metric name for this tracer. If unspecified, the class / method name will be used.
     * 
     * @return
     */
    String metricName() default NULL;

    /**
     * Specifies one or more rollup metrics names. When the tracer finishes, and unscoped metric (a metric which is not
     * scoped to a specific transaction) will be recorded with the given metric name. This is useful when you want to
     * record a summary value across multiple methods or transaction names.
     * 
     * @return
     */
    String[] rollupMetricName() default NULL;

    /**
     * If true, this method will be considered the start of a transaction. When this method is invoked within the
     * context of an existing transaction this has no effect.
     * 
     * @return
     */
    boolean dispatcher() default false;

    /**
     * Names the current transaction using this tracer's metric name.
     * 
     * @return
     */
    boolean nameTransaction() default false;

    /**
     * @deprecated do not use
     * @return
     */
    String tracerFactoryName() default NULL;

    /**
     * Ignores the entire current transaction.
     * 
     * @return
     */
    boolean skipTransactionTrace() default false;

    /**
     * Excludes this traced method from transaction traces. Metric data is still generated.
     * 
     * @return
     */
    boolean excludeFromTransactionTrace() default false;

    /**
     * A leaf tracer will not have any child tracers. This is useful when all time should be attributed to the tracer
     * even if other trace points are encountered during its execution. For example, database tracers often act as leaf
     * so that all time is attributed to database activity even if instrumented external calls are made.
     * 
     * If a leaf tracer does not participate in transaction traces ({@link #excludeFromTransactionTrace()}) the agent
     * can create a tracer with lower overhead.
     * 
     * @return
     */
    boolean leaf() default false;
}