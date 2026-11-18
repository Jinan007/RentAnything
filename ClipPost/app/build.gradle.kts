plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.clippost.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clippost.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // A fixed keystore committed to the repo, so every build has the same SHA-1.
    // This is a test key: its password is public in the repo. Do not use it for a Play Store release.
    signingConfigs {
        create("clippost") {
            storeFile = file("../keystore/clippost-debug.jks")
            storePassword = "clippost123"
            keyAlias = "clippost"
            keyPassword = "clippost123"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("clippost")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
}
