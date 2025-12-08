rootProject.name = "bootsandcats"

include(
    "server-model",
    "server-dao",
    "server-logic",
    "server-run",
    "server-ui",
    "profile-ui",
    "oauth2-http-client",
    "e2e-tests"
)

project(":server-model").projectDir = file("server-model")
project(":server-dao").projectDir = file("server-dao")
project(":server-logic").projectDir = file("server-logic")
project(":server-run").projectDir = file("server-run")
project(":server-ui").projectDir = file("server-ui")
project(":profile-ui").projectDir = file("profile-ui")
project(":oauth2-http-client").projectDir = file("oauth2-http-client")
project(":e2e-tests").projectDir = file("e2e-tests")
