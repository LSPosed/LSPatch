val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra
val androidBuildToolsVersion: String by rootProject.extra

val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("com.android.library")
}

android {
    flavorDimensions += "api"
    productFlavors.create("Riru") {
            dimension = "api"
    }

    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

dependencies {
    api(project(":daemon-service"))
}
