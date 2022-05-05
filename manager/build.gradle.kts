val defaultManagerPackageName: String by rootProject.extra
val apiCode: Int by rootProject.extra
val coreVerCode: Int by rootProject.extra
val coreVerName: String by rootProject.extra

plugins {
    id("com.android.application")
    id("dev.rikka.tools.refine")
    id("kotlin-parcelize")
    kotlin("android")
}

android {
    defaultConfig {
        applicationId = defaultManagerPackageName

        buildConfigField("int", "API_CODE", """$apiCode""")
        buildConfigField("int", "CORE_VERSION_CODE", """$coreVerCode""")
        buildConfigField("String", "CORE_VERSION_NAME", """"$coreVerName"""")
    }

    buildTypes {
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
        kotlinCompilerExtensionVersion = "1.2.0-alpha03"
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
            into("${rootProject.projectDir}/out")
            rename(".*.apk", "manager.apk")
        }
    }
}

dependencies {
    implementation(projects.hiddenapi.bridge)
    implementation(projects.patch)
    implementation(projects.services.daemonService)
    implementation(projects.share.android)

    compileOnly("dev.rikka.hidden:stub:2.3.1")
    implementation("dev.rikka.hidden:compat:2.3.1")
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.activity:activity-compose:1.6.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended:1.1.1")
    implementation("androidx.compose.material3:material3:1.0.0-alpha08")
    implementation("androidx.compose.runtime:runtime-livedata:1.1.1")
    implementation("androidx.compose.ui:ui:1.1.1")
    implementation("androidx.compose.ui:ui-tooling:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.0-beta01")
    implementation("androidx.navigation:navigation-compose:2.5.0-beta01")
    implementation("androidx.preference:preference:1.2.0")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.24.5-alpha")
    implementation("com.google.accompanist:accompanist-navigation-animation:0.24.5-alpha")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.24.5-alpha")
    implementation("com.google.android.material:material:1.5.0")
    implementation("dev.rikka.shizuku:api:12.1.0")
    implementation("dev.rikka.shizuku:provider:12.1.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
