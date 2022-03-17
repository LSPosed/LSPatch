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
    implementation(projects.patch)
}

tasks.jar {
    archiveBaseName.set("lspatch")
    destinationDirectory.set(file("${rootProject.projectDir}/out"))
    manifest {
        attributes("Main-Class" to "org.lsposed.patch.LSPatch")
    }
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    into("assets") {
        from("src/main/assets")
        from("${rootProject.projectDir}/out/assets")
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF", "META-INF/*.txt", "META-INF/versions/**")
}

val jar = tasks.jar.get()

tasks.register("buildDebug") {
    jar.dependsOn(":appstub:copyDebug")
    jar.dependsOn(":patch-loader:copyDebug")
    dependsOn(tasks.build)
}

tasks.register("buildRelease") {
    jar.dependsOn(":appstub:copyRelease")
    jar.dependsOn(":patch-loader:copyRelease")
    dependsOn(tasks.build)
}

tasks["build"].doLast {
    println("Build to " + jar.archiveFile)
    println("Try \'java -jar " + jar.archiveFileName + "\' find more help")
}
