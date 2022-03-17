plugins {
    id("com.android.library")
}

android {
    flavorDimensions += "api"
    productFlavors.create("Riru") {
            dimension = "api"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }
}

dependencies {
    api(projects.services.daemonService)
}
