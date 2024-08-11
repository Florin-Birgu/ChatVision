import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "ai.augmentedproducticity.chatvision"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.augmentedproducticity.chatvision"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildFeatures {
            buildConfig = true
        }
//            buildConfigField("String", "CHATGPT_API_KEY", "\"test123\"")

        // Load API key from local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val chatGptApiKey = localProperties["CHATGPT_API_KEY"] as String?
        if (chatGptApiKey != null) {
            buildConfigField("String", "CHATGPT_API_KEY", "\"$chatGptApiKey\"")
        }
        val geminiApiKey = localProperties["GEMINI_API_KEY"] as String?
        if (chatGptApiKey != null) {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false

            kotlinOptions {
                freeCompilerArgs = listOf("-Xdebug")
            }
        }
        release {
            isMinifyEnabled = false
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)


    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
//    implementation("androidx.camera:camera-video:1.3.4")
//    implementation("androidx.camera:camera-extensions:1.3.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("io.ktor:ktor-client-cio:2.3.11")
    implementation("io.ktor:ktor-client-okhttp:2.3.11")
    implementation("io.ktor:ktor-client-serialization:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")

//    implementation("com.aallam.openai:openai-client:3.7.2")

    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")

    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation(project(":openCV"))

//    implementation("org.opencv:opencv-android:4.10.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}