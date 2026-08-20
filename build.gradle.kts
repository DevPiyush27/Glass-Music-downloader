// build.gradle.kts (Project Level)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.chaquo.python") version "15.0.1" apply false
}