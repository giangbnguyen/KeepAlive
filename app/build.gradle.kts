plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Stable signing key supplied by CI (see README). Without it every CI run would sign with a
// fresh random debug key, and updating the app would require an uninstall (losing all accounts).
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "com.gmailchecker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gmailchecker.app"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("GITHUB_REF_NAME")
            ?.takeIf { it.startsWith("v") }
            ?.removePrefix("v")
            ?: "1.0.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("stable") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val stableSigning = signingConfigs.findByName("stable")
        debug {
            if (stableSigning != null) signingConfig = stableSigning
        }
        release {
            isMinifyEnabled = false
            signingConfig = stableSigning
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
