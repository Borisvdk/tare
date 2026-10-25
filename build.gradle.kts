plugins {
    alias(libs.plugins.android.application) apply false
    // Pins the Kotlin Gradle Plugin version that AGP's built-in Kotlin support uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}
