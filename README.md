### [**English Version**](https://github.com/WindySha/Xpatch/blob/6ec0f3c16128dda46ab05bdd915d66ebbdaaf9fc/README_en.md)

# Android App破解工具Xpatch的使用方法

## Xpatch概述
Xpatch用来重新签名打包Apk文件，使重打包后的Apk能加载安装在系统里的Xposed插件，从而实现免Root Hook任意App。

## Xpatch基本原理
Xpatch的原理是对Apk文件进行二次打包，重新签名，并生成一个新的apk文件。
在Apk二次打包过程中，插入加载Xposed插件的逻辑，这样，新的Apk文件就可以加载任意Xposed插件，从而实现免Root Hook任意App的Java代码。

Hook框架底层使用的是Lody的whale，支持的平台架构有：ARM/THUMB、ARM64，支持的android版本大致有（其他未测试）：android 5.0-android 9.0

## Xpatch工具包下载
[点击我下载最新的Xpatch Jar包][1]  
或者进入Releases页面下载：[releases][2]

## Xpatch使用方法
Xpatch项目最终生成物是一个Jar包，此Jar使用起来非常简单，只需要一行命令，一个接入xposed hook功能的apk就生成：
```
$ java -jar XpatchJar包路径 apk文件路径

For example:
$ java -jar ../../xpatch.jar ../../wechat.apk
```

这条命令之后，在原apk文件(wechat.apk)相同的文件夹中，会生成一个名称为`wechat-xposed-signed.apk`的新apk，这就是重新签名之后的支持xposed插件的apk。

**Note:** 由于签名与原签名不一致，因此需要先卸载掉系统上已经安装的原apk，才能安装这个Xpatch后的apk

当然，也可以增加`-o`参数，指定新apk生成的路径：
```
$ java -jar ../../xpatch.jar ../../wechat.apk -o ../../new-wechat.apk
```

更多参数类型可以使用--help查看，或者不输入任何参数运行jar包：
```
$ java -jar ../../xpatch.jar --help(可省略)
```
这行命令之后得到结果：
```
Please choose one apk file you want to process. 
options:
 -f,--force                   force overwrite
 -h,--help                    Print this help message
 -k,--keep                    not delete the jar file that is changed by dex2jar
                               and the apk zip files
 -l,--log                     show all the debug logs
 -o,--output <out-apk-file>   output .apk file, default is $source_apk_dir/[file
                              -name]-xposed-signed.apk
```
如果觉得每次命令都要输入`java -jar xpatch.jar`太麻烦，也可以将xpatch.jar的文件目录加入到系统环境变量里，这样，每次只需输入xpatch即可，关于加入环境变量的方法，可以参考apktool文件中的做法：
[Apktool install instructions][3]

----
### 2019/4/15 updated  
增加自动破解签名的功能，此功能默认开启，如果需要关闭可以增加`-c`即可，比如：  
```
$ java -jar ../../xpatch.jar ../../wechat.apk -c
```  
通过help可以查看到:  
>options:  
> -c,--crach                   disable craching the apk's signature.
----
----
### 2019/4/25 updated
增加将Xposed modules打包到apk中的功能
通过help可以查看到: 
 >-xm,--xposed-modules <arg>   the xposed mpdule files to be packaged into the ap
 >                            k, multi files should be seperated by :(mac) or ;(
 >                             win) 

使用方式为在命令后面增加`-xm path`即可，比如：
```
$ java -jar ../../xpatch.jar ../../source.apk -xm ../../module1.apk
```
假如需要将多个Xposed插件打包进去，在Mac中使用":"，在Windows下使用";"，隔开多个文件路径即可，比如：
```
mac
$  java -jar ../../xpatch.jar ../../source.apk -xm ../../module1.apk:../../module2.apk  

windows
$  java -jar ../../xpatch.jar ../../source.apk -xm ../../module1.apk;../../module2.apk
```

**注意：**
1. 多个Xposed modules使用`:`(mac)/`;`(win)分割;
2. 假如此module既被打包到apk中，又安装在设备上，则只会加载打包到apk中的module，不会加载安装的。
这里是通过包名区分是不是同一个module。

----


## Xposed模块加载方法
当新apk安装到系统之后，应用启动时，默认会加载所有已安装的Xposed插件(Xposed Module)。

一般情况下，Xposed插件中都会对包名过滤，有些Xposed插件有界面，并且在界面上可以设置开关，所以默认启用所有的Xposed插件的方式，大多数情形下都可行。

但在少数情况可能会出现问题，比如，同一个应用安装有多个Xposed插件（wechat插件就非常多），并且都没有独立的开关界面，同时启用这些插件可能会产生冲突。

