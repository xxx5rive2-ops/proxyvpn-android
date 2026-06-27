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
    // AGP — Android Gradle Plugin
    compileOnly("com.android.tools.build:gradle:8.7.3")
    // Kotlin Gradle Plugin
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    // KSP
    compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.0-1.0.29")
    // Hilt
    compileOnly("com.google.dagger:hilt-android-gradle-plugin:2.54")
    // Kotlin Compose
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0")
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
