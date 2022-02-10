val androidCompileSdkVersion: Int by rootProject.extra
val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra

val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("com.android.application")
}

android {
    flavorDimensions += "api"
    productFlavors {
        create("Riru") {
            dimension = "api"
        }
    }

    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion

        multiDexEnabled = false

        signingConfigs.create("config") {
            val androidStoreFile = project.findProperty("androidStoreFile") as String?
            if (!androidStoreFile.isNullOrEmpty()) {
                storeFile = file(androidStoreFile)
                storePassword = project.property("androidStorePassword") as String
                keyAlias = project.property("androidKeyAlias") as String
                keyPassword = project.property("androidKeyPassword") as String
            }
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
        }
    }

    lint {
        abortOnError = false
    }
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.capitalize()
    val variantLowered = variant.name.toLowerCase()

    task<Copy>("copyDex$variantCapped") {
        dependsOn("assemble$variantCapped")
        from("$buildDir/intermediates/dex/$variantLowered/mergeDex$variantCapped/classes.dex")
        rename("classes.dex", "lsp.dex")
        into("${rootProject.projectDir}/out/dexes")
    }

    task<Copy>("copySo$variantCapped") {
        dependsOn("assemble$variantCapped")
        from("$buildDir/intermediates/merged_native_libs/$variantLowered/out/lib")
        into("${rootProject.projectDir}/out/so")
    }

    task("copy$variantCapped") {
        dependsOn("copySo$variantCapped")
        dependsOn("copyDex$variantCapped")

        doLast {
            println("Dex and so files has been copied to ${rootProject.projectDir}${File.separator}out")
        }
    }
}

dependencies {
    implementation(project(":daemon-service"))
    implementation(project(":lspcore"))
    implementation(project(":hiddenapi-bridge"))
    compileOnly(project(":hiddenapi-stubs"))
    implementation(project(":share"))
    implementation(project(":imanager"))

    implementation("com.google.code.gson:gson:2.8.9")
}
