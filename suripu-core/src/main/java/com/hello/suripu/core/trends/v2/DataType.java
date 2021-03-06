package com.hello.suripu.core.trends.v2;

/**
 * Created by ksg on 01/21/16
 */
public enum DataType {
    SCORES("SCORES"), // {0, 100} inclusive
    HOURS("HOURS"), // floats
    PERCENTS("PERCENTS"); // {0.0, 1.0} should add up to 1.0

    protected String value;
    private DataType(final String value) { this.value = value; }
}
