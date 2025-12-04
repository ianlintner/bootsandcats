rootProject.name = "bootsandcats"

include(
    "server-dao",
    "server-logic",
    "server-ui",
    "canary-app",
    "gatling"
)

project(":server-dao").projectDir = file("server-dao")
project(":server-logic").projectDir = file("server-logic")
project(":server-ui").projectDir = file("server-ui")
project(":canary-app").projectDir = file("canary-app")
project(":gatling").projectDir = file("gatling")
