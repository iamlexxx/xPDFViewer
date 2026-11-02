plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xpdfviewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xpdfviewer"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    val keystoreFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (!keystoreFile.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!keystoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // CameraX for custom document scanner
    val cameraVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // On-Device Neural TTS Engine (Sherpa-ONNX / Piper VITS)
    implementation("com.github.k2-fsa:sherpa-onnx:v2.0.1") {
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-native-lib-linux-aarch64")
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-native-lib-linux-x64")
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-native-lib-osx-aarch64")
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-native-lib-osx-x64")
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-native-lib-win-arm64")
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-native-lib-win-x64")
    }
    implementation("com.github.csukuangfj.piper-phonemize:piper-phonemize:v1.5.9")
    implementation("org.apache.commons:commons-compress:1.26.1")

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}

// Satisfy Android Studio Gradle sync when querying Kotlin build script model on subprojects
tasks.register("prepareKotlinBuildScriptModel") {}
