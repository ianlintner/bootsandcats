# OAuth2 Authorization Server Test Enhancement Plan

## Executive Summary

This plan outlines a comprehensive strategy to transform the existing test suite into a behavioral/functional testing framework that:
1. Tests critical OAuth2/OIDC paths (happy and sad)
2. Gates code quality in CI/CD pipelines
3. Provides AI-agent-friendly feedback for automated fixes
4. Supports continuous testing during agent-assisted development

---

## Current State Analysis

### Existing Test Coverage

| Module | Test Type | Coverage | Notes |
|--------|-----------|----------|-------|
| `server-ui` | Unit | ~15 tests | Security, JWT, Config |
| `server-ui` | Integration | ~10 tests | OAuth2 flows, Actuator |
| `e2e-tests` | E2E | 1 comprehensive test | Full auth code + PKCE flow |
| `server-logic` | Unit | 0 tests | Empty |
| `server-dao` | Unit | 0 tests | No test directory |

### Gaps Identified
1. **No sad path/error scenario tests** - Only happy paths tested
2. **No structured behavioral tests** - Tests are technical, not behavior-driven
3. **No contract tests** - No API contract validation
4. **Limited E2E scenarios** - Single test covers one flow
5. **No structured test output** - Logs not AI-parseable
6. **No regression detection** - No baseline comparison
7. **Missing token validation tests** - JWT claims not validated

---

## Proposed Test Architecture

```
tests/
├── unit/                    # Fast, isolated tests (existing)
├── integration/             # Spring context tests (existing + enhanced)
├── contract/                # API contract tests (NEW)
├── behavioral/              # BDD-style scenario tests (NEW)
│   ├── happy/              # Happy path scenarios
│   └── sad/                # Error/edge case scenarios
└── e2e/                     # Full system tests (existing + enhanced)
```

---

## Phase 1: Test Infrastructure (Week 1)

### 1.1 Structured Test Output for AI Agents

Create a custom test reporter that outputs JSON-structured results:

**File: `server-ui/src/test/java/com/bootsandcats/oauth2/testing/AiAgentTestReporter.java`**

```java
/**
 * Custom JUnit 5 extension that generates AI-parseable test reports.
 * Output format is optimized for AI agents to understand failures and suggest fixes.
 */
@ExtendWith(AiAgentTestReporter.class)
```

**Report Format:**
```json
{
  "testId": "OAuth2AuthorizationCodeFlow_ValidPKCE_ShouldIssueTokens",
  "category": "OAUTH2_AUTHORIZATION",
  "scenario": "HAPPY_PATH",
  "status": "FAILED",
  "duration_ms": 1234,
  "failure": {
    "type": "ASSERTION_ERROR",
    "expected": "200 OK with access_token",
    "actual": "401 Unauthorized",
    "location": {
      "file": "OAuth2FlowTest.java",
      "line": 145,
      "method": "shouldIssueTokensWithValidPKCE"
    }
  },
  "context": {
    "endpoint": "/oauth2/token",
    "request": {
      "grant_type": "authorization_code",
      "code_verifier": "***REDACTED***"
    },
    "response": {
      "status": 401,
      "body": "{\"error\":\"invalid_client\"}"
    }
  },
  "suggested_fix": {
    "type": "CHECK_CLIENT_CREDENTIALS",
    "files_to_review": [
      "AuthorizationServerConfig.java",
      "RegisteredClientRepository"
    ],
    "common_causes": [
      "Client secret mismatch",
      "Client not registered",
      "Wrong authentication method"
    ]
  },
  "regression_info": {
    "last_passed": "2024-12-07T10:30:00Z",
    "commits_since_pass": ["abc123", "def456"]
  }
}
```

### 1.2 Test Categories & Tags

Add standardized test tags for filtering and reporting:

```java
@Tag("oauth2")
@Tag("happy-path")
@Tag("critical")
@Tag("authorization-code")
public class AuthorizationCodeFlowTest {}

@Tag("oauth2")
@Tag("sad-path")
@Tag("security")
@Tag("input-validation")
public class OAuth2ErrorHandlingTest {}
```

**Tag Taxonomy:**
- Flow: `authorization-code`, `client-credentials`, `refresh-token`, `pkce`
- Path: `happy-path`, `sad-path`, `edge-case`
- Priority: `critical`, `important`, `nice-to-have`
- Category: `security`, `compliance`, `performance`, `integration`

