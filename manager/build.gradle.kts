val defaultManagerPackageName: String by rootProject.extra
val apiCode: Int by rootProject.extra
val verCode: Int by rootProject.extra
val verName: String by rootProject.extra
val coreVerCode: Int by rootProject.extra
val coreVerName: String by rootProject.extra

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("dev.rikka.tools.refine")
    id("kotlin-parcelize")
    kotlin("android")
}

android {
    defaultConfig {
        applicationId = defaultManagerPackageName
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            proguardFiles("proguard-rules-debug.pro")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.2.0"
    }

    sourceSets["main"].assets.srcDirs(rootProject.projectDir.resolve("out/assets"))
}

afterEvaluate {
    android.applicationVariants.forEach { variant ->
        val variantLowered = variant.name.toLowerCase()
        val variantCapped = variant.name.capitalize()

        task<Copy>("copy${variantCapped}Assets") {
            dependsOn(":appstub:copy$variantCapped")
            dependsOn(":patch-loader:copy$variantCapped")
            tasks["merge${variantCapped}Assets"].dependsOn(this)

            into("$buildDir/intermediates/assets/$variantLowered/merge${variantCapped}Assets")
            from("${rootProject.projectDir}/out/assets")
        }

        task<Copy>("build$variantCapped") {
            dependsOn(tasks["assemble$variantCapped"])
            from(variant.outputs.map { it.outputFile })
            into("${rootProject.projectDir}/out/$variantLowered")
            rename(".*.apk", "manager-v$verName-$verCode-$variantLowered.apk")
        }
    }
}

dependencies {
    implementation(projects.hiddenapi.bridge)
    implementation(projects.patch)
    implementation(projects.services.daemonService)
    implementation(projects.share.android)
    implementation(projects.share.java)

    val roomVersion = "2.4.2"
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    compileOnly("dev.rikka.hidden:stub:2.3.1")
    implementation("dev.rikka.hidden:compat:2.3.1")
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.activity:activity-compose:1.6.0-alpha05")
    implementation("androidx.compose.material:material-icons-extended:1.3.0-alpha01")
    implementation("androidx.compose.material3:material3:1.0.0-alpha14")
    implementation("androidx.compose.runtime:runtime-livedata:1.3.0-alpha01")
    implementation("androidx.compose.ui:ui:1.3.0-alpha01")
    implementation("androidx.compose.ui:ui-tooling:1.3.0-alpha01")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.0")
    implementation("androidx.navigation:navigation-compose:2.5.0")
    implementation("androidx.preference:preference:1.2.0")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.24.11-rc")
    implementation("com.google.accompanist:accompanist-navigation-animation:0.24.11-rc")
    implementation("com.google.accompanist:accompanist-pager:0.24.11-rc")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.24.11-rc")
    implementation("com.google.android.material:material:1.6.1")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("dev.rikka.shizuku:api:12.1.0")
    implementation("dev.rikka.shizuku:provider:12.1.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    ksp("androidx.room:room-compiler:$roomVersion")
}
