package com.newrelic.api.agent;

import java.util.Enumeration;

/**
 * Represents a web request.
 * 
 * The implementation of this interface does not need to be servlet specific, but the api is based on the servlet spec.
 * 
 * @author sdaubin
 * 
 * @see NewRelic#setRequestAndResponse(Request, Response)
 * @see javax.servlet.http.HttpServletRequest
 */
public interface Request extends InboundHeaders {
    /**
     * Returns the part of this request's URL from the protocol name up to the query string in the first line of the
     * HTTP request.
     * 
     * @return Request URL from the protocol name to query string.
     */
    String getRequestURI();

    /**
     * Returns the login of the user making this request, if the user has been authenticated, or null if the user has
     * not been authenticated.
     * 
     * @return Login of the user making this request.
     */
    String getRemoteUser();

    /**
     * Returns an Enumeration of String objects containing the names of the parameters contained in this request. If the
     * request has no parameters, the method returns an empty Enumeration or null.
     * 
     * @return An enumeration of String objects containing the names of the parameters contained in the request.
     */
    @SuppressWarnings("rawtypes")
    Enumeration getParameterNames();

    /**
     * Returns an array of String objects containing all of the values the given request parameter has, or null if the
     * parameter does not exist. If the parameter has a single value, the array has a length of 1.
     * 
     * @param name The name of the attribute.
     * @return All values of the given input request parameter, or null if the input name does not exist.
     */
    String[] getParameterValues(String name);

    /**
     * Returns the value of the named attribute as an Object, or null if no attribute of the given name exists.
     * 
     * @param name The name of the attribute to return.
     * @return Value of the named input attribute, or null if no attribute with the given input name.
     */
    Object getAttribute(String name);

    /**
     * Returns the value for the cookie with the given name, or null if the cookie does not exist.
     * 
     * @param The name of the cookie
     * @return The value of the cookie or null if it does not exist.
     */
    String getCookieValue(String name);
}
