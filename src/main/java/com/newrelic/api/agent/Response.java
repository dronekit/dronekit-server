package com.newrelic.api.agent;

/**
 * Represents a response to a web request.
 * 
 * The implementation of this interface does not need to be servlet specific, but the api is based on the servlet spec.
 * 
 * @author sdaubin
 * 
 * @see NewRelic#setRequestAndResponse(Request, Response)
 * @see javax.servlet.http.HttpServletResponse
 */
public interface Response extends OutboundHeaders {
    /**
     * Returns the status code for this response.
     * 
     * @return The status code.
     * @throws Exception Problem getting the status.
     */
    int getStatus() throws Exception;

    /**
     * Returns the error status message, or null if there is none.
     * 
     * @return The error status message.
     * @throws Exception Problem getting status message.
     */
    String getStatusMessage() throws Exception;

    /**
     * Returns the response content type, or null if it is not available.
     * 
     * @return
     * @since 3.1.0
     */
    String getContentType();
}
