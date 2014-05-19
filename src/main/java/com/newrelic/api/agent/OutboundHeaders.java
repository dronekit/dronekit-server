package com.newrelic.api.agent;

public interface OutboundHeaders {
    /**
     * Sets a response header with the given name and value.
     * 
     * @param name Name to add to the response header.
     * @param value Value to add to the response header.
     */
    void setHeader(String name, String value);

    /**
     * Returns the {@link HeaderType} of this implementation. This is used to customize the format of the keys for the
     * header.
     * 
     * @return the specific type of the header.
     */
    HeaderType getHeaderType();
}