为了解决此问题，当应用启动时，会查找系统中所有已安装的Xposed插件，并在文件目录下生成一个文件
`mnt/sdcard/xposed_config/modules.list`，记录这些Xposed插件App。
比如：
```
com.blanke.mdwechat#MDWechat

com.gh0u1l5.wechatmagician#微信巫师

com.example.wx_plug_in3#畅玩微信

liubaoyua.customtext#文本自定义
```
记录的方式是：`插件app包名#插件app名称`

需要禁用某个插件，只需要修改此文件，在该插件包名前面增加一个`#`号即可。

比如，需要禁用`畅玩微信`和`文本自定义`两个插件，只需要修改该文本文件，增加一个`#`号即可：

```
com.blanke.mdwechat#MDWechat

com.gh0u1l5.wechatmagician#微信巫师

#com.example.wx_plug_in3#畅玩微信

#liubaoyua.customtext#文本自定义
```
如果需要禁用所有插件，只需在所有的包名前面增加`#`。
It's so easy !!!


**Note:**
有些App没有获取到sd卡文件读写权限，这会导致modules.list配置文件读写失败，此时会默认启用所有插件。可手动开启app的文件读写权限，避免这种情况发生。


## 可用的Xposed模块示例

 - [腾讯视频，爱奇艺去广告插件-RemoveVideoAdsPlugin](https://github.com/WindySha/RemoveVideoAdsPlugin)
 - [MDWechat][8]
 - [文本自定义][9]
 - ...
 - ...
 - **你自己编写的Xposed模块**
 
**Note：一般来说，只要app可以被Xpatch破解，并且运行时没有做签名校验，与其相关的Xposed模块都是可用的。**

## 可破解的App示例
 - 微信
 - 今日头条
 - 腾讯视频
 - 天天快报
 - 爱奇艺
 - Subway Surf
 - ...
 - **其他App**
 
## Todo

 1. 自动破解app签名  （Finished on 2019.4.15）
 2. 支持xposed插件直接打包到apk中  （Finished on 2019.4.25）
 3. 支持xposed插件中so文件的加载
 ...
 
敬请期待....

## 源码解析
Xpatch源码解析博文已发布到个人技术公众号**Android葵花宝典**上。  
扫一扫即可查看：  
![](https://upload-images.jianshu.io/upload_images/1639238-ab6e0fceabfffdda.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/180)

## Issues
Xpatch是基于apk二次打包实现的，而且使用到了dex2Jar工具，因此，也存在不少的局限性。大概有以下几点：

1. 对于使用了签名校验的应用，使用Xpatch得到的apk可能无法启动，或者无法获取到网络数据，比如优酷，趣头条等。不过，这种问题并不是致命性问题，既然app启动时可以加载xposed插件，那我们可以编写一个hook获取签名的方法的xposed插件，从而使校验签名能够顺利通过。具体实施细节稍后会在个人微信技术号上公开，欢迎关注：**Android葵花宝典**。  
2. 有些app可能做了app加固，导致dex2Jar工具无法将dex文件解析为jar包，从而无法生成新的apk。这种问题暂时还无法解决。  
3. hook框架使用的是lody的[Whale框架][5]，此框架存在一些不稳定性，对少数方法的hook会导致崩溃，并且在某些机型上hook也会崩溃。  
4. Xposed Hook框架暂时不支持Dalvik虚拟机。  
5. 暂时不支持Xposed插件中的资源Hook。
 
## Technology Discussion
**QQ Group: 977513757**  
or  
**Post comments under this article: [Xpatch: 免Root实现App加载Xposed插件的一种方案](https://windysha.github.io/2019/04/18/Xpatch-%E5%85%8DRoot%E5%AE%9E%E7%8E%B0App%E5%8A%A0%E8%BD%BDXposed%E6%8F%92%E4%BB%B6%E7%9A%84%E4%B8%80%E7%A7%8D%E6%96%B9%E6%A1%88/)** 

## Thanks

 - [Xposed][10]
 - [whale][11]
 - [dex2jar][12]
 - [AXMLPrinter2][13]

  [1]: https://github.com/WindySha/Xpatch/releases/download/v1.3/xpatch-1.3.zip
  [2]: https://github.com/WindySha/Xpatch/releases
  [3]: https://ibotpeaches.github.io/Apktool/install/
  [5]: https://github.com/asLody/whale
  [6]: https://repo.xposed.info/module/com.example.wx_plug_in3
  [7]: https://github.com/Gh0u1L5/WechatMagician/releases
  [8]: https://github.com/Blankeer/MDWechat
  [9]: https://repo.xposed.info/module/liubaoyua.customtext
  [10]: https://github.com/rovo89/Xposed
  [11]: https://github.com/asLody/whale
  [12]: https://github.com/pxb1988/dex2jar
  [13]: https://code.google.com/archive/p/android4me/downloads
  [14]: http://www.apache.org/licenses/LICENSE-2.0.html
  
