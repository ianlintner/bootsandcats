package com.bootsandcats.oauth2.testing;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JUnit 5 extension that generates AI-agent-parseable test reports.
 *
 * <p>This reporter outputs structured JSON that helps AI agents understand test failures, diagnose
 * root causes, and suggest fixes. It captures context about what was tested, what failed, and
 * provides guidance for remediation.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * @ExtendWith(AiAgentTestReporter.class)
 * class MyTest {
 *     @Test
 *     @Tag("happy-path")
 *     @Tag("critical")
 *     void myTest() { ... }
 * }
 * }</pre>
 *
 * <h2>Output Format</h2>
 *
 * The reporter generates JSON files in the directory specified by {@code test.output.dir} system
 * property (defaults to {@code build/test-results/ai-reports}).
 *
 * @see AiTestResult for the structure of individual test results
 */
public class AiAgentTestReporter
        implements BeforeAllCallback,
                AfterAllCallback,
                BeforeTestExecutionCallback,
                AfterTestExecutionCallback {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String OUTPUT_DIR_PROPERTY = "test.output.dir";
    private static final String DEFAULT_OUTPUT_DIR = "build/test-results/ai-reports";

    private static final Map<String, List<AiTestResult>> TEST_RESULTS = new ConcurrentHashMap<>();
    private static final Map<String, Long> TEST_START_TIMES = new ConcurrentHashMap<>();

    /** Holds contextual information set by tests for richer failure reports. */
    private static final ThreadLocal<TestContext> CURRENT_CONTEXT = new ThreadLocal<>();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String className = context.getRequiredTestClass().getName();
        TEST_RESULTS.computeIfAbsent(className, k -> new ArrayList<>());
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        String testId = getTestId(context);
        TEST_START_TIMES.put(testId, System.currentTimeMillis());
        CURRENT_CONTEXT.set(new TestContext());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        String testId = getTestId(context);
        long startTime = TEST_START_TIMES.getOrDefault(testId, System.currentTimeMillis());
        long duration = System.currentTimeMillis() - startTime;

        AiTestResult result = buildTestResult(context, duration);

        String className = context.getRequiredTestClass().getName();
        TEST_RESULTS.computeIfAbsent(className, k -> new ArrayList<>()).add(result);

        CURRENT_CONTEXT.remove();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        String className = context.getRequiredTestClass().getName();
        List<AiTestResult> results = TEST_RESULTS.get(className);

        if (results != null && !results.isEmpty()) {
            writeReport(className, results);
        }
    }

    /**
     * Sets request context for the current test. Call this in your test to provide context about
     * what HTTP request was made.
     *
     * @param endpoint the endpoint being tested
     * @param method the HTTP method
     * @param params request parameters (sensitive values will be redacted)
     */
    public static void setRequestContext(
            String endpoint, String method, Map<String, String> params) {
        TestContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) {
            ctx.endpoint = endpoint;
            ctx.httpMethod = method;
            ctx.requestParams = redactSensitive(params);
        }
    }

    /**
     * Sets response context for the current test. Call this in your test to provide context about
     * what HTTP response was received.
     *
     * @param status the HTTP status code
     * @param body the response body
     */
    public static void setResponseContext(int status, String body) {
        TestContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) {
            ctx.responseStatus = status;
            ctx.responseBody = truncate(body, 1000);
        }
    }

    /**
     * Sets expected outcome for the current test. Call this to help AI agents understand what was
     * expected.
     *
     * @param expected description of expected outcome
     */
    public static void setExpectedOutcome(String expected) {
        TestContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) {
            ctx.expectedOutcome = expected;
        }
    }

    /**
     * Marks the current test as a regression test. Regression tests should not be modified to pass
     * - the underlying issue should be fixed instead.
     *
     * @param isRegression true if this is a regression test
     */
    public static void setRegressionIndicator(boolean isRegression) {
        TestContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) {
            ctx.isRegressionTest = isRegression;
        }
    }

    private AiTestResult buildTestResult(ExtensionContext context, long duration) {
        AiTestResult result = new AiTestResult();
        result.testId = getTestId(context);
        result.testName = context.getDisplayName();
        result.className = context.getRequiredTestClass().getSimpleName();
        result.methodName = context.getRequiredTestMethod().getName();
        result.durationMs = duration;
        result.timestamp = Instant.now().toString();
        result.tags = extractTags(context);
        result.category = determineCategory(result.tags);
        result.scenario = determineScenario(result.tags);

        Optional<Throwable> failure = context.getExecutionException();
        if (failure.isPresent()) {
            result.status = "FAILED";
            result.failure = buildFailureInfo(failure.get(), context);
        } else {
            result.status = "PASSED";
        }

        TestContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) {
            result.context = buildContextInfo(ctx);
        }

        return result;
    }

    private FailureInfo buildFailureInfo(Throwable throwable, ExtensionContext context) {
        FailureInfo info = new FailureInfo();
        info.type = throwable.getClass().getSimpleName();
        info.message = throwable.getMessage();

        // Extract location from stack trace
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().equals(context.getRequiredTestClass().getName())) {
                info.file = element.getFileName();
                info.line = element.getLineNumber();
                info.method = element.getMethodName();
                break;
            }
        }

        // Add suggested fixes based on failure type and context
        info.suggestedFix = suggestFix(throwable, context);

        return info;
    }

    private SuggestedFix suggestFix(Throwable throwable, ExtensionContext context) {
        SuggestedFix fix = new SuggestedFix();
        String message = throwable.getMessage() != null ? throwable.getMessage().toLowerCase() : "";
        List<String> tags = extractTags(context);

        // Analyze failure and suggest fixes
        if (message.contains("401") || message.contains("unauthorized")) {
            fix.type = "CHECK_AUTHENTICATION";
            fix.filesToReview =
                    List.of(
                            "AuthorizationServerConfig.java",
                            "SecurityFilterChainConfig.java",
                            "TestOAuth2ClientConfiguration.java");
            fix.commonCauses =
                    List.of(
                            "Client credentials mismatch",
                            "Client not registered in test configuration",
                            "Wrong authentication method (e.g., client_secret_basic vs client_secret_post)");
        } else if (message.contains("invalid_grant")) {
            fix.type = "CHECK_GRANT_VALIDATION";
            fix.filesToReview =
                    List.of(
                            "AuthorizationServerConfig.java",
                            "RegisteredClientRepository implementation");
            fix.commonCauses =
                    List.of(
                            "Authorization code expired or already used",
                            "PKCE code_verifier mismatch",
                            "Redirect URI mismatch",
                            "Wrong client for the authorization code");
        } else if (message.contains("invalid_scope")) {
            fix.type = "CHECK_SCOPE_CONFIGURATION";
            fix.filesToReview =
                    List.of("RegisteredClient configuration", "TestOAuth2ClientConfiguration.java");
            fix.commonCauses =
                    List.of(
                            "Scope not configured for client",
                            "Scope string format mismatch",
                            "Case sensitivity in scope names");
        } else if (tags.contains("pkce")) {
            fix.type = "CHECK_PKCE_CONFIGURATION";
            fix.filesToReview =
                    List.of("AuthorizationServerConfig.java", "RegisteredClient.clientSettings()");
            fix.commonCauses =
                    List.of(
                            "PKCE not required for public clients",
                            "Code challenge method not supported",
                            "Code verifier validation logic error");
        } else {
            fix.type = "GENERAL_INVESTIGATION";
            fix.filesToReview = List.of(context.getRequiredTestClass().getSimpleName() + ".java");
            fix.commonCauses =
                    List.of("Review test setup and assertions", "Check application logs");
        }

        return fix;
    }

    private Map<String, Object> buildContextInfo(TestContext ctx) {
        Map<String, Object> info = new HashMap<>();

        if (ctx.endpoint != null) {
            info.put("endpoint", ctx.endpoint);
        }
        if (ctx.httpMethod != null) {
            info.put("method", ctx.httpMethod);
        }
        if (ctx.requestParams != null) {
            info.put("request_params", ctx.requestParams);
        }
        if (ctx.responseStatus != null) {
            info.put("response_status", ctx.responseStatus);
        }
        if (ctx.responseBody != null) {
            info.put("response_body", ctx.responseBody);
        }
        if (ctx.expectedOutcome != null) {
            info.put("expected_outcome", ctx.expectedOutcome);
        }

        return info;
    }

    private List<String> extractTags(ExtensionContext context) {
        List<String> tags = new ArrayList<>();
        Method method = context.getRequiredTestMethod();

        for (Tag tag : method.getAnnotationsByType(Tag.class)) {
            tags.add(tag.value());
        }

        // Also check class-level tags
        for (Tag tag : context.getRequiredTestClass().getAnnotationsByType(Tag.class)) {
            if (!tags.contains(tag.value())) {
                tags.add(tag.value());
            }
        }

        return tags;
    }

    private String determineCategory(List<String> tags) {
        if (tags.contains("authorization-code")) return "AUTHORIZATION_CODE_FLOW";
        if (tags.contains("client-credentials")) return "CLIENT_CREDENTIALS_FLOW";
        if (tags.contains("refresh-token")) return "REFRESH_TOKEN_FLOW";
        if (tags.contains("token-operations")) return "TOKEN_OPERATIONS";
        if (tags.contains("oidc")) return "OIDC_DISCOVERY";
        if (tags.contains("security")) return "SECURITY";
        return "GENERAL";
    }

    private String determineScenario(List<String> tags) {
        if (tags.contains("happy-path")) return "HAPPY_PATH";
        if (tags.contains("sad-path")) return "SAD_PATH";
        if (tags.contains("edge-case")) return "EDGE_CASE";
        return "UNKNOWN";
    }

    private String getTestId(ExtensionContext context) {
        return context.getRequiredTestClass().getSimpleName()
                + "_"
                + context.getRequiredTestMethod().getName();
    }

    private void writeReport(String className, List<AiTestResult> results) throws IOException {
        String outputDir = System.getProperty(OUTPUT_DIR_PROPERTY, DEFAULT_OUTPUT_DIR);
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        ObjectNode report = OBJECT_MAPPER.createObjectNode();
        report.put("class", className);
        report.put("timestamp", Instant.now().toString());
        report.put("total", results.size());
        report.put("passed", results.stream().filter(r -> "PASSED".equals(r.status)).count());
        report.put("failed", results.stream().filter(r -> "FAILED".equals(r.status)).count());

        ArrayNode testsNode = report.putArray("tests");
        for (AiTestResult result : results) {
            testsNode.addPOJO(result);
        }

        // Add summary for AI agents
        ObjectNode summary = report.putObject("ai_summary");
        List<AiTestResult> failures =
                results.stream().filter(r -> "FAILED".equals(r.status)).toList();
        if (!failures.isEmpty()) {
            ArrayNode criticalFailures = summary.putArray("critical_failures");
            for (AiTestResult failure : failures) {
                ObjectNode f = criticalFailures.addObject();
                f.put("test_id", failure.testId);
                f.put("category", failure.category);
                f.put("scenario", failure.scenario);
                if (failure.failure != null) {
                    f.put("error_type", failure.failure.type);
                    f.put("error_message", failure.failure.message);
                    if (failure.failure.suggestedFix != null) {
                        f.put("fix_type", failure.failure.suggestedFix.type);
                    }
                }
            }
        }

        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        Path reportFile = outputPath.resolve(simpleClassName + "-ai-report.json");
        try (FileWriter writer = new FileWriter(reportFile.toFile())) {
            OBJECT_MAPPER.writeValue(writer, report);
        }
    }

    private static Map<String, String> redactSensitive(Map<String, String> params) {
        if (params == null) return null;
        Map<String, String> redacted = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("secret")
                    || key.contains("password")
                    || key.contains("token")
                    || key.contains("code")
                    || key.contains("verifier")) {
                redacted.put(entry.getKey(), "***REDACTED***");
            } else {
                redacted.put(entry.getKey(), entry.getValue());
            }
        }
        return redacted;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...[truncated]";
    }

    /** Internal test context holder. */
    private static class TestContext {
        String endpoint;
        String httpMethod;
        Map<String, String> requestParams;
        Integer responseStatus;
        String responseBody;
        String expectedOutcome;
        boolean isRegressionTest;
    }

    /** Test result structure for AI consumption. */
    public static class AiTestResult {
        public String testId;
        public String testName;
        public String className;
        public String methodName;
        public String status;
        public long durationMs;
        public String timestamp;
        public List<String> tags;
        public String category;
        public String scenario;
        public FailureInfo failure;
        public Map<String, Object> context;
    }

    /** Failure information structure. */
    public static class FailureInfo {
        public String type;
        public String message;
        public String file;
        public int line;
        public String method;
        public SuggestedFix suggestedFix;
    }

    /** Suggested fix information. */
    public static class SuggestedFix {
        public String type;
        public List<String> filesToReview;
        public List<String> commonCauses;
    }
}
