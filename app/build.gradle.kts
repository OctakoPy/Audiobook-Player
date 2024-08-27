plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)  // Change this line from kapt to ksp
}

android {
    namespace = "com.example.audiobookplayerbeta"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.audiobookplayerbeta"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.room.runtime.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.media)
    implementation(libs.androidx.swiperefreshlayout)


    // Change these lines
    ksp(libs.androidx.room.compiler)  // Use ksp instead of kapt
    implementation(libs.androidx.room.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}