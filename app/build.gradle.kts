plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.agrovision.kiosk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.agrovision.kiosk"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // CameraX core libraries
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    
    // Room components
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    
    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}