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

        // ANGLE is the in-process GLES backend. Native code links the prebuilt
        // ANGLE shared libraries (libEGL.so / libGLESv2.so) packaged under
        // src/main/jniLibs/<abi>. CI builds ANGLE for Android and copies them in
        // before the Gradle build. See ARCHITECTURE.md and .github/workflows.
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                cppFlags += "-Wall -Wextra"
                arguments += "-DANDROID_STL=c++_shared"
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    // Enable armeabi-v7a only when required: -PangleAbiV7a=true
    if (project.hasProperty("angleAbiV7a") && project.property("angleAbiV7a") == "true") {
        defaultConfig.externalNativeBuild.cmake.abiFilters += listOf("armeabi-v7a")
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

    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
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
        // Do not pick up stale prebuilt ANGLE libs from the source tree in CI
        // unless they were produced by the ANGLE build step.
        jniLibs {
            pickFirsts += "lib/arm64-v8a/libc++_shared.so"
        }
    }
}

dependencies {
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
