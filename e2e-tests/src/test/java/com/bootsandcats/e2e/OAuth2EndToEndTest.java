package com.bootsandcats.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class OAuth2EndToEndTest {

    private static TestEnvironment env;
    private static final Path LOG_PATH =
            Paths.get(System.getProperty("java.io.tmpdir"), "oauth2-e2e.log");
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private static final Logger LOGGER = LogManager.getLogger(OAuth2EndToEndTest.class);

    @BeforeAll
    static void setup() {
        String baseUrl = System.getenv("E2E_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            LOGGER.warn("Skipping E2E tests because E2E_BASE_URL is not set");
            Assumptions.assumeTrue(false, "E2E_BASE_URL is not set");
        }
        try {
            Files.deleteIfExists(LOG_PATH);
        } catch (Exception ignored) {
        }
        env = TestEnvironment.fromEnv(baseUrl);
        log("Starting OAuth2 E2E test. baseUrl=%s", env.baseUrl);
    }

    @Test
    @DisplayName("Auth code + PKCE + refresh + userinfo + introspect + revoke")
    void authorizationCodePkceFlow_endToEnd() throws Exception {
        AuthorizationClient client = new AuthorizationClient(env);
        AuthorizationResult result = client.completeAuthorizationCodePkceFlow();

        assertThat(result.accessToken).isNotBlank();
        assertThat(result.refreshToken).isNotBlank();
        assertThat(result.idToken).isNotBlank();

        HttpResult userInfoResponse =
            get(
                env.baseUrl + "/userinfo",
                Map.of("Authorization", "Bearer " + result.accessToken),
                new HashMap<>(),
                false);

        assertThat(userInfoResponse.statusCode()).isEqualTo(200);
        JsonNode userInfoJson = OBJECT_MAPPER.readTree(userInfoResponse.body());
        assertThat(userInfoJson.path("sub").asText()).isNotBlank();

        // Refresh token
        Map<String, List<String>> refreshParams = new HashMap<>();
        refreshParams.put("client_id", List.of(env.confidentialClientId));
        refreshParams.put("client_secret", List.of(env.confidentialClientSecret));
        refreshParams.put("grant_type", List.of("refresh_token"));
        refreshParams.put("refresh_token", List.of(result.refreshToken));

        HttpResult refreshResponse =
            postForm(env.baseUrl + "/oauth2/token", refreshParams, Map.of(), new HashMap<>(), false);

        assertThat(refreshResponse.statusCode()).isEqualTo(200);
        String refreshedAccessToken = OBJECT_MAPPER.readTree(refreshResponse.body()).path("access_token").asText();
        assertThat(refreshedAccessToken).isNotBlank();

        // Introspect should show active
        Map<String, List<String>> introspectParams = new HashMap<>();
        introspectParams.put("client_id", List.of(env.confidentialClientId));
        introspectParams.put("client_secret", List.of(env.confidentialClientSecret));
        introspectParams.put("token", List.of(refreshedAccessToken));
        introspectParams.put("token_type_hint", List.of("access_token"));

        HttpResult introspectResponse =
            postForm(env.baseUrl + "/oauth2/introspect", introspectParams, Map.of(), new HashMap<>(), false);

        assertThat(introspectResponse.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(introspectResponse.body()).path("active").asBoolean()).isTrue();

        // Revoke access token
        Map<String, List<String>> revokeParams = new HashMap<>();
        revokeParams.put("client_id", List.of(env.confidentialClientId));
        revokeParams.put("client_secret", List.of(env.confidentialClientSecret));
        revokeParams.put("token", List.of(refreshedAccessToken));
        revokeParams.put("token_type_hint", List.of("access_token"));

        HttpResult revokeResponse =
            postForm(env.baseUrl + "/oauth2/revoke", revokeParams, Map.of(), new HashMap<>(), false);

        assertThat(revokeResponse.statusCode()).isIn(200, 204);

        HttpResult introspectAfterRevoke =
            postForm(env.baseUrl + "/oauth2/introspect", introspectParams, Map.of(), new HashMap<>(), false);

        assertThat(introspectAfterRevoke.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(introspectAfterRevoke.body()).path("active").asBoolean()).isFalse();
    }

    private static class AuthorizationClient {
        private final TestEnvironment env;

        AuthorizationClient(TestEnvironment env) {
            this.env = env;
        }

        AuthorizationResult completeAuthorizationCodePkceFlow() throws Exception {
            Map<String, String> cookies = new HashMap<>();
            Pkce pkce = Pkce.create();
            String state = UUID.randomUUID().toString();
            Map<String, String> authorizeParams =
                    Map.of(
                            "response_type",
                            "code",
                            "client_id",
                            env.confidentialClientId,
                            "redirect_uri",
                            env.confidentialRedirectUri,
                            "scope",
                            "openid profile email",
                            "state",
                            state,
                            "nonce",
                            UUID.randomUUID().toString(),
                            "code_challenge",
                            pkce.codeChallenge,
                            "code_challenge_method",
                            "S256");

            String authorizePath = "/oauth2/authorize";

            HttpResult loginPage = get(env.baseUrl + "/login", Map.of(), cookies, false);
            log("GET /login -> status=%d cookies=%s", loginPage.statusCode(), cookies);

            String loginUrl = env.baseUrl + "/login";
            ParsedForm loginForm = ParsedForm.parseFirstForm(loginPage.body(), loginUrl);
            log("loginForm action=%s", loginForm.action);
            log("loginForm fields before set=%s", loginForm.fields);
            log("credentials username=%s password=****", env.username);
            loginForm.setSingle("username", env.username);
            loginForm.setSingle("password", env.password);
            log("loginForm fields after set=%s", loginForm.fields);
            log("login cookies=%s", cookies);

            HttpResult loginSubmit = postForm(loginForm.action, loginForm.fields, Map.of(), cookies, false);
            log("POST %s -> status=%d location=%s cookies=%s",
                    loginForm.action,
                    loginSubmit.statusCode(),
                    loginSubmit.location(),
                    cookies);
            if (loginSubmit.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Login failed. status="
                                + loginSubmit.statusCode()
                                + " bodySnippet="
                                + loginSubmit.body().substring(0, Math.min(300, loginSubmit.body().length()))
                                + " headers="
                                + loginSubmit.headers());
            }

            String authorizeUrl = env.baseUrl + authorizePath + "?" + encodeQuery(authorizeParams);
            HttpResult postLoginAuth = get(authorizeUrl, Map.of(), cookies, false);
            log("GET %s -> status=%d location=%s", authorizePath, postLoginAuth.statusCode(), postLoginAuth.location());
            HttpResult authorizationPage = postLoginAuth;
            if (postLoginAuth.statusCode() == 200) {
                String body = postLoginAuth.body();
                try {
                    ParsedForm consentForm = ParsedForm.parseFirstForm(body, env.baseUrl + authorizePath);
                    consentForm.approveAllScopes();
                    log("Consent form action=%s fields=%s", consentForm.action, consentForm.fields);
                    authorizationPage = postForm(consentForm.action, consentForm.fields, Map.of(), cookies, false);
                    log("POST consent -> status=%d location=%s", authorizationPage.statusCode(), authorizationPage.location());
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Authorization endpoint returned HTML instead of redirect and consent auto-submit failed. Body snippet: "
                                    + body.substring(0, Math.min(500, body.length())),
                            e);
                }
            }

            if (postLoginAuth.statusCode() == 302 && !isRedirectWithCode(postLoginAuth)) {
                String redirectLocation = postLoginAuth.location();
                if (redirectLocation == null) {
                    throw new IllegalStateException(
                            "Expected redirect after login but missing Location header. status="
                                    + postLoginAuth.statusCode()
                                    + " bodySnippet="
                                    + postLoginAuth.body().substring(0, Math.min(300, postLoginAuth.body().length()))
                                    + " headers="
                                    + postLoginAuth.headers());
                }
                String consentUrl = resolveLocation(env.baseUrl, redirectLocation);
                authorizationPage = get(consentUrl, Map.of(), cookies, true);
                log("Follow consent %s -> status=%d location=%s", consentUrl, authorizationPage.statusCode(), authorizationPage.location());
            }

            if (isRedirectWithCode(authorizationPage)) {
                return exchangeCodeForTokens(pkce, authorizationPage, state, cookies);
            }

            throw new IllegalStateException(
                    "Expected authorization redirect with code but received status="
                            + authorizationPage.statusCode()
                            + " headers="
                            + authorizationPage.headers()
                            + " bodySnippet="
                            + authorizationPage.body().substring(0, Math.min(300, authorizationPage.body().length())));
        }

        private AuthorizationResult exchangeCodeForTokens(
                Pkce pkce, HttpResult response, String expectedState, Map<String, String> cookies)
                throws Exception {
            log(
                    "exchangeCodeForTokens: status=%d location=%s bodySnippet=%s",
                    response.statusCode(),
                    response.location(),
                    response.body().substring(0, Math.min(500, response.body().length())));
            String location = response.location();
            if (location == null) {
                log(
                        "No Location header after consent submit, attempting follow-up GET to consent form action URL...");
                String fallbackUrl = null;
                try {
                    Document doc = Jsoup.parse(response.body());
                    Element form = doc.selectFirst("form");
                    if (form != null) {
                        if (form.hasAttr("action")) {
                            fallbackUrl = form.absUrl("action");
                        } else {
                            fallbackUrl = env.baseUrl + "/oauth2/authorize";
                        }
                    }
                } catch (Exception e) {
                    // ignore, fallbackUrl will remain null
                }
                log("Consent fallback URL: %s", fallbackUrl);
                if (fallbackUrl == null || fallbackUrl.isBlank()) {
                    throw new IllegalStateException(
                            "Missing redirect Location header after consent submit, and could not determine fallback URL. Response body: "
                                    + response.body()
                                            .substring(
                                                    0,
                                                    Math.min(500, response.body().length())));
                }
                HttpResult followup = get(fallbackUrl, Map.of(), cookies, false);
                location = followup.location();
                if (location == null) {
                    throw new IllegalStateException(
                            "Missing redirect Location header after consent submit and follow-up. Fallback URL: "
                                    + fallbackUrl
                                    + ". Response body: "
                                    + followup.body()
                                            .substring(
                                                    0,
                                                    Math.min(500, followup.body().length())));
                }
            }

            String redirect = resolveLocation(env.baseUrl, location);
            URI uri = new URI(redirect);
            Map<String, String> params = QueryParams.from(uri.getQuery());
            assertThat(params.get("state")).isEqualTo(expectedState);
            String code = params.get("code");
            assertThat(code).isNotBlank();

            Map<String, List<String>> tokenParams = new HashMap<>();
            tokenParams.put("client_id", List.of(env.confidentialClientId));
            tokenParams.put("client_secret", List.of(env.confidentialClientSecret));
            tokenParams.put("grant_type", List.of("authorization_code"));
            tokenParams.put("code", List.of(code));
            tokenParams.put("redirect_uri", List.of(env.confidentialRedirectUri));
            tokenParams.put("code_verifier", List.of(pkce.codeVerifier));

            HttpResult tokenResponse = postForm(env.baseUrl + "/oauth2/token", tokenParams, Map.of(), cookies, false);

            assertThat(tokenResponse.statusCode()).isEqualTo(200);
            log("POST /oauth2/token (code exchange) -> status=%d", tokenResponse.statusCode());

            JsonNode tokenJson = OBJECT_MAPPER.readTree(tokenResponse.body());
            String accessToken = tokenJson.path("access_token").asText();
            String refreshToken = tokenJson.path("refresh_token").asText();
            String idToken = tokenJson.path("id_token").asText();

            return new AuthorizationResult(accessToken, refreshToken, idToken);
        }
    }

    private record AuthorizationResult(String accessToken, String refreshToken, String idToken) {}

    private record HttpResult(int statusCode, String body, Map<String, List<String>> headers) {
        String header(String name) {
            List<String> values = headers.get(name);
            return (values == null || values.isEmpty()) ? null : values.get(0);
        }

        String location() {
            return header("Location");
        }

        static HttpResult from(HttpResponse<String> response) {
            Map<String, List<String>> normalizedHeaders =
                    new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            response.headers()
                    .map()
                    .forEach((key, value) -> normalizedHeaders.put(key, List.copyOf(value)));
            return new HttpResult(response.statusCode(), response.body(), normalizedHeaders);
        }
    }

    private static class QueryParams {
        static Map<String, String> from(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null || query.isBlank()) {
                return result;
            }
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    result.put(decode(kv[0]), decode(kv[1]));
                }
            }
            return result;
        }

        private static String decode(String value) {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String encodeQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encodeForm(Map<String, List<String>> params) {
        return params.entrySet().stream()
                .flatMap(
                        entry ->
                                entry.getValue().stream()
                                        .map(value -> urlEncode(entry.getKey()) + "=" + urlEncode(value)))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static HttpResult get(
            String url, Map<String, String> headers, Map<String, String> cookies, boolean followRedirects)
            throws Exception {
        return send("GET", url, headers, cookies, null, followRedirects);
    }

    private static HttpResult postForm(
            String url,
            Map<String, List<String>> formParams,
            Map<String, String> headers,
            Map<String, String> cookies,
            boolean followRedirects)
            throws Exception {
        Map<String, String> mergedHeaders = new HashMap<>(headers);
        mergedHeaders.put("Content-Type", "application/x-www-form-urlencoded");
        HttpRequest.BodyPublisher body =
                HttpRequest.BodyPublishers.ofString(encodeForm(formParams));
        return send("POST", url, mergedHeaders, cookies, body, followRedirects);
    }

    private static HttpResult send(
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> cookies,
            HttpRequest.BodyPublisher body,
            boolean followRedirects)
            throws Exception {
        Map<String, String> cookieStore = new HashMap<>(cookies);
        HttpResult result = sendOnce(method, URI.create(url), headers, cookieStore, body);

        int hops = 0;
        while (followRedirects && isRedirectStatus(result.statusCode()) && result.location() != null && hops < 5) {
            hops++;
            String nextUrl = resolveLocation(env.baseUrl, result.location());
            result = sendOnce("GET", URI.create(nextUrl), headers, cookieStore, null);
        }

        cookies.clear();
        cookies.putAll(cookieStore);
        return result;
    }

    private static HttpResult sendOnce(
            String method,
            URI uri,
            Map<String, String> headers,
            Map<String, String> cookies,
            HttpRequest.BodyPublisher body)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(uri)
                        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : body);

        if (headers != null) {
            headers.forEach(builder::header);
        }

        if (cookies != null && !cookies.isEmpty()) {
            builder.header("Cookie", cookieHeader(cookies));
        }

        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (cookies != null) {
            mergeCookiesFromResponse(response, cookies);
        }
        return HttpResult.from(response);
    }

    private static void mergeCookiesFromResponse(HttpResponse<String> response, Map<String, String> cookies) {
        List<String> setCookies = response.headers().allValues("Set-Cookie");
        for (String setCookie : setCookies) {
            String[] parts = setCookie.split(";", 2);
            String[] kv = parts[0].split("=", 2);
            if (kv.length == 2) {
                cookies.put(kv[0], kv[1]);
            }
        }
    }

    private static String cookieHeader(Map<String, String> cookies) {
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private static boolean isRedirectStatus(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    private static void log(String message, Object... args) {
        String formatted = args.length == 0 ? message : String.format(message, args);
        String line = "[" + Instant.now() + "] " + formatted + System.lineSeparator();
        LOGGER.debug(formatted);
        try {
            Files.writeString(
                    LOG_PATH,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
        } catch (Exception e) {
            // As a fallback, write to stdout if file logging fails
            System.out.println("[E2E-LOG-FALLBACK] " + line + " error=" + e.getMessage());
        }
    }

    private static class ParsedForm {
        final String action;
        final Map<String, List<String>> fields;

        private ParsedForm(String action, Map<String, List<String>> fields) {
            this.action = action;
            this.fields = fields;
        }

        static ParsedForm parseFirstForm(String html, String baseUrl) {
            Document doc = Jsoup.parse(html, baseUrl);
            Element form = doc.selectFirst("form");
            if (form == null) {
                throw new IllegalStateException("No form found in response");
            }
            Map<String, List<String>> fields = new HashMap<>();
            Elements inputs = form.select("input");
            for (Element input : inputs) {
                String name = input.attr("name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String type = input.attr("type");
                String value = input.attr("value");
                if ("checkbox".equalsIgnoreCase(type)) {
                    if (input.hasAttr("checked") || name.startsWith("scope")) {
                        fields.computeIfAbsent(name, k -> new ArrayList<>())
                                .add(value.isBlank() ? "true" : value);
                    }
                    continue;
                }
                fields.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            }
            String action = form.hasAttr("action") ? form.absUrl("action") : baseUrl;
            return new ParsedForm(action, fields);
        }

        void approveAllScopes() {
            fields.computeIfAbsent("user_oauth_approval", k -> new ArrayList<>()).add("true");
        }

        void setSingle(String key, String value) {
            fields.put(key, new ArrayList<>(List.of(value)));
        }
    }

    private static class Pkce {
        final String codeVerifier;
        final String codeChallenge;

        private Pkce(String codeVerifier, String codeChallenge) {
            this.codeVerifier = codeVerifier;
            this.codeChallenge = codeChallenge;
        }

        static Pkce create() {
            SecureRandom secureRandom = new SecureRandom();
            byte[] codeVerifierBytes = new byte[32];
            secureRandom.nextBytes(codeVerifierBytes);
            String codeVerifier =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
                String codeChallenge =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
                return new Pkce(codeVerifier, codeChallenge);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to generate PKCE challenge", ex);
            }
        }
    }

    private static class TestEnvironment {
        final String baseUrl;
        final String confidentialClientId;
        final String confidentialClientSecret;
        final String confidentialRedirectUri;
        final String username;
        final String password;

        private TestEnvironment(
                String baseUrl,
                String confidentialClientId,
                String confidentialClientSecret,
                String confidentialRedirectUri,
                String username,
                String password) {
            this.baseUrl = baseUrl;
            this.confidentialClientId = confidentialClientId;
            this.confidentialClientSecret = confidentialClientSecret;
            this.confidentialRedirectUri = confidentialRedirectUri;
            this.username = username;
            this.password = password;
        }

        static TestEnvironment fromEnv(String baseUrl) {
            String clientId = getEnv("E2E_CLIENT_ID", "demo-client");
            String clientSecret = getEnv("E2E_CLIENT_SECRET", "demo-secret");
            String redirectUri = getEnv("E2E_REDIRECT_URI", "http://localhost:8080/callback");
            String username = getEnv("E2E_USERNAME", "user");
            String password = getEnv("E2E_PASSWORD", "password");
            return new TestEnvironment(
                    baseUrl, clientId, clientSecret, redirectUri, username, password);
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return value;
        }
    }

    private static boolean isRedirectWithCode(HttpResult response) {
        String location = response.location();
        return location != null && location.contains("code=");
    }

    private static String resolveLocation(String baseUrl, String location) {
        if (location == null) {
            throw new IllegalStateException("Missing redirect Location header");
        }
        if (location.startsWith("http")) {
            return location;
        }
        if (location.startsWith("/")) {
            return baseUrl + location;
        }
        if (location.startsWith("./")) {
            return baseUrl + location.substring(1);
        }
        return baseUrl + "/" + location;
    }
}
