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
        gradlePluginPortal()
    }
}

rootProject.name = "Manhwaread"

// JVM-модули: собираются и тестируются без Android SDK
include(":core:model")
include(":core:common")
include(":core:network")
include(":core:translation-api")
include(":core:vision-model")
include(":core:pipeline")
include(":source:api")
include(":source:mangadex")
include(":source:madara")
include(":source:asura")

// Android-модули
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":feature:library")
include(":feature:browse")
include(":feature:details")
include(":feature:reader")
include(":feature:history")
include(":feature:downloads")
include(":feature:settings")
include(":feature:onboarding")
include(":app")
