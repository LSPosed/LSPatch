# What is Xpatch

Xpatch is a jar tool which is used to repackage the apk file. Then, the new apk can load any Xposed modules installed in the android system.  

This is a way to use Xposed modules without root your device. 

It is easy way to modify one app using xposed module. And any apps changed by Xpatch can load every modules downloaded in the [Xposed Module Repository](https://repo.xposed.info/).
# Benefits
1. Use xposed modules without your device;
2. Modify any apps without root your device.

# How to use
1. Download the latest jar file from the [release page](https://github.com/WindySha/Xpatch/releases);
2. Run this command in the Windows/Mac console:
```
$ java -jar ../../xpatch.jar  ../../source.apk
```
Then,  a new apk named `source-xposed-signed.apk` in the same folder as `source.apk`.

# More commands
1. You can specify the output apk path by add `-o` parameter, eg:
```
$ java -jar ../../xpatch.jar   ../../source.apk -o  ../../dst.apk
```
2. Show all the building new apk logs, just add `-l`, eg:
```
$ java -jar ../../xpatch.jar   ../../source.apk -l
```
3. Not delete the build files, just add `-k`, eg:
```
$ java -jar ../../xpatch.jar   ../../source.apk -k
```
4. After the version 1.2, craching app signature verifying is added, if you won't need the function, just add '-c', eg:
```
$ java -jar ../../xpatch.jar   ../../source.apk -c
```
5. More command details can be found when no parameter is added, eg:
```
$ java -jar ../../xpatch.jar 
```


# Todo list
1. Support packaging the xposed modules into the source apk;
2. Support loading so library in the xposed modules;
3. Crach apk protections.

# Issues
1. If the apk dex files are protected,  dex2jar can not effect on the dexs, then this tool will not work;
2. The hook framework is using [whale](https://github.com/asLody/whale); this framework is not very stable, some hooks may fail;
3. Do not support Davlik VM;
4. Do not support resource hook;

# Discuss
You can discuss with me under this page. 
[Xpatch Comments](https://windysha.github.io/2019/04/18/Xpatch-%E5%85%8DRoot%E5%AE%9E%E7%8E%B0App%E5%8A%A0%E8%BD%BDXposed%E6%8F%92%E4%BB%B6%E7%9A%84%E4%B8%80%E7%A7%8D%E6%96%B9%E6%A1%88/)


# Thanks
 - [Xposed][10]
 - [whale][11]
 - [dex2jar][12]
 - [AXMLPrinter2][13]

  [10]: https://github.com/rovo89/Xposed
  [11]: https://github.com/asLody/whale
  [12]: https://github.com/pxb1988/dex2jar
  [13]: https://code.google.com/archive/p/android4me/downloads
