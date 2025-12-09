package com.bootsandcats.profileui.config;

import static org.mockito.Mockito.mock;

import com.bootsandcats.profileui.repository.MongoProfileRepository;
import com.bootsandcats.profileui.repository.ProfileRepository;
import com.mongodb.client.MongoClient;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Test configuration that provides mock MongoDB beans.
 *
 * <p>This prevents Micronaut from trying to connect to a real MongoDB instance during unit tests.
 * The environment "test" activates this configuration.
 */
@Factory
@Requires(env = "test")
public class TestMongoConfig {

    @Singleton
    @Primary
    @Replaces(MongoClient.class)
    public MongoClient mongoClient() {
        return mock(MongoClient.class);
    }

    @Singleton
    @Primary
    @Replaces(MongoProfileRepository.class)
    public ProfileRepository profileRepository() {
        return mock(ProfileRepository.class);
    }
}
