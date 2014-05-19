package com.newrelic.api.agent;

public interface InboundHeaders {
    /**
     * Returns the value of the specified request header as a String.
     * 
     * @param name The name of the desired request header.
     * @return Value of the input specified request header.
     */
    String getHeader(String name);

    /**
     * Returns the {@link HeaderType} of this implementation. This is used to customize the format of the keys for the
     * header.
     * 
     * @return the specific type of the header.
     */
    HeaderType getHeaderType();
}
