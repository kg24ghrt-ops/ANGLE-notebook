plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.angledpaper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.angledpaper"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Filament is used as a prebuilt Maven AAR (no native source compilation).
        // ABI filter: arm64-v8a first; add armeabi-v7a only if required.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    if (project.hasProperty("angleAbiV7a") && project.property("angleAbiV7a") == "true") {
        defaultConfig.ndk.abiFilters += listOf("armeabi-v7a")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "lib/arm64-v8a/libc++_shared.so"
        }
    }
}

dependencies {
    // Filament: prebuilt AAR from Maven Central — no native build in CI.
    implementation("com.google.android.filament:filament-android:1.77.3")
    implementation("com.google.android.filament:filamat-android:1.77.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
