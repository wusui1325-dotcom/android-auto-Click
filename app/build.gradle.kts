plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties

android {
    namespace = "com.wusui.androidautoclick"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wusui.androidautoclick"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6.2"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val local = rootProject.file("local.properties")
            if (local.exists()) {
                local.inputStream().use { props.load(it) }
            }
            // Prefer env (CI secrets) then local.properties. No committed plaintext defaults.
            val storePath = System.getenv("STORE_FILE")
                ?: props.getProperty("storeFile")
                ?: "release.keystore"
            val storePass = System.getenv("STORE_PASSWORD")
                ?: props.getProperty("storePassword")
            val alias = System.getenv("KEY_ALIAS")
                ?: props.getProperty("keyAlias")
                ?: "release"
            val keyPass = System.getenv("KEY_PASSWORD")
                ?: props.getProperty("keyPassword")
                ?: storePass
            // Resolve against repo root so "release.keystore" and "../release.keystore" both work
            val candidate = rootProject.file(storePath)
            storeFile = if (candidate.exists()) candidate else file(storePath)
            if (storePass.isNullOrBlank() || keyPass.isNullOrBlank()) {
                // Allow configure phase; assembleRelease will fail if file/password missing.
                println("WARNING: release signing password missing (set env or local.properties). See Drive android-auto-Click-secrets.")
            } else {
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
