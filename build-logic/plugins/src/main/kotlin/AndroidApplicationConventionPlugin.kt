import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("io.gitlab.arturbosch.detekt")
                apply("org.jlleitschuh.gradle.ktlint")
            }
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = rootProject.extra["targetSdk"] as Int
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                    getByName("debug") {
                        applicationIdSuffix = ".debug"
                        isDebuggable = true
                    }
                }
                // Universal APK + per-ABI splits
                splits {
                    abi {
                        isEnable = true
                        reset()
                        include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                        isUniversalApk = true
                    }
                }
                bundle {
                    abi { enableSplit = true }
                    density { enableSplit = true }
                    language { enableSplit = false }
                }
            }
        }
    }
}
