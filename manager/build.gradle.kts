val androidCompileSdkVersion: Int by rootProject.extra
val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra

val defaultManagerPackageName: String by rootProject.extra
val verCode: Int by rootProject.extra
val verName: String by rootProject.extra

val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

val composeVersion = "1.2.0-alpha03"

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = defaultManagerPackageName
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = verCode
        versionName = verName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = composeVersion
    }

    sourceSets["main"].assets.srcDirs(
        projects.patch.dependencyProject.projectDir.resolve("src/main/assets"),
        rootProject.projectDir.resolve("out/assets")
    )
}

afterEvaluate {
    android.applicationVariants.forEach { variant ->
        val variantLowered = variant.name.toLowerCase()
        val variantCapped = variant.name.capitalize()

        task<Copy>("copy${variantCapped}Assets") {
            dependsOn(":appstub:copy$variantCapped")
            dependsOn(":app:copyRiru$variantCapped")
            tasks["merge${variantCapped}Assets"].dependsOn(this)

            into("$buildDir/intermediates/assets/$variantLowered/merge${variantCapped}Assets")
            from("${rootProject.projectDir}/out/assets")
        }

        task<Copy>("build$variantCapped") {
            dependsOn(tasks["assemble$variantCapped"])
            from(variant.outputs.map { it.outputFile })
            into("${rootProject.projectDir}/out")
        }
    }
}

dependencies {
    implementation(projects.imanager)
    implementation(projects.patch)

    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.activity:activity-compose:1.5.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended:1.0.5")
    implementation("androidx.compose.material3:material3:1.0.0-alpha04")
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling:$composeVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.0-alpha01")
    implementation("androidx.navigation:navigation-compose:2.5.0-alpha01")
    implementation("androidx.preference:preference:1.2.0")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.24.2-alpha")
    implementation("com.google.accompanist:accompanist-navigation-animation:0.24.2-alpha")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.24.2-alpha")
    implementation("com.google.android.material:material:1.5.0")
}