### 1.3 Test Data Builders

Create fluent builders for test data:

```java
public class TestClientBuilder {
    public static RegisteredClient.Builder publicClient() { ... }
    public static RegisteredClient.Builder confidentialClient() { ... }
    public static RegisteredClient.Builder m2mClient() { ... }
}

public class TestTokenRequestBuilder {
    public static AuthorizationCodeRequest.Builder validAuthCodeRequest() { ... }
    public static AuthorizationCodeRequest.Builder missingCodeVerifier() { ... }
    public static AuthorizationCodeRequest.Builder expiredCode() { ... }
}
```

---

## Phase 2: Behavioral Test Scenarios (Week 2)

### 2.1 Authorization Code Flow (with PKCE)

#### Happy Paths

| Test ID | Scenario | Expected Outcome |
|---------|----------|------------------|
| `AC-HP-001` | Valid authorization code exchange with PKCE | Access token, refresh token, ID token issued |
| `AC-HP-002` | Authorization with all scopes approved | Token contains all requested scopes |
| `AC-HP-003` | Authorization with partial consent | Token contains only approved scopes |
| `AC-HP-004` | Refresh token exchange | New access token issued |
| `AC-HP-005` | ID token contains required OIDC claims | `sub`, `iss`, `aud`, `exp`, `iat`, `nonce` present |

#### Sad Paths

| Test ID | Scenario | Expected Error |
|---------|----------|----------------|
| `AC-SP-001` | Missing code_verifier | `invalid_grant` error |
| `AC-SP-002` | Invalid code_verifier (SHA256 mismatch) | `invalid_grant` error |
| `AC-SP-003` | Expired authorization code | `invalid_grant` error |
| `AC-SP-004` | Authorization code replay attack | `invalid_grant` error |
| `AC-SP-005` | Invalid redirect_uri | `invalid_request` error |
| `AC-SP-006` | Invalid client_id | `invalid_client` error |
| `AC-SP-007` | Wrong client_secret | `invalid_client` error |
| `AC-SP-008` | Mismatched state parameter | Redirect with `state` mismatch |
| `AC-SP-009` | Invalid scope requested | `invalid_scope` error |
| `AC-SP-010` | Code used for wrong client | `invalid_grant` error |

### 2.2 Client Credentials Flow

#### Happy Paths

| Test ID | Scenario | Expected Outcome |
|---------|----------|------------------|
| `CC-HP-001` | Valid client credentials | Access token issued |
| `CC-HP-002` | Token with requested scopes | Token contains requested scopes |
| `CC-HP-003` | Token introspection returns active | `active: true` with correct claims |

#### Sad Paths

| Test ID | Scenario | Expected Error |
|---------|----------|----------------|
| `CC-SP-001` | Invalid client credentials | `401 Unauthorized` |
| `CC-SP-002` | Client not authorized for grant type | `unauthorized_client` error |
| `CC-SP-003` | Scope not allowed for client | `invalid_scope` error |
| `CC-SP-004` | Missing grant_type parameter | `invalid_request` error |

### 2.3 Token Operations

#### Happy Paths

| Test ID | Scenario | Expected Outcome |
|---------|----------|------------------|
| `TK-HP-001` | Token introspection (valid token) | `active: true` with metadata |
| `TK-HP-002` | Token revocation | `200 OK`, subsequent introspect shows `active: false` |
| `TK-HP-003` | JWKS endpoint returns valid keys | EC P-256 keys with `kid`, `use: sig` |

#### Sad Paths

| Test ID | Scenario | Expected Error |
|---------|----------|----------------|
| `TK-SP-001` | Introspect expired token | `active: false` |
| `TK-SP-002` | Introspect revoked token | `active: false` |
| `TK-SP-003` | Introspect malformed token | `active: false` |
| `TK-SP-004` | Revoke with invalid client auth | `401 Unauthorized` |

### 2.4 OIDC Discovery

#### Happy Paths

| Test ID | Scenario | Expected Outcome |
|---------|----------|------------------|
| `OD-HP-001` | Discovery document available | All required fields present |
| `OD-HP-002` | JWKS URI accessible | Valid JWKS returned |
| `OD-HP-003` | UserInfo with valid token | User claims returned |

