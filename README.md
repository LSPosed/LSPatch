## Introduction 

LSPatch fork from Xpatch. 

LSPatch provides a way to insert dex and so into the target APK by repackaging. The following changes have been made since Xpatch

1. use LSPosed as Hook framework
1. Clean up the code
1. merge Xpatch's Loader and Patch into a single project


## Usage

1. download the artifact
1. run `java -jar lspatch.jar`

## Dev

```
Android Studio Arctic Fox | 2020.3.1 +
```

## Build

```
gradlew build<Debug|Release>
```

## Supported Android Versions

- Min: Android 9
- Max: In theory, same with [LSPosed](https://github.com/LSPosed/LSPosed#supported-versions)

## Known issues

1. Can't solve the signature verification issue perfectly