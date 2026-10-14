import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: calculators, models, formatting. No Android imports.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Compile against the JDK 17 class library, not the JDK running Gradle: newer JDK APIs
        // (e.g. List.removeFirst) would pass JVM tests but crash on Android, and Android lint
        // never analyzes this module.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    // Reads the shared normalization vectors of the food database builder.
    testImplementation(libs.kotlinx.serialization.json)
}

// The Python builder and the Kotlin normalizer are tested against the same vectors (PROMPT.md section 5.2).
tasks.named<ProcessResources>("processTestResources") {
    from(rootProject.file("tools/food-db-builder/normalize_cases.json"))
}
