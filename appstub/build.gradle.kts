val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra
val androidBuildToolsVersion: String by rootProject.extra

val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("com.android.application")
}

android {
    compileSdk = androidCompileSdkVersion
    buildToolsVersion = androidBuildToolsVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion

        multiDexEnabled = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.capitalize()
    val variantLowered = variant.name.toLowerCase()

    task<Copy>("copyDex$variantCapped") {
        dependsOn("assemble$variantCapped")
        from("$buildDir/intermediates/dex/$variantLowered/mergeDex$variantCapped/classes.dex")
        rename("classes.dex", "loader.dex")
        into("${rootProject.projectDir}/out/assets/dex")
    }

    task("copy$variantCapped") {
        dependsOn("copyDex$variantCapped")

        doLast {
            println("Loader dex has been copied to ${rootProject.projectDir}${File.separator}out")
        }
    }
}

dependencies {
    compileOnly(projects.hiddenapiStubs)

    implementation("de.upb.cs.swt:axml:2.1.2")
}
