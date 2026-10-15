import com.android.build.api.artifact.SingleArtifact
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"): do not apply org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
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

    signingConfigs {
        getByName("debug") {
            // Committed debug key (standard "android" passwords, not a secret): debug APKs built in
            // different cloud sessions keep the same signature and install over each other.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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

    sourceSets {
        // Room's exported schemas are test assets (migration tests from phase 3 on).
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        getByName("test").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and manifest.
            isIncludeAndroidResources = true
        }
    }
}

room {
    // Exported schemas are committed; every schema change needs an explicit migration.
    schemaDirectory("$projectDir/schemas")
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
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    // ui-test-junit4 pulls espresso-core 3.5.0, which calls InputManager.getInstance() (gone in SDK 37).
    testImplementation(libs.androidx.test.espresso.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// --- Food database (PROMPT.md §5.1, §9) ---------------------------------------------------
// -PfoodDb=sample (default) packages tools/food-db-builder/out/foods-sample.db (committed, small, for
// development and tests); -PfoodDb=full packages out/foods-full.db (built by build_food_db.py, not
// committed). The chosen file ends up as assets/databases/foods.db, which Room copies on first launch, next to a
// content id (assets/databases/foods.db.id, a hash of the file): the app replaces its copy when the id changes, because
// Room alone would keep an old copy that has the same schema version. A RELEASE build refuses the sample database
// unless -PallowSampleRelease is given (a gate run that only checks that the release variant builds).

val foodDbChoice = providers.gradleProperty("foodDb").orElse("sample").get()
if (foodDbChoice != "sample" && foodDbChoice != "full") {
    throw GradleException("-PfoodDb must be 'sample' or 'full', not '$foodDbChoice'.")
}

abstract class CopyFoodDbTask : DefaultTask() {

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    @get:Input
    abstract val choice: Property<String>

    @get:Input
    abstract val releaseBuild: Property<Boolean>

    @get:Input
    abstract val allowSampleRelease: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val file = source.orNull?.asFile
        if (file == null || !file.isFile) {
            throw GradleException(
                "The ${choice.get()} food database is missing. Build it with " +
                    "tools/food-db-builder/build_food_db.py (see its README), or build with -PfoodDb=sample.",
            )
        }
        if (releaseBuild.get() && choice.get() != "full") {
            if (!allowSampleRelease.get()) {
                throw GradleException(
                    "A release build would ship the SAMPLE food database (about 3,000 foods). Build with -PfoodDb=full " +
                        "(tools/food-db-builder/out/foods-full.db), or add -PallowSampleRelease if this build is only a check.",
                )
            }
            logger.warn("WARNING: this release build packages the SAMPLE food database.")
        }
        val target = outputDir.get().asFile.resolve("databases/foods.db")
        target.parentFile.mkdirs()
        Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val contentId = digest.digest().joinToString("") { "%02x".format(it) }.take(32)
        outputDir.get().asFile.resolve("databases/foods.db.id").writeText(contentId)
    }
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

        val copyFoodDb = tasks.register<CopyFoodDbTask>("copy${variantName}FoodDb") {
            description = "Packages the $foodDbChoice food database as assets/databases/foods.db ($variantName)."
            choice.set(foodDbChoice)
            releaseBuild.set(variant.buildType == "release")
            allowSampleRelease.set(providers.gradleProperty("allowSampleRelease").isPresent)
            val dbFile = rootProject.layout.projectDirectory.file("tools/food-db-builder/out/foods-$foodDbChoice.db")
            if (dbFile.asFile.isFile) source.set(dbFile)
            outputDir.set(layout.buildDirectory.dir("generated/foodDb/${variant.name}"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyFoodDb, CopyFoodDbTask::outputDir)

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
