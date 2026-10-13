import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"): do not apply org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.privat.kalorienzaehler"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.privat.kalorienzaehler"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // x86_64 only for the emulator.
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        error += setOf("HardcodedText", "SetTextI18n")
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and manifest.
            isIncludeAndroidResources = true
        }
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric's SDK 37 sandbox requires a Java 21 runtime (bytecode stays Java 17).
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    // Robolectric's android-all jar is large; JDK 17+ needs these opens for its sandbox.
    maxHeapSize = "2g"
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
    )
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Provides the ComponentActivity that createComposeRule() launches.
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// --- Offline safeguard (PROMPT.md §2.1, §2.4) --------------------------------------------
// Fails the build if the merged manifest of any variant requests a permission other than
// CAMERA. androidx.core adds an app-private, signature-level permission
// (<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) that only the app itself can
// hold; it grants nothing and is allowed.

abstract class VerifyManifestPermissionsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowedPermissions: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(mergedManifest.get().asFile)
        val declared = listOf("uses-permission", "uses-permission-sdk-23")
            .flatMap { tag ->
                val nodes = document.getElementsByTagName(tag)
                (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
            }
            .toSortedSet()
        report.get().asFile.writeText(declared.joinToString(separator = "\n", postfix = "\n"))

        if ("android.permission.INTERNET" in declared) {
            throw GradleException("The merged manifest requests android.permission.INTERNET. The app must stay offline.")
        }
        val unexpected = declared - allowedPermissions.get()
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                "The merged manifest requests unexpected permissions: $unexpected. " +
                    "Only CAMERA is allowed; remove the others with tools:node=\"remove\".",
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyManifestPermissionsTask>("verify${variantName}ManifestPermissions") {
            group = "verification"
            description = "Checks that the merged $variantName manifest only requests allowed permissions."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowedPermissions.set(
                variant.applicationId.map { appId ->
                    setOf("android.permission.CAMERA", "$appId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                },
            )
            report.set(layout.buildDirectory.file("reports/manifest-permissions/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(verify) }
        tasks.matching { it.name == "assemble$variantName" }.configureEach { dependsOn(verify) }
    }
}
