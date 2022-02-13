val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra

plugins {
    id("java-library")
}

java {
    sourceCompatibility = androidSourceCompatibility
    targetCompatibility = androidTargetCompatibility
}

dependencies {
    implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))
    implementation(projects.apkzlib)
    implementation(projects.axmlprinter)
    implementation(projects.share)

    implementation("commons-io:commons-io:2.11.0")
    implementation("com.beust:jcommander:1.82")
    implementation("com.google.code.gson:gson:2.8.9")
}
