import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    val agpVersion by extra("7.1.2")
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
    }
}

val commitCount = run {
    val repo = FileRepository(rootProject.file(".git"))
    val refId = repo.refDatabase.exactRef("refs/remotes/origin/lsp").objectId!!
    Git(repo).log().add(refId).call().count()
}

val coreCommitCount = run {
    val repo = FileRepository(rootProject.file("core/.git"))
    val refId = repo.refDatabase.exactRef("refs/remotes/origin/lspatch").objectId!!
    Git(repo).log().add(refId).call().count()
}

// sync from https://github.com/LSPosed/LSPosed/blob/master/build.gradle.kts
val defaultManagerPackageName by extra("org.lsposed.lspatch")
val apiCode by extra(93)
val verCode by extra(commitCount)
val verName by extra("0.3")
val coreVerCode by extra(coreCommitCount + 4200)
val coreVerName by extra("1.7.2")
val androidMinSdkVersion by extra(28)
val androidTargetSdkVersion by extra(32)
val androidCompileSdkVersion by extra(32)
val androidCompileNdkVersion by extra("23.1.7779620")
val androidBuildToolsVersion by extra("31.0.0")
val androidSourceCompatibility by extra(JavaVersion.VERSION_11)
val androidTargetCompatibility by extra(JavaVersion.VERSION_11)

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

listOf("Debug", "Release").forEach { variant ->
    tasks.register("build$variant") {
        description = "Build LSPatch with $variant"
        dependsOn(projects.patchJar.dependencyProject.tasks["build$variant"])
        dependsOn(projects.manager.dependencyProject.tasks["build$variant"])
    }
}
