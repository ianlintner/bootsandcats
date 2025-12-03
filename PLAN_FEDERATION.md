# Plan for Federated Login and Canary App

## 1. Dependencies
- Add `spring-boot-starter-oauth2-client` to `pom.xml`.

## 2. Database & User Management
- Create `com.bootsandcats.oauth2.model.User` entity to store user details (username, email, provider, providerId).
- Create `com.bootsandcats.oauth2.repository.UserRepository`.
- Create `com.bootsandcats.oauth2.security.FederatedIdentityAuthenticationSuccessHandler` to save/update users upon login.

## 3. Authorization Server Configuration
- Modify `AuthorizationServerConfig.java`:
    - Update `defaultSecurityFilterChain` to enable `oauth2Login()`.
    - Configure the success handler.
- Update `application.properties` to include OAuth2 client configurations for GitHub, Google, and Azure.

## 4. Canary Application
- Create a new Maven module `canary-app` (or a separate source tree within the repo).
- **Structure:**
    - `src/main/java/com/bootsandcats/canary/CanaryApplication.java`
    - `src/main/java/com/bootsandcats/canary/web/CanaryController.java` (Displays user info)
    - `src/main/resources/application.properties` (Configured to use the Auth Server as provider)
- **Docker:** Create `canary-app/Dockerfile`.
- **K8s:** Create `k8s/canary-deployment.yaml` and `k8s/canary-service.yaml`.

## 5. Deployment
- Update `k8s/deployment.yaml` for `oauth2-server` to inject client IDs/secrets via environment variables.
- Create `k8s/canary-deployment.yaml`.
- Deploy both applications to the cluster.

## 6. Verification
- Access the Canary App.
- Redirect to Auth Server.
- Login via GitHub/Google/Azure.
- Redirect back to Canary App.
- Verify user details are displayed.
