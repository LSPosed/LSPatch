## Introduction 

LSPatch fork from Xpatch. 

LSPatch provides a way to insert dex and so into the target APK by repackaging. The following changes have been made since Xpatch

1. use LSPosed as Hook framework
1. Clean up the code
1. merge Xpatch's Loader and Patch into a single project


## Usage

1. download the artifact
1. run `java -jar mmpatch.jar`

## Build

```
gradle build[Debug|Release]
```

## Principle

LSPatch modifies the app property of AndroidManifest.xml in the target APK, changing it to the Application class in the inserted dex. Then it initializes LSPosed, and loads the Xposed module installed in the system.

## Known issues

Can't solve the signature verification issue perfectly
