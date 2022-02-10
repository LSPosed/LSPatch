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
    implementation(project(":axmlprinter"))
    implementation(project(":share"))
    implementation(project(":apkzlib"))
    implementation("commons-io:commons-io:2.11.0")
    implementation("com.beust:jcommander:1.82")
    implementation("com.google.code.gson:gson:2.8.9")
}

tasks.jar {
    archiveBaseName.set("lspatch")
    destinationDirectory.set(file("${rootProject.projectDir}/out"))
    manifest {
        attributes("Main-Class" to "org.lsposed.patch.LSPatch")
    }
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    from("src/main") {
        include("assets/**")
    }

    into("assets/dex") {
        from("${rootProject.projectDir}/out/dexes")
    }

    into("assets/so") {
        from("${rootProject.projectDir}/out/so")
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF", "META-INF/*.txt", "META-INF/versions/**")
}

val jar = tasks.jar.get()

tasks.register("buildDebug") {
    jar.dependsOn(":appstub:copyDebug")
    jar.dependsOn(":app:copyRiruDebug")
    dependsOn(tasks.build)
}

tasks.register("buildRelease") {
    jar.dependsOn(":appstub:copyRelease")
    jar.dependsOn(":app:copyRiruRelease")
    dependsOn(tasks.build)
}

tasks["build"].doLast {
    println("Build to " + jar.archiveFile)
    println("Try \'java -jar " + jar.archiveFileName + "\' find more help")
}

sourceSets["main"].resources {
    srcDirs("src/main/java")
    include("**/*.*")
}
