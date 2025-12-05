rootProject.name = "bootsandcats"

include(
    "server-dao",
    "server-logic",
    "server-ui",
    "canary-app",
    "e2e-tests"
)

project(":server-dao").projectDir = file("server-dao")
project(":server-logic").projectDir = file("server-logic")
project(":server-ui").projectDir = file("server-ui")
project(":canary-app").projectDir = file("canary-app")
project(":e2e-tests").projectDir = file("e2e-tests")
