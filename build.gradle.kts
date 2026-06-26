plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Project-wide properties
extra["compileSdk"] = 35
extra["minSdk"] = 26
extra["targetSdk"] = 35
extra["versionCode"] = 1
extra["versionName"] = "1.0.0"
extra["applicationId"] = "app.netguard.pro"

tasks.register("cleanAll") {
    description = "Clean all modules"
    group = "build"
    subprojects.forEach { subproject ->
        subproject.tasks.findByName("clean")?.let { dependsOn(it) }
    }
}