#### Sad Paths

| Test ID | Scenario | Expected Error |
|---------|----------|----------------|
| `OD-SP-001` | UserInfo without token | `401 Unauthorized` |
| `OD-SP-002` | UserInfo with expired token | `401 Unauthorized` |
| `OD-SP-003` | UserInfo with wrong scope | `403 Forbidden` or missing claims |

### 2.5 Security Tests

| Test ID | Scenario | Expected Outcome |
|---------|----------|------------------|
| `SEC-001` | CSRF protection on token endpoint | CSRF exempted (per OAuth spec) |
| `SEC-002` | Security headers present | CSP, X-Frame-Options, etc. |
| `SEC-003` | HTTPS redirect in prod profile | 301/308 redirect |
| `SEC-004` | Rate limiting (if configured) | 429 after threshold |
| `SEC-005` | Token signature validation | ES256 signature verifies |

---

## Phase 3: Implementation Files (Week 2-3)

### 3.1 New Test Classes to Create

```
server-ui/src/test/java/com/bootsandcats/oauth2/
├── testing/
│   ├── AiAgentTestReporter.java          # JSON test reporter
│   ├── TestResultCollector.java          # Aggregates results
│   ├── OAuth2TestContext.java            # Test context holder
│   └── assertions/
│       ├── TokenAssertions.java          # JWT assertion helpers
│       └── OAuth2ResponseAssertions.java # HTTP response assertions
├── builders/
│   ├── TestClientBuilder.java            # Client registration builder
│   ├── TokenRequestBuilder.java          # Token request builder
│   └── AuthorizationRequestBuilder.java  # Auth request builder
├── behavioral/
│   ├── happy/
│   │   ├── AuthorizationCodeHappyPathTest.java
│   │   ├── ClientCredentialsHappyPathTest.java
│   │   ├── TokenOperationsHappyPathTest.java
│   │   └── OidcDiscoveryHappyPathTest.java
│   └── sad/
│       ├── AuthorizationCodeSadPathTest.java
│       ├── ClientCredentialsSadPathTest.java
│       ├── TokenOperationsSadPathTest.java
│       └── InputValidationTest.java
├── contract/
│   ├── OidcDiscoveryContractTest.java    # Validates OIDC spec compliance
│   ├── OAuth2TokenContractTest.java      # Validates OAuth2 spec compliance
│   └── JwksContractTest.java             # Validates JWKS format
└── security/
    ├── JwtClaimsValidationTest.java      # JWT claim validation
    ├── TokenExpirationTest.java          # Token lifecycle tests
    └── SecurityHeadersTest.java          # HTTP security headers
```

### 3.2 E2E Test Enhancements

```
e2e-tests/src/test/java/com/bootsandcats/e2e/
├── scenarios/
│   ├── AuthorizationCodeE2ETest.java     # Multiple auth code scenarios
│   ├── RefreshTokenE2ETest.java          # Refresh flow scenarios
│   ├── TokenRevocationE2ETest.java       # Revocation scenarios
│   └── MultiClientE2ETest.java           # Multi-client interactions
├── fixtures/
│   ├── TestUser.java                     # Test user credentials
│   ├── TestClients.java                  # Pre-configured test clients
│   └── TestScenarioData.java             # Scenario-specific data
└── reporters/
    └── E2ETestReporter.java              # E2E-specific reporter
```

---

## Phase 4: CI/CD Integration (Week 3)

### 4.1 GitHub Actions Workflow Enhancement

**File: `.github/workflows/ci.yml` (additions)**

```yaml
  behavioral-tests:
    name: Behavioral Tests (Happy + Sad Paths)
    runs-on: ubuntu-latest
    needs: build-unit
    steps:
      - name: Run behavioral tests
        run: |
          gradle test \
            --tests "com.bootsandcats.oauth2.behavioral.*" \
            -Dtest.output.format=json \
            -Dtest.output.dir=build/test-results/behavioral

      - name: Generate AI-readable report
        if: always()
        run: |
          scripts/generate-ai-test-report.sh build/test-results/behavioral

      - name: Upload behavioral test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: behavioral-test-report
          path: build/test-results/behavioral/ai-report.json

  contract-tests:
    name: Contract Tests (OAuth2/OIDC Compliance)
    runs-on: ubuntu-latest
    needs: build-unit
    steps:
      - name: Run contract tests
        run: |
          gradle test --tests "com.bootsandcats.oauth2.contract.*"

  gate-check:
    name: Quality Gate
    runs-on: ubuntu-latest
    needs: [behavioral-tests, contract-tests, static-analysis]
    if: always()
    steps:
      - name: Download all test reports
        uses: actions/download-artifact@v4

      - name: Evaluate quality gate
        run: |
          scripts/evaluate-quality-gate.sh
          # Outputs structured feedback for AI agents

      - name: Comment PR with results
        if: github.event_name == 'pull_request'
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const report = JSON.parse(fs.readFileSync('quality-gate-report.json'));
            // Format for AI agent consumption
```

