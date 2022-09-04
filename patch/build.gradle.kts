val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("java-library")
}

java {
    sourceCompatibility = androidSourceCompatibility
    targetCompatibility = androidTargetCompatibility
    sourceSets {
        main {
            java.srcDirs("libs/manifest-editor/lib/src/main/java")
            resources.srcDirs("libs/manifest-editor/lib/src/main")
        }
    }
}

dependencies {
    implementation(projects.apkzlib)
    implementation(projects.axmlprinter)
    implementation(projects.share.java)

    implementation("commons-io:commons-io:2.11.0")
    implementation("com.beust:jcommander:1.82")
    implementation("com.google.code.gson:gson:2.9.1")
}
