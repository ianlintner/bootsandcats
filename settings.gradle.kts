rootProject.name = "bootsandcats"

// OAuth2 Authorization Server modules
include(
    "oauth2-server:server-model",
    "oauth2-server:server-dao",
    "oauth2-server:server-logic",
    "oauth2-server:server-run",
    "oauth2-server:server-ui",
    "oauth2-server:oauth2-http-client"
)

// Profile Service modules
include(
    "profile-service:server"
)

// E2E Tests
include(
    "e2e-tests"
)

// OAuth2 Server module paths
project(":oauth2-server:server-model").projectDir = file("oauth2-server/server-model")
project(":oauth2-server:server-dao").projectDir = file("oauth2-server/server-dao")
project(":oauth2-server:server-logic").projectDir = file("oauth2-server/server-logic")
project(":oauth2-server:server-run").projectDir = file("oauth2-server/server-run")
project(":oauth2-server:server-ui").projectDir = file("oauth2-server/server-ui")
project(":oauth2-server:oauth2-http-client").projectDir = file("oauth2-server/oauth2-http-client")

// Profile Service module paths
project(":profile-service:server").projectDir = file("profile-service/server")

// E2E Tests path
project(":e2e-tests").projectDir = file("e2e-tests")
