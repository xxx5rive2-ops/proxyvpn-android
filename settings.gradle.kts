pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/netguard.versions.toml"))
        }
    }
}

rootProject.name = "NetGuardPro"

include(":app")
include(":core:core-common")
include(":core:core-domain")
include(":core:core-data")
include(":core:core-database")
include(":core:core-network")
include(":core:core-security")
include(":core:core-testing")
include(":core:core-ui")
include(":engine:engine-vpn")
include(":engine:engine-proxy")
include(":engine:engine-rules")
include(":engine:engine-dns")
include(":engine:engine-traffic")
include(":features:feature-dashboard")
include(":features:feature-rules")
include(":features:feature-servers")
include(":features:feature-traffic")
include(":features:feature-settings")
include(":features:feature-diagnostics")
