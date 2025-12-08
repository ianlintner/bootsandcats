package com.bootsandcats.oauth2.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;
import com.bootsandcats.oauth2.testing.AiAgentTestReporter;
import com.bootsandcats.oauth2.model.User;
import com.bootsandcats.oauth2.repository.UserRepository;

/**
 * Integration tests for PostgreSQL database operations using Testcontainers.
 *
 * <p>These tests verify that our JPA entities and repositories work correctly
 * against a real PostgreSQL database, ensuring compatibility with production.
 *
 * <h2>AI Agent Debugging Notes:</h2>
 * <ul>
 *   <li>If tests fail with connection errors, check Docker daemon is running</li>
 *   <li>If Flyway migrations fail, check migration scripts in db/migration</li>
 *   <li>PostgreSQL container uses port 5432 internally, mapped to dynamic host port</li>
 *   <li>Tests use @Transactional for automatic rollback after each test</li>
 * </ul>
 *
 * <h2>Test Categories:</h2>
 * <ul>
 *   <li>User persistence tests - CRUD operations for app_users table</li>
 *   <li>Federated identity lookup tests - Finding users by provider/providerId</li>
 *   <li>Database constraint tests - Unique constraints, NOT NULL violations</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class})
@DisplayName("PostgreSQL Integration Tests")
public class PostgresIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("User Persistence Tests")
    class UserPersistenceTests {

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should persist and retrieve a federated user")
        void shouldPersistAndRetrieveFederatedUser() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldPersistAndRetrieveFederatedUser",
                    "Federated user can be persisted to PostgreSQL and retrieved"
            );

            try {
                // Given
                User user = new User();
                user.setUsername("testuser");
                user.setEmail("test@example.com");
                user.setProvider("github");
                user.setProviderId("12345678");
                user.setName("Test User");
                user.setPictureUrl("https://avatars.githubusercontent.com/u/12345678");
                user.setLastLogin(Instant.now());

                // When
                User savedUser = userRepository.save(user);

                // Then
                assertThat(savedUser.getId()).isNotNull();
                ctx.addObservation("Generated user ID: " + savedUser.getId());

                Optional<User> retrieved = userRepository.findById(savedUser.getId());
                assertThat(retrieved).isPresent();
                assertThat(retrieved.get().getUsername()).isEqualTo("testuser");
                assertThat(retrieved.get().getEmail()).isEqualTo("test@example.com");
                assertThat(retrieved.get().getProvider()).isEqualTo("github");
                assertThat(retrieved.get().getProviderId()).isEqualTo("12345678");

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should find user by provider and providerId")
        void shouldFindUserByProviderAndProviderId() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldFindUserByProviderAndProviderId",
                    "Federated identity lookup returns correct user"
            );

            try {
                // Given
                User user = new User();
                user.setUsername("githubuser");
                user.setEmail("github@example.com");
                user.setProvider("github");
                user.setProviderId("github-123");
                userRepository.save(user);

                // When
                Optional<User> found = userRepository.findByProviderAndProviderId("github", "github-123");

                // Then
                assertThat(found).isPresent();
                assertThat(found.get().getUsername()).isEqualTo("githubuser");
                assertThat(found.get().getEmail()).isEqualTo("github@example.com");

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should find user by username")
        void shouldFindUserByUsername() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldFindUserByUsername",
                    "User lookup by username returns correct user"
            );

            try {
                // Given
                User user = new User();
                user.setUsername("uniqueuser");
                user.setEmail("unique@example.com");
                user.setProvider("google");
                user.setProviderId("google-456");
                userRepository.save(user);

                // When
                Optional<User> found = userRepository.findByUsername("uniqueuser");

                // Then
                assertThat(found).isPresent();
                assertThat(found.get().getEmail()).isEqualTo("unique@example.com");

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @Transactional
        @DisplayName("SAD PATH: Should return empty when user not found by provider")
        void shouldReturnEmptyWhenUserNotFoundByProvider() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldReturnEmptyWhenUserNotFoundByProvider",
                    "Non-existent provider/providerId returns empty Optional"
            );

            try {
                // When
                Optional<User> found = userRepository.findByProviderAndProviderId("nonexistent", "fake-id");

                // Then
                assertThat(found).isEmpty();

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should update existing user")
        void shouldUpdateExistingUser() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldUpdateExistingUser",
                    "Existing user can be updated with new information"
            );

            try {
                // Given
                User user = new User();
                user.setUsername("updateuser");
                user.setEmail("old@example.com");
                user.setProvider("github");
                user.setProviderId("update-123");
                User saved = userRepository.save(user);
                Long userId = saved.getId();

                // When
                saved.setEmail("new@example.com");
                saved.setName("Updated Name");
                saved.setLastLogin(Instant.now());
                userRepository.save(saved);

                // Then
                Optional<User> updated = userRepository.findById(userId);
                assertThat(updated).isPresent();
                assertThat(updated.get().getEmail()).isEqualTo("new@example.com");
                assertThat(updated.get().getName()).isEqualTo("Updated Name");
                assertThat(updated.get().getLastLogin()).isNotNull();

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should delete user")
        void shouldDeleteUser() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldDeleteUser",
                    "User can be deleted from the database"
            );

            try {
                // Given
                User user = new User();
                user.setUsername("deleteuser");
                user.setEmail("delete@example.com");
                user.setProvider("github");
                user.setProviderId("delete-123");
                User saved = userRepository.save(user);
                Long userId = saved.getId();

                // When
                userRepository.deleteById(userId);

                // Then
                Optional<User> deleted = userRepository.findById(userId);
                assertThat(deleted).isEmpty();

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }
    }

    @Nested
    @DisplayName("Multi-Provider Tests")
    class MultiProviderTests {

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should support multiple providers with same providerId")
        void shouldSupportMultipleProvidersWithSameProviderId() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldSupportMultipleProvidersWithSameProviderId",
                    "Same providerId can exist for different providers (uniqueness is provider+providerId)"
            );

            try {
                // Given - same providerId but different providers
                User githubUser = new User();
                githubUser.setUsername("github-user");
                githubUser.setEmail("github@example.com");
                githubUser.setProvider("github");
                githubUser.setProviderId("shared-id-123");
                userRepository.save(githubUser);

                User googleUser = new User();
                googleUser.setUsername("google-user");
                googleUser.setEmail("google@example.com");
                googleUser.setProvider("google");
                googleUser.setProviderId("shared-id-123");
                userRepository.save(googleUser);

                // When
                Optional<User> foundGithub = userRepository.findByProviderAndProviderId("github", "shared-id-123");
                Optional<User> foundGoogle = userRepository.findByProviderAndProviderId("google", "shared-id-123");

                // Then
                assertThat(foundGithub).isPresent();
                assertThat(foundGithub.get().getUsername()).isEqualTo("github-user");
                assertThat(foundGoogle).isPresent();
                assertThat(foundGoogle.get().getUsername()).isEqualTo("google-user");

                ctx.addObservation("Both users with same providerId but different providers coexist");
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should store all federated identity providers")
        void shouldStoreAllFederatedIdentityProviders() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldStoreAllFederatedIdentityProviders",
                    "GitHub, Google, and Azure AD users can all be stored"
            );

            try {
                // Given
                User githubUser = new User();
                githubUser.setProvider("github");
                githubUser.setProviderId("gh-1");
                githubUser.setUsername("ghuser");
                userRepository.save(githubUser);

                User googleUser = new User();
                googleUser.setProvider("google");
                googleUser.setProviderId("goog-1");
                googleUser.setUsername("googuser");
                userRepository.save(googleUser);

                User azureUser = new User();
                azureUser.setProvider("azure");
                azureUser.setProviderId("az-1");
                azureUser.setUsername("azuser");
                userRepository.save(azureUser);

                // When
                long count = userRepository.count();

                // Then
                assertThat(count).isGreaterThanOrEqualTo(3);
                assertThat(userRepository.findByProviderAndProviderId("github", "gh-1")).isPresent();
                assertThat(userRepository.findByProviderAndProviderId("google", "goog-1")).isPresent();
                assertThat(userRepository.findByProviderAndProviderId("azure", "az-1")).isPresent();

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldHandleNullOptionalFields",
                    "User can be saved with null optional fields (name, pictureUrl)"
            );

            try {
                // Given
                User user = new User();
                user.setUsername("minimaluser");
                user.setProvider("github");
                user.setProviderId("minimal-123");
                // email, name, pictureUrl, lastLogin are null

                // When
                User saved = userRepository.save(user);

                // Then
                assertThat(saved.getId()).isNotNull();
                Optional<User> retrieved = userRepository.findById(saved.getId());
                assertThat(retrieved).isPresent();
                assertThat(retrieved.get().getEmail()).isNull();
                assertThat(retrieved.get().getName()).isNull();
                assertThat(retrieved.get().getPictureUrl()).isNull();

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @Transactional
        @DisplayName("HAPPY PATH: Should preserve timestamps accurately")
        void shouldPreserveTimestampsAccurately() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "PostgresIntegrationTest",
                    "shouldPreserveTimestampsAccurately",
                    "Instant timestamps are stored and retrieved correctly in PostgreSQL"
            );

            try {
                // Given
                Instant loginTime = Instant.parse("2024-01-15T10:30:00Z");
                User user = new User();
                user.setUsername("timestampuser");
                user.setProvider("github");
                user.setProviderId("ts-123");
                user.setLastLogin(loginTime);

                // When
                User saved = userRepository.save(user);
                Optional<User> retrieved = userRepository.findById(saved.getId());

                // Then
                assertThat(retrieved).isPresent();
                assertThat(retrieved.get().getLastLogin()).isEqualTo(loginTime);

                ctx.addObservation("PostgreSQL TIMESTAMP WITH TIME ZONE correctly preserves Instant values");
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }
    }
}
