pluginManagement {
    repositories {
        google()
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
}

rootProject.name = "NEXUS-IPTV"

include(":app")
include(":core:common")
include(":core:model")
include(":core:security")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:player")
include(":core:stream-engine")
include(":core:ui-tv")
include(":core:ui-mobile")
include(":feature:sources")
include(":feature:livetv")
include(":feature:epg")
include(":feature:vod")
include(":feature:dvr")
include(":feature:multiview")
include(":feature:diagnostics")
include(":feature:settings")
