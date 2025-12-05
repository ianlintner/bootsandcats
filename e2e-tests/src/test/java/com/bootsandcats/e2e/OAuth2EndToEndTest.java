package com.bootsandcats.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import io.restassured.filter.session.SessionFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuth2EndToEndTest {

    private static TestEnvironment env;

    @BeforeAll
    static void setup() {
        env = TestEnvironment.fromEnv();
    }

    @Test
    @DisplayName("Auth code + PKCE + refresh + userinfo + introspect + revoke")
    void authorizationCodePkceFlow_endToEnd() throws Exception {
        AuthorizationClient client = new AuthorizationClient(env);
        AuthorizationResult result = client.completeAuthorizationCodePkceFlow();

        assertThat(result.accessToken).isNotBlank();
        assertThat(result.refreshToken).isNotBlank();
        assertThat(result.idToken).isNotBlank();

        Response userInfoResponse =
                RestAssured.given()
                        .baseUri(env.baseUrl)
                        .auth()
                        .oauth2(result.accessToken)
                        .get("/userinfo");

        assertThat(userInfoResponse.statusCode()).isEqualTo(200);
        assertThat(userInfoResponse.jsonPath().getString("sub")).isNotBlank();

        // Refresh token
        Response refreshResponse =
                RestAssured.given()
                        .baseUri(env.baseUrl)
                        .contentType(ContentType.URLENC)
                        .auth()
                        .preemptive()
                        .basic(env.confidentialClientId, env.confidentialClientSecret)
                        .formParam("grant_type", "refresh_token")
                        .formParam("refresh_token", result.refreshToken)
                        .post("/oauth2/token");

        assertThat(refreshResponse.statusCode()).isEqualTo(200);
        String refreshedAccessToken = refreshResponse.jsonPath().getString("access_token");
        assertThat(refreshedAccessToken).isNotBlank();

        // Introspect should show active
        Response introspectResponse =
                RestAssured.given()
                        .baseUri(env.baseUrl)
                        .auth()
                        .preemptive()
                        .basic(env.confidentialClientId, env.confidentialClientSecret)
                        .contentType(ContentType.URLENC)
                        .formParam("token", refreshedAccessToken)
                        .formParam("token_type_hint", "access_token")
                        .post("/oauth2/introspect");

        assertThat(introspectResponse.statusCode()).isEqualTo(200);
        assertThat(introspectResponse.jsonPath().getBoolean("active")).isTrue();

        // Revoke access token
        Response revokeResponse =
                RestAssured.given()
                        .baseUri(env.baseUrl)
                        .auth()
                        .preemptive()
                        .basic(env.confidentialClientId, env.confidentialClientSecret)
                        .contentType(ContentType.URLENC)
                        .formParam("token", refreshedAccessToken)
                        .formParam("token_type_hint", "access_token")
                        .post("/oauth2/revoke");

        assertThat(revokeResponse.statusCode()).isIn(200, 204);

        Response introspectAfterRevoke =
                RestAssured.given()
                        .baseUri(env.baseUrl)
                        .auth()
                        .preemptive()
                        .basic(env.confidentialClientId, env.confidentialClientSecret)
                        .contentType(ContentType.URLENC)
                        .formParam("token", refreshedAccessToken)
                        .formParam("token_type_hint", "access_token")
                        .post("/oauth2/introspect");

        assertThat(introspectAfterRevoke.statusCode()).isEqualTo(200);
        assertThat(introspectAfterRevoke.jsonPath().getBoolean("active")).isFalse();
    }

    private static class AuthorizationClient {
        private final TestEnvironment env;
        private final SessionFilter session = new SessionFilter();

        AuthorizationClient(TestEnvironment env) {
            this.env = env;
        }

        private Optional<String> extractPreferredJSessionId(Response response) {
            List<String> setCookies = response.getHeaders().getValues("Set-Cookie");
            if (setCookies == null) {
                return Optional.empty();
            }
            return setCookies.stream()
                    .filter(value -> value.startsWith("JSESSIONID=") && value.contains("Path=/"))
                    .map(value -> value.split(";", 2)[0])
                    .map(cookie -> cookie.substring("JSESSIONID=".length()))
                    .findFirst();
        }

        AuthorizationResult completeAuthorizationCodePkceFlow() throws Exception {
            Pkce pkce = Pkce.create();
            String state = UUID.randomUUID().toString();
            Map<String, String> authorizeParams =
                Map.of(
                    "response_type", "code",
                    "client_id", env.confidentialClientId,
                    "redirect_uri", env.confidentialRedirectUri,
                    "scope", "openid profile email",
                        "state", state,
                        "nonce", UUID.randomUUID().toString(),
                    "code_challenge", pkce.codeChallenge,
                    "code_challenge_method", "S256");

            String authorizePath = "/oauth2/authorize";

                Response loginPage =
                    RestAssured.given().baseUri(env.baseUrl).filter(session).get("/login");

            Map<String, String> cookies = new HashMap<>(loginPage.getCookies());
            extractPreferredJSessionId(loginPage)
                    .ifPresent((id) -> cookies.put("JSESSIONID", id));

            String loginUrl = env.baseUrl + "/login";
            ParsedForm loginForm = ParsedForm.parseFirstForm(loginPage.asString(), loginUrl);

            System.out.println("loginForm action: " + loginForm.action);
            System.out.println("loginForm fields before set: " + loginForm.fields);
            System.out.println("env.username: " + env.username + ", env.password: " + env.password);
            loginForm.setSingle("username", env.username);
            loginForm.setSingle("password", env.password);
            System.out.println("loginForm fields after set: " + loginForm.fields);
            System.out.println("login cookies: " + cookies);

                var loginRequest =
                    RestAssured.given()
                        .filter(session)
                        .redirects()
                        .follow(false)
                        .contentType(ContentType.URLENC)
                        .cookies(cookies);

                loginForm.fields.forEach(
                    (k, values) -> loginRequest.formParam(k, values.toArray()));

                Response loginSubmit = loginRequest.post(loginForm.action);
                System.out.println("loginSubmit cookies: " + loginSubmit.getCookies());

                cookies.putAll(loginSubmit.getCookies());

                System.out.println(
                    "loginSubmit status="
                        + loginSubmit.statusCode()
                        + " location="
                        + loginSubmit.getHeader("Location"));

                if (loginSubmit.statusCode() >= 400) {
                throw new IllegalStateException(
                    "Login failed. status="
                        + loginSubmit.statusCode()
                        + " bodySnippet="
                        + loginSubmit
                            .asString()
                            .substring(
                                0, Math.min(300, loginSubmit.asString().length()))
                        + " headers="
                        + loginSubmit.getHeaders());
                }

                Response postLoginAuth =
                    RestAssured.given()
                        .baseUri(env.baseUrl)
                        .filter(session)
                        .cookies(cookies)
                        .redirects()
                        .follow(true)
                        .queryParams(authorizeParams)
                        .get(authorizePath);

                cookies.putAll(postLoginAuth.getCookies());

                System.out.println(
                    "authorize status="
                        + postLoginAuth.statusCode()
                        + " location="
                        + postLoginAuth.getHeader("Location"));
                if (postLoginAuth.statusCode() == 200) {
                    System.out.println("authorize 200 body: " + postLoginAuth.asString().substring(0, Math.min(500, postLoginAuth.asString().length())));
                }

                Response authorizationPage = postLoginAuth;
                if (postLoginAuth.statusCode() == 302 && !isRedirectWithCode(postLoginAuth)) {
                    String redirectLocation = postLoginAuth.getHeader("Location");
                    if (redirectLocation == null) {
                    throw new IllegalStateException(
                        "Expected redirect after login but missing Location header. status="
                            + postLoginAuth.statusCode()
                            + " bodySnippet="
                            + postLoginAuth
                                .asString()
                                .substring(
                                    0,
                                    Math.min(
                                        300,
                                        postLoginAuth.asString().length()))
                            + " headers="
                            + postLoginAuth.getHeaders());
                    }

                    String consentUrl = resolveLocation(env.baseUrl, redirectLocation);
                    authorizationPage =
                        RestAssured.given()
                            .filter(session)
                            .cookies(cookies)
                            .redirects()
                            .follow(true)
                            .get(consentUrl);
                }

                cookies.putAll(authorizationPage.getCookies());

                if (isRedirectWithCode(authorizationPage)) {
                    return exchangeCodeForTokens(pkce, authorizationPage, state);
                }

                String authorizationBody = authorizationPage.asString();
                if (!authorizationBody.toLowerCase().contains("<form")) {
                    throw new IllegalStateException(
                            "Expected consent/login form but none found. status="
                                    + authorizationPage.statusCode()
                                    + " bodySnippet="
                                    + authorizationBody.substring(0, Math.min(300, authorizationBody.length()))
                                    + " headers="
                                    + authorizationPage.getHeaders());
                }

                    ParsedForm consentForm =
                        ParsedForm.parseFirstForm(authorizationBody, env.baseUrl + authorizePath);
                    consentForm.approveAllScopes();

                    var consentRequest =
                        RestAssured.given()
                            .filter(session)
                            .cookies(cookies)
                            .redirects()
                            .follow(false)
                            .contentType(ContentType.URLENC);

                    consentForm.fields.forEach(
                        (k, values) -> consentRequest.formParam(k, values.toArray()));

                    Response consentSubmit = consentRequest.post(consentForm.action);

                    cookies.putAll(consentSubmit.getCookies());

            return exchangeCodeForTokens(pkce, consentSubmit, state);
        }

        private AuthorizationResult exchangeCodeForTokens(Pkce pkce, Response response, String expectedState)
                throws URISyntaxException {
            String redirect = resolveLocation(env.baseUrl, response.getHeader("Location"));
            URI uri = new URI(redirect);
            Map<String, String> params = QueryParams.from(uri.getQuery());
            assertThat(params.get("state")).isEqualTo(expectedState);
            String code = params.get("code");
            assertThat(code).isNotBlank();

            Response tokenResponse =
                    RestAssured.given()
                            .baseUri(env.baseUrl)
                            .contentType(ContentType.URLENC)
                            .auth()
                            .preemptive()
                            .basic(env.confidentialClientId, env.confidentialClientSecret)
                            .formParam("grant_type", "authorization_code")
                            .formParam("code", code)
                            .formParam("redirect_uri", env.confidentialRedirectUri)
                            .formParam("code_verifier", pkce.codeVerifier)
                            .post("/oauth2/token");

            assertThat(tokenResponse.statusCode()).isEqualTo(200);

            String accessToken = tokenResponse.jsonPath().getString("access_token");
            String refreshToken = tokenResponse.jsonPath().getString("refresh_token");
            String idToken = tokenResponse.jsonPath().getString("id_token");

            return new AuthorizationResult(accessToken, refreshToken, idToken);
        }
    }

    private record AuthorizationResult(String accessToken, String refreshToken, String idToken) {}

    private static class QueryParams {
        static Map<String, String> from(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null || query.isBlank()) {
                return result;
            }
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    result.put(
                            decode(kv[0]),
                            decode(kv[1]));
                }
            }
            return result;
        }

        private static String decode(String value) {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
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
                        fields
                                .computeIfAbsent(name, k -> new ArrayList<>())
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

        static TestEnvironment fromEnv() {
            String baseUrl = getEnv("E2E_BASE_URL", "http://localhost:9000");
            String clientId = getEnv("E2E_CLIENT_ID", "demo-client");
            String clientSecret = getEnv("E2E_CLIENT_SECRET", "demo-secret");
            String redirectUri = getEnv("E2E_REDIRECT_URI", "http://localhost:8080/callback");
            String username = getEnv("E2E_USERNAME", "user");
            String password = getEnv("E2E_PASSWORD", "password");
            return new TestEnvironment(baseUrl, clientId, clientSecret, redirectUri, username, password);
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return value;
        }
    }

    private static boolean isRedirectWithCode(Response response) {
        String location = response.getHeader("Location");
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