### 4.2 Quality Gate Script

**File: `scripts/evaluate-quality-gate.sh`**

```bash
#!/bin/bash
# Evaluates test results and generates AI-agent-friendly feedback

PASS_THRESHOLD=95  # Minimum pass rate
CRITICAL_MUST_PASS=true

# Parse test results
# Generate structured JSON output
# Exit with appropriate code
```

---

## Phase 5: AI Agent Integration (Week 4)

### 5.1 Test Result Format for AI Agents

The test output should be structured to help AI agents:
1. **Understand what failed** - Clear, contextual error messages
2. **Know where to look** - File paths, line numbers, related configs
3. **Suggest fixes** - Common causes and remediation steps
4. **Track regressions** - When tests last passed, what changed

### 5.2 Continuous Testing During Development

**File: `scripts/watch-tests.sh`**

```bash
#!/bin/bash
# Runs relevant tests when files change
# Outputs AI-parseable results in real-time

gradle test --continuous \
  -Dtest.output.format=json \
  -Dtest.output.stream=true
```

### 5.3 AI Agent Feedback Template

```json
{
  "summary": {
    "total": 45,
    "passed": 43,
    "failed": 2,
    "skipped": 0,
    "pass_rate": 95.6
  },
  "critical_failures": [
    {
      "test_id": "AC-SP-001",
      "name": "shouldRejectMissingCodeVerifier",
      "impact": "HIGH - PKCE enforcement broken",
      "diagnosis": {
        "symptom": "Token issued without code_verifier",
        "root_cause": "PKCE validation disabled",
        "evidence": "200 OK when 400 expected"
      },
      "fix_guidance": {
        "files": ["AuthorizationServerConfig.java"],
        "action": "Ensure requireProofKey() is called for public clients",
        "code_hint": "RegisteredClient.builder().clientSettings(ClientSettings.builder().requireProofKey(true).build())"
      }
    }
  ],
  "regression_alert": {
    "is_regression": true,
    "broken_since": "commit abc123",
    "previously_passing": true,
    "suspect_changes": [
      "server-ui/src/main/java/com/.../AuthorizationServerConfig.java"
    ]
  }
}
```

---

## Phase 6: Test Maintenance & Documentation (Ongoing)

### 6.1 Test Documentation

Each behavioral test should include:

```java
/**
 * Test ID: AC-SP-001
 * Category: Authorization Code Flow - Sad Path
 * Priority: CRITICAL
 * 
 * Scenario: Client attempts token exchange without PKCE code_verifier
 * 
 * Given: A valid authorization code obtained with PKCE challenge
 * When: Token request is made without code_verifier parameter
 * Then: Server returns 400 Bad Request with error "invalid_grant"
 * 
 * Security Requirement: RFC 7636 PKCE enforcement
 * Compliance: OAuth 2.1 Section 4.1.3
 * 
 * Common Causes of Failure:
 * - PKCE validation disabled in config
 * - Public client not configured to require proof key
 * - Code verifier validation logic bypassed
 */
@Test
@Tag("sad-path")
@Tag("critical")
@Tag("pkce")
void shouldRejectMissingCodeVerifier() { ... }
```

### 6.2 Test Coverage Matrix

Maintain a coverage matrix mapping:
- OAuth2/OIDC spec sections → Test IDs
- Security requirements → Test IDs
- User stories → Test IDs

---

## Implementation Priority

### Must Have (Week 1-2)
1. ✅ AI-agent-friendly test reporter
2. ✅ Authorization code happy/sad path tests
3. ✅ Client credentials happy/sad path tests
4. ✅ Token operations tests
5. ✅ OIDC discovery contract tests

