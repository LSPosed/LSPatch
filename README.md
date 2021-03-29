# What is MMPatch

fork from [Xpatch][11]

MMPatch is a jar tool which is used to repackage the apk file. Then, the new apk can load any Xposed modules installed in the android system.  

This is a way to use Xposed modules without root your device. 

It is easy way to modify one app using xposed module. And any apps changed by MMPatch can load every modules downloaded in the [Xposed Module Repository](https://repo.xposed.info/).

# Benefits

1. Use xposed modules without your device;
2. Modify any apps without root your device.

# How to use

1. Download the latest jar file from the [release page](https://github.com/327135569/MMPatch/releases);
1. Put dex files you wanna package to app in `list-dex` dir
1. Put so files you wanna package to app in `list-so/{eabi}` dir, eg: `list-so/armeabi-v7a`.
1. Run this command in the Windows/Mac console:
```
$ java -jar mmpatch.jar source.apk
```
Then,  a new apk named `source-xposed-signed.apk` in the same folder as `source.apk`.

More command details can be found when no parameter is added, eg:
```
$ java -jar mmpatch.jar 
```

# How to disable Xposed modules

When the new apk is installed in the device, It will load all the Xposed modules installed in the device when it's process started.  

But you can manage the installed Xposed modules on/off state by a file in the storage.  
The file path is `/sdcard/xpmodules.list`.  

When the new app started, it will search all the installed Xposed modules and write the the module app name and the module application name into this file. (`/sdcard/xpmodules.list`)
eg: 
```
com.blanke.mdwechat#MDWechat
liubaoyua.customtext#文本自定义
```
Each line of this file is Application Name#App Name.
You can disable a Xposed module by add `#` before the Application Name, eg:  
```
#com.blanke.mdwechat#MDWechat
liubaoyua.customtext#文本自定义
```
This means the MDWechat Xposed module is disabled.  

```
#com.blanke.mdwechat#MDWechat
#liubaoyua.customtext#文本自定义
```
This means all Xposed modules are disabled.    

Note: The target app must have file system access permission. Otherwise this file will not be created, and all xposed modules are enabled.


# Thanks to

 - [Xpatch][11]
 - [Xposed][10]
 - [AXMLPrinter2][13]


  [11]: https://github.com/WindySha/Xpatch.git
  [10]: https://github.com/rovo89/Xposed
  [13]: https://code.google.com/archive/p/android4me/downloads
