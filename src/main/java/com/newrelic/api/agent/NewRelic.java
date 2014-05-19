package com.newrelic.api.agent;

import java.util.Map;

/**
 * The New Relic api. Consumers of this api can either add the newrelic-api.jar to their classpath or copy this source
 * file into their source.
 * 
 * @author sdaubin
 * 
 */
public final class NewRelic {

    // ************************** Metric API ****************************************//

    /**
     * Record a metric value for the given name.
     * 
     * @param name The name of the metric. The metric is not recorded if the name is null or the empty string.
     * @param value The value of the metric.
     */
    public static void recordMetric(String name, float value) {
    }

    /**
     * Record a response time in milliseconds for the given metric name.
     * 
     * @param name The name of the metric. The response time is not recorded if the name is null or the empty string.
     * @param millis The response time in milliseconds.
     */
    public static void recordResponseTimeMetric(String name, long millis) {
    }

    /**
     * Increment the metric counter for the given name.
     * 
     * @param name The name of the metric to increment.
     */
    public static void incrementCounter(String name) {
    }

    /**
     * Increment the metric counter for the given name.
     * 
     * @param name The name of the metric to increment.
     * @param count The amount in which the metric should be incremented.
     */
    public static void incrementCounter(String name, int count) {
    }

    // ************************** Error collector ***********************************//

    /**
     * Notice an exception and report it to New Relic. If this method is called within a transaction, the exception will
     * be reported with the transaction when it finishes. If it is invoked outside of a transaction, a traced error will
     * be created and reported to New Relic.
     * 
     * @param throwable The throwable to notice and report.
     * @param params Custom parameters to include in the traced error. May be null.
     */
    public static void noticeError(Throwable throwable, Map<String, String> params) {
    }

    /**
     * Report an exception to New Relic.
     * 
     * @param throwable The throwable to report.
     * @see #noticeError(Throwable, Map)
     */
    public static void noticeError(Throwable throwable) {
    }

    /**
     * Notice an error and report it to New Relic. If this method is called within a transaction, the error message will
     * be reported with the transaction when it finishes. If it is invoked outside of a transaction, a traced error will
     * be created and reported to New Relic.
     * 
     * @param message The error message to be reported.
     * @param params Custom parameters to include in the traced error. May be null
     */
    public static void noticeError(String message, Map<String, String> params) {
    }

    /**
     * Notice an error and report it to New Relic. If this method is called within a transaction, the error message will
     * be reported with the transaction when it finishes. If it is invoked outside of a transaction, a traced error will
     * be created and reported to New Relic.
     * 
     * @param message Message to report with a transaction when it finishes.
     */
    public static void noticeError(String message) {
    }

    // **************************** Transaction APIs ********************************//

    /**
     * Add a key/value pair to the current transaction. These are reported in errors and transaction traces.
     * 
     * @param key Custom parameter key.
     * @param value Custom parameter value. @
     */
    public static void addCustomParameter(String key, Number value) {
    }

    /**
     * Add a key/value pair to the current transaction. These are reported in errors and transaction traces.
     * 
     * @param key Custom parameter key.
     * @param value Custom parameter value. @
     */
    public static void addCustomParameter(String key, String value) {
    }

    /**
     * Set the name of the current transaction.
     * 
     * @param category Metric category. If the input is null, then the default will be used.
     * @param name The name of the transaction starting with a forward slash. example: /store/order
     */
    public static void setTransactionName(String category, String name) {
    }

    /**
     * Ignore the current transaction.
     */
    public static void ignoreTransaction() {
    }

    /**
     * Ignore the current transaction for calculating Apdex score.
     */
    public static void ignoreApdex() {
    }

    /**
     * Sets the request and response instances for the current transaction. Use this API to generate web transactions
     * for custom web request dispatchers.
     * 
     * @param request The current transaction's request.
     * @param response The current transaction's response.
     */
    public static void setRequestAndResponse(Request request, Response response) {
    }

    // **************************** Real User Monitoring (RUM) ********************************
    // API calls to support manual instrumentation of RUM if auto instrumentation is not available.
    // Get the JavaScript header and footer that should be inserted into the HTML response for browser-side monitoring.
    // See this article for details:
    // http://support.newrelic.com/help/kb/java/real-user-monitoring-in-java

    /**
     * Get the RUM JavaScript header for the current web transaction.
     * 
     * @return RUM JavaScript header for the current web transaction.
     */
    public static String getBrowserTimingHeader() {
        return "";
    }

    /**
     * Get the RUM JavaScript footer for the current web transaction.
     * 
     * @return RUM JavaScript footer for the current web transaction.
     */
    public static String getBrowserTimingFooter() {
        return "";
    }

    /**
     * Set the user name to associate with the RUM JavaScript footer for the current web transaction.
     * 
     * @param name User name to associate with the RUM JavaScript footer.
     */
    public static void setUserName(String name) {
    }

    /**
     * Set the account name to associate with the RUM JavaScript footer for the current web transaction.
     * 
     * @param name Account name to associate with the RUM JavaScript footer.
     */
    public static void setAccountName(String name) {
    }

    /**
     * Set the product name to associate with the RUM JavaScript footer for the current web transaction.
     * 
     * @param name Product name to associate with the RUM JavaScript footer.
     */
    public static void setProductName(String name) {
    }

}
