import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("plugin.serialization") version "2.3.20"
}

// Load local properties
val plazaProperties = Properties().apply {
    val propFile = rootProject.file("plaza.properties")
    if (propFile.canRead()) {
        propFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "one.plaza.nightwaveplaza"
    compileSdk = 36

    defaultConfig {
        applicationId = "one.plaza.nightwaveplaza"
        minSdk = 23
        targetSdk = 35
        versionCode = 244
        versionName = "2.1.4"

        buildFeatures {
            buildConfig = true
        }

        vectorDrawables {
            useSupportLibrary = true
        }

        fun String.asConfigValue() = "\"$this\""
        buildConfigField("String", "PLAZA_API", plazaProperties.getProperty("PLAZA_API", "").asConfigValue())
        buildConfigField("String", "PLAZA_URL_OVERRIDE", plazaProperties.getProperty("PLAZA_URL_OVERRIDE", "").asConfigValue())

        manifestPlaceholders += mapOf(
            "sentryDsn" to plazaProperties.getProperty("SENTRY_DSN", "")
        )
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/NightwavePlaza.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_SIGN_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_SIGN_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs["release"]
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/AL2.0"
            excludes += "/META-INF/LGPL2.1"
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-session:1.10.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.0")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("androidx.webkit:webkit:1.15.0")

    implementation("com.github.bumptech.glide:glide:5.0.7")

    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("io.sentry:sentry-android:8.39.1")
    //debugImplementation "com.squareup.leakcanary:leakcanary-android:2.14"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.register("printVersionName") {
    val vName = android.defaultConfig.versionName ?: "unknown"
    val vCode = android.defaultConfig.versionCode ?: 0

    doLast {
        println("$vName-b$vCode")
    }
}

tasks.register("createReleaseTag") {
    val vName = android.defaultConfig.versionName ?: "unknown"
    val vCode = android.defaultConfig.versionCode ?: 0

    doLast {
        val versionName = "$vName-b$vCode"
        val tagName = "v${versionName}"

        println("Creating tag: ${tagName}...")
        val process = ProcessBuilder(listOf("git", "tag", "-a", tagName, "-m", "Release $versionName")).start()
        process.waitFor()
        if (process.exitValue() == 0) {
            println("Tag ${tagName} created.")
        } else {
            val errorText = process.errorStream.bufferedReader().use { it.readText() }
            println("Error creating tag: ${errorText}")
        }
    }
}

tasks.register<FetchAppViewTask>("fetchAndEmbedView") {
    manifestUrl.set("https://akai.plaza.one/app-view/update-manifest.json")
    appVersionCode.set(android.defaultConfig.versionCode ?: 1)
    assetsDir.set(layout.projectDirectory.dir("src/main/assets/www"))

    // Disable cache
    outputs.upToDateWhen { false }
}