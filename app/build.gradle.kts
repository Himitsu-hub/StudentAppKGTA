plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)

}

android {
    namespace = "ru.alemak.studentapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.alemak.studentapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil)
    implementation(libs.coil.svg)

    implementation(libs.hilt.androidx)
    ksp(libs.hilt.androidx.compiler)

    // Excel parsing
    implementation("org.apache.poi:poi:5.2.4")
    implementation("org.apache.poi:poi-ooxml:5.2.4")

    // Для работы с файлами
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.work.runtime.ktx)
}