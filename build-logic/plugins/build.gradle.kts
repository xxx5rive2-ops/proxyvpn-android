plugins {
    `kotlin-dsl`
}

group = "app.netguard.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.plugins.android.application.toDep())
    compileOnly(libs.plugins.android.library.toDep())
    compileOnly(libs.plugins.kotlin.android.toDep())
    compileOnly(libs.plugins.kotlin.jvm.toDep())
    compileOnly(libs.plugins.kotlin.compose.toDep())
    compileOnly(libs.plugins.ksp.toDep())
    compileOnly(libs.plugins.hilt.toDep())
    compileOnly(libs.plugins.detekt.toDep())
    compileOnly(libs.plugins.ktlint.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("netguardAndroidApplication") {
            id = "netguard.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("netguardAndroidLibrary") {
            id = "netguard.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("netguardAndroidCompose") {
            id = "netguard.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("netguardAndroidHilt") {
            id = "netguard.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("netguardAndroidTest") {
            id = "netguard.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("netguardKotlinJvm") {
            id = "netguard.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
    }
}