### Should Have (Week 3)
1. ⬜ CI/CD quality gate integration
2. ⬜ Regression detection
3. ⬜ Security header tests
4. ⬜ JWT claims validation tests

### Nice to Have (Week 4+)
1. ⬜ Continuous testing during development
2. ⬜ Performance baseline tests
3. ⬜ Multi-client interaction tests
4. ⬜ Federation flow tests

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Test Coverage | >80% line coverage | JaCoCo report |
| Critical Path Coverage | 100% | Manual review |
| Sad Path Coverage | >20 scenarios | Test count |
| Test Execution Time | <5 min for integration | CI timing |
| AI Agent Success Rate | >90% auto-fix | Track fixes |
| Regression Detection | <1 day to catch | Time-to-detect |

---

## Next Steps

1. **Review and approve this plan**
2. **Create test infrastructure** (Phase 1)
3. **Implement behavioral tests** (Phase 2)
4. **Integrate with CI/CD** (Phase 4)
5. **Document and maintain** (Ongoing)

---

## Appendix A: OAuth2/OIDC Spec References

- RFC 6749: OAuth 2.0 Authorization Framework
- RFC 6750: Bearer Token Usage
- RFC 7636: PKCE
- RFC 7662: Token Introspection
- RFC 7009: Token Revocation
- OpenID Connect Core 1.0
- OAuth 2.1 Draft Specification

## Appendix B: Test Client Configuration

| Client ID | Type | Grants | PKCE | Scopes |
|-----------|------|--------|------|--------|
| `demo-client` | Confidential | auth_code, refresh, client_credentials | Optional | openid, profile, email, read |
| `m2m-client` | Confidential | client_credentials | N/A | api:read, api:write |
| `public-client` | Public | auth_code | Required | openid, profile |

## Appendix C: Error Code Reference

| Error | HTTP Status | Scenario |
|-------|-------------|----------|
| `invalid_request` | 400 | Missing/invalid parameters |
| `invalid_client` | 401 | Client authentication failed |
| `invalid_grant` | 400 | Invalid code, token, or credentials |
| `unauthorized_client` | 400 | Client not authorized for grant |
| `invalid_scope` | 400 | Invalid or unauthorized scope |
| `invalid_token` | 401 | Token expired, revoked, or malformed |

---

## Implementation Status (Updated)

### ✅ Completed Components

#### Test Infrastructure
- [x] `AiAgentTestReporter.java` - AI-friendly JSON test reporting
- [x] `TokenAssertions.java` - Fluent JWT/token assertions
- [x] `OAuth2ResponseAssertions.java` - Response validation assertions

#### Behavioral Tests - Happy Path
- [x] `ClientCredentialsHappyPathTest.java` (5 tests)
- [x] `TokenOperationsHappyPathTest.java` (5 tests)

#### Behavioral Tests - Sad Path  
- [x] `ClientCredentialsSadPathTest.java` (9 tests)
- [x] `TokenOperationsSadPathTest.java` (6 tests)

#### Contract Tests
- [x] `OidcDiscoveryContractTest.java` (10 OIDC compliance tests)

#### Security Regression Tests
- [x] `SecurityRegressionTest.java` (15 security tests)
  - Security headers (X-Content-Type-Options, X-Frame-Options, Cache-Control)
  - Authentication requirements (introspection, revocation, userinfo)
  - Credential validation
  - Sensitive data protection
  - Request validation
  - Public endpoint access

#### Gradle Tasks
- [x] `./gradlew behavioralTests` - All happy/sad path tests
- [x] `./gradlew happyPathTests` - Happy paths only
- [x] `./gradlew sadPathTests` - Sad paths only
- [x] `./gradlew contractTests` - API compliance
- [x] `./gradlew securityTests` - Security regression
- [x] `./gradlew criticalTests` - All critical tests
- [x] `./gradlew oauth2Tests` - All OAuth2 tests
- [x] `./gradlew fastTests` - Fast unit tests

### 📋 Remaining Work

- [ ] Authorization code flow happy/sad path tests
- [ ] PKCE flow tests
- [ ] Refresh token flow tests
- [ ] E2E test enhancement with AI reporting
- [ ] CI/CD integration updates
- [ ] Test documentation
