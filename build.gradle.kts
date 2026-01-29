plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Hilt
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    // KSP - Kotlin 2.0.21用
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    // Serialization - Kotlin 2.0.21用
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}