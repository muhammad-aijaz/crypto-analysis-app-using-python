
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python") version "16.0.0"
}

android {

    sourceSets.getByName("main") {
        setRoot("src/main")}
    namespace = "com.example.cryptofilterapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cryptofilterapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
}

chaquopy {
    sourceSets.getByName("main") {
        setSrcDirs(listOf("src/main/python"))
    }
    defaultConfig {

        version = "3.11"
        buildPython("C:\\Users\\Aijaz\\AppData\\Local\\Programs\\Python\\Python311\\python.exe")

        pip {
            install("yarl==1.12.0")


            install("ccxt")
            install("pandas")
            install("pandas_ta")
     //      install("scipy")

 //           install("requests==2.24.0")
//            install("MyPackage-1.2.3-py2.py3-none-any.whl")
//            install("./MyPackage")
//            install("-r", "requirements.txt")
        }
    }
    }


dependencies {
//    implementation("com.chaquo.python:target:3.11.0")
//    implementation("androidx.compose.foundation:foundation:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}