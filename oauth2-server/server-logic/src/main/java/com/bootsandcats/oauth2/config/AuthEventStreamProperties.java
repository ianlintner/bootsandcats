package com.bootsandcats.oauth2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for publishing authentication/audit events to Redis Streams.
 */
@Component
@ConfigurationProperties(prefix = "auth.events")
public class AuthEventStreamProperties {

    /**
     * Whether publishing to the stream is enabled.
     */
    private boolean enabled = true;

    /**
     * Stream key where auth events are appended.
     */
    private String streamName = "auth:events";

    /**
     * Optional maximum length for the stream. Zero or negative disables trimming.
     */
    private long maxLength = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public long getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(long maxLength) {
        this.maxLength = maxLength;
    }
}
