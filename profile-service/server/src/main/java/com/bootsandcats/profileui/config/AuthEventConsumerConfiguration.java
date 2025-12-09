package com.bootsandcats.profileui.config;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuration for consuming auth events from Redis Streams. */
@ConfigurationProperties("auth.events.consumer")
public class AuthEventConsumerConfiguration {

    private boolean enabled = true;
    private String stream = "auth:events";
    private String group = "profile-service";
    private String consumerName = "profile-service-1";
    private int batchSize = 10;
    private Duration pollInterval = Duration.ofSeconds(5);
    private boolean createGroup = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public boolean isCreateGroup() {
        return createGroup;
    }

    public void setCreateGroup(boolean createGroup) {
        this.createGroup = createGroup;
    }
}
