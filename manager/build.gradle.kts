import java.util.Locale

val defaultManagerPackageName: String by rootProject.extra
val apiCode: Int by rootProject.extra
val verCode: Int by rootProject.extra
val verName: String by rootProject.extra
val coreVerCode: Int by rootProject.extra
val coreVerName: String by rootProject.extra

plugins {
    alias(libs.plugins.agp.app)
    id("com.google.devtools.ksp")
    id("dev.rikka.tools.refine")
    id("kotlin-parcelize")
    kotlin("android")
}

android {
    defaultConfig {
        applicationId = defaultManagerPackageName
    }

    androidResources {
        noCompress.add(".so")
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.2"
    }

    sourceSets["main"].assets.srcDirs(rootProject.projectDir.resolve("out/assets"))
    namespace = "org.lsposed.lspatch"

    applicationVariants.all {
        kotlin.sourceSets {
            getByName(name) {
                kotlin.srcDir("build/generated/ksp/$name/kotlin")
            }
        }
    }
}

afterEvaluate {
    android.applicationVariants.forEach { variant ->
        val variantLowered = variant.name.lowercase(Locale.getDefault())
        val variantCapped = variant.name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.getDefault()
            ) else it.toString()
        }

        task<Copy>("copy${variantCapped}Assets") {
            dependsOn(":meta-loader:copy$variantCapped")
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
    implementation(projects.patch)
    implementation(projects.services.daemonService)
    implementation(projects.share.android)
    implementation(projects.share.java)

    val roomVersion = "2.5.2"
    val accompanistVersion = "0.27.0"
    val composeDestinationsVersion = "1.9.42-beta"
    implementation(platform("androidx.compose:compose-bom:2023.06.01"))

    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    compileOnly("dev.rikka.hidden:stub:4.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.customview:customview:1.2.0-alpha02")
    debugImplementation("androidx.customview:customview-poolingcontainer:1.0.0")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("androidx.preference:preference:1.2.0")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("com.google.accompanist:accompanist-navigation-animation:$accompanistVersion")
    implementation("com.google.accompanist:accompanist-pager:$accompanistVersion")
    implementation("com.google.accompanist:accompanist-swiperefresh:$accompanistVersion")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("dev.rikka.shizuku:api:13.1.2")
    implementation("dev.rikka.shizuku:provider:13.1.2")
    implementation("dev.rikka.tools.refine:runtime:4.3.0")
    implementation("io.github.raamcosta.compose-destinations:core:$composeDestinationsVersion")
    implementation("me.zhanghai.android.appiconloader:appiconloader:1.5.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    ksp("androidx.room:room-compiler:$roomVersion")
    ksp("io.github.raamcosta.compose-destinations:ksp:$composeDestinationsVersion")
}
