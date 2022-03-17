plugins {
    id("com.android.application")
}

android {

    defaultConfig {
        multiDexEnabled = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    compileOnly(projects.hiddenapi.stubs)

    implementation("de.upb.cs.swt:axml:2.1.2")
}
