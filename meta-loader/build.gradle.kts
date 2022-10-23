plugins {
    id("com.android.application")
}

android {

    defaultConfig {
        multiDexEnabled = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.capitalize()
    val variantLowered = variant.name.toLowerCase()

    task<Copy>("copyDex$variantCapped") {
        dependsOn("assemble$variantCapped")
        val dexOutPath = if (variant.buildType == "release")
            "$buildDir/intermediates/dex/$variantLowered/minify${variantCapped}WithR8" else
            "$buildDir/intermediates/dex/$variantLowered/mergeDex$variantCapped"
        from(dexOutPath)
        rename("classes.dex", "metaloader.dex")
        into("${rootProject.projectDir}/out/assets/lspatch")
    }

    task("copy$variantCapped") {
        dependsOn("copyDex$variantCapped")

        doLast {
            println("Loader dex has been copied to ${rootProject.projectDir}${File.separator}out")
        }
    }
}

dependencies {
    compileOnly(projects.hiddenapi.stubs)
    implementation(projects.share.java)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
