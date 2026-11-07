import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

val supportedApkAbis = listOf("arm64-v8a", "x86_64")
val requestedApkAbi = providers.gradleProperty("apkAbi").orNull
require(requestedApkAbi == null || requestedApkAbi in supportedApkAbis) {
    "apkAbi must be arm64-v8a or x86_64."
}

val torrentEngineAar = providers.gradleProperty("torrentEngineAar").orNull?.let { file(it) }
require(torrentEngineAar == null || torrentEngineAar.isFile) {
    "torrentEngineAar must point to the verified jlibtorrent Android AAR."
}

if (torrentEngineAar != null) {
    val digest = MessageDigest.getInstance("SHA-256")
    torrentEngineAar.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
    require(checksum == "9f14e44e27389ae1fbefa9f7e2ef6853b57387aa829cde4d5170722b8ae8382e") {
        "torrentEngineAar does not match the pinned jlibtorrent Android 2.0.15.3 checksum."
    }
}

android {
    namespace = "com.garyapp.ytdl"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.garyapp.ytdl"
        minSdk = if (torrentEngineAar == null) 24 else 26
        buildConfigField("boolean", "TORRENT_ENABLED", (torrentEngineAar != null).toString())
        manifestPlaceholders["torrentEnabled"] = (torrentEngineAar != null).toString()
        manifestPlaceholders["appDisplayName"] = if (torrentEngineAar == null) "CaptainFlash" else "CaptainFlash Torrent"
        targetSdk = 37
        versionCode = 19
        versionName = "1.11.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += requestedApkAbi?.let { listOf(it) } ?: supportedApkAbis
        }
    }

    sourceSets["main"].kotlin.directories.add(if (torrentEngineAar == null) "src/standard/java" else "src/torrent/java")
    if (torrentEngineAar != null) sourceSets["test"].kotlin.directories.add("src/torrentTest/java")

    packaging.jniLibs.useLegacyPackaging = true

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        if (torrentEngineAar != null) {
            unitTests.all {
                // These API 24 cases exercise the standard APK. Torrent requires API 26.
                it.filter.excludeTestsMatching("com.garyapp.ytdl.download.HistoryFileDeletionTest.api24DeletesOrdinaryPrivateFileWithoutNio26")
                it.filter.excludeTestsMatching("com.garyapp.ytdl.ui.CookiesDocumentPickerTest.olderAndroidUsesSystemStorageNavigation")
            }
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython(providers.environmentVariable("YTDL_BUILD_PYTHON").orElse("python3").get())
        pip {
            install("yt-dlp==2026.3.17")
        }
    }
}

dependencies {
    if (torrentEngineAar != null) implementation(files(torrentEngineAar))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    annotationProcessor("androidx.room:room-compiler:2.8.4")

    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0-rc01")
}
