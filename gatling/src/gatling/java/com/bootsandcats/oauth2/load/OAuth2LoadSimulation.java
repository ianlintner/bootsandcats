package com.bootsandcats.oauth2.load;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.Base64;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Gatling load test simulation for OAuth2 Authorization Server.
 *
 * <p>Run with: mvn gatling:test -Pload-test
 */
public class OAuth2LoadSimulation extends Simulation {

    /**
     * Safely parse an integer system property, falling back to a default value if parsing fails.
     */
    private static int safeParseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println(
                    "Invalid integer value: '" + value + "'. Using fallback: " + defaultValue);
            return defaultValue;
        }
    }

    private final String baseUrl = System.getProperty("baseUrl", "http://localhost:9000");
    private final int users = safeParseInt(System.getProperty("users", "100"), 100);
    private final int rampUpSeconds = safeParseInt(System.getProperty("rampUp", "30"), 30);

    // HTTP Protocol Configuration
    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(baseUrl)
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/x-www-form-urlencoded")
                    .userAgentHeader("Gatling Load Test");

    // Basic auth header for m2m-client
    private final String basicAuthHeader =
            "Basic " + Base64.getEncoder().encodeToString("m2m-client:m2m-secret".getBytes());

    // Token request chain
    private final ChainBuilder requestToken =
            exec(
                    http("Client Credentials Token Request")
                            .post("/oauth2/token")
                            .header("Authorization", basicAuthHeader)
                            .formParam("grant_type", "client_credentials")
                            .formParam("scope", "api:read")
                            .check(status().is(200))
                            .check(jsonPath("$.access_token").saveAs("accessToken")));

    // Token introspection chain
    private final ChainBuilder introspectToken =
            exec(
                    http("Token Introspection")
                            .post("/oauth2/introspect")
                            .header("Authorization", basicAuthHeader)
                            .formParam("token", "#{accessToken}")
                            .check(status().is(200))
                            .check(jsonPath("$.active").is("true")));

    // JWKS fetch chain
    private final ChainBuilder fetchJwks =
            exec(http("Fetch JWKS").get("/oauth2/jwks").check(status().is(200)));

    // OIDC discovery chain
    private final ChainBuilder fetchOidcConfig =
            exec(
                    http("OIDC Discovery")
                            .get("/.well-known/openid-configuration")
                            .check(status().is(200)));

    // Health check chain
    private final ChainBuilder healthCheck =
            exec(http("Health Check").get("/actuator/health").check(status().is(200)));

    // Client Credentials Flow Scenario
    private final ScenarioBuilder clientCredentialsScenario =
            scenario("Client Credentials Flow")
                    .exec(requestToken)
                    .pause(Duration.ofMillis(100))
                    .exec(introspectToken);

    // Discovery Scenario
    private final ScenarioBuilder discoveryScenario =
            scenario("Discovery Endpoints").exec(fetchOidcConfig).exec(fetchJwks);

    // Health Check Scenario
    private final ScenarioBuilder healthScenario = scenario("Health Checks").exec(healthCheck);

    // Mixed Workload Scenario
    private final ScenarioBuilder mixedWorkloadScenario =
            scenario("Mixed Workload")
                    .randomSwitch()
                    .on(
                            percent(50.0).then(exec(requestToken, introspectToken)),
                            percent(30.0).then(exec(fetchOidcConfig, fetchJwks)),
                            percent(20.0).then(exec(healthCheck)));

    {
        setUp(
                        // Client credentials flow with ramp-up
                        clientCredentialsScenario
                                .injectOpen(
                                        rampUsers(users).during(Duration.ofSeconds(rampUpSeconds)))
                                .protocols(httpProtocol),

                        // Discovery endpoint load
                        discoveryScenario
                                .injectOpen(
                                        constantUsersPerSec(10)
                                                .during(Duration.ofSeconds(rampUpSeconds)))
                                .protocols(httpProtocol),

                        // Health checks
                        healthScenario
                                .injectOpen(
                                        constantUsersPerSec(5)
                                                .during(Duration.ofSeconds(rampUpSeconds)))
                                .protocols(httpProtocol))
                .assertions(
                        global().responseTime().max().lt(5000),
                        global().successfulRequests().percent().gt(99.0),
                        forAll().failedRequests().percent().lt(1.0));
    }
}
