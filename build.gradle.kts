// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // THIS WAS MISSING: The Google Services Plugin
    id("com.google.gms.google-services") version "4.4.1" apply false
}