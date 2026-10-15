// Top-level build file. Plugins are declared here (not applied) so that every module shares
// one classpath; declaring the Kotlin plugins at the catalog's Kotlin version also makes AGP's
// built-in Kotlin use that Kotlin Gradle Plugin version instead of the one AGP bundles.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
