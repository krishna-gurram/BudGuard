plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Every CI run gets a higher version code, so a new APK installs over the old one.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "dev.krishnagurram.budguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.krishnagurram.budguard"
        minSdk = 33
        targetSdk = 35
        versionCode = runNumber
        versionName = "2.3.$runNumber"

        // The Fold 8 is arm64; dropping other CPU types cuts the APK from ~65 MB to ~25 MB.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Personal sideloaded app: the key lives in this private repo so every
    // build is signed the same way and updates install over each other.
    signingConfigs {
        create("release") {
            storeFile = file("budguard.jks")
            storePassword = "budguard-local"
            keyAlias = "budguard"
            keyPassword = "budguard-local"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// Screenshot renders run only when asked (-Pshots), so they never block an APK.
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("shots")) exclude("**/shots/**")
    // Robolectric fetches Android itself at test time; Google's Maven Central mirror is reliable from CI.
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
