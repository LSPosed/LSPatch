## Introduction 

LSPatch fork from Xpatch. 

LSPatch provides a way to insert dex and so into the target APK by repackaging. The following changes have been made since Xpatch

1. use LSPosed as Hook framework
1. Clean up the code
1. merge Xpatch's Loader and Patch into a single project


## Usage

1. download the artifact
1. run `java -jar lspatch.jar`

## Build

```
Android Studio Arctic Fox | 2020.3.1 Beta 3
```

```
gradlew build[Debug|Release]
```

## Supported Android Versions
Same with [LSPosed](
https://github.com/LSPosed/LSPosed#supported-versions)

## Principle

1. Decompress target APK.
1. Patch the app property of AndroidManifest.xml in the target APK, changing it to the Application class in the inserted dex.
1. Copy all files in `list-so`, `list-assets`, `list-dex` into target APK.
1. Package and sign target APK.

Running Stage:
1. Inserted dex initializes LSPosed
1. New ClassLoader from `assets/lsploader.dex`.
1. Loads the Xposed module installed in the system with new ClassLoader.

## Known issues

1. Can't solve the signature verification issue perfectly
1. If you use under Windows, you need open `CMD/Powershell` with `Run as Administrator`, See [Code](https://github.com/LSPosed/LSPatch/blob/ab1a213161f90ec7ac604df47434201170b92b9a/patch/src/main/java/org/lsposed/patch/util/FileUtils.java#L67-L70).
