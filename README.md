### [**English Version**](https://github.com/WindySha/Xpatch/blob/6ec0f3c16128dda46ab05bdd915d66ebbdaaf9fc/README_en.md)

# Android App破解工具Xpatch的使用方法

## Xpatch概述
Xpatch用来重新签名打包Apk文件，使重打包后的Apk能加载安装在系统里的Xposed插件，从而实现免Root Hook任意App。

## Xpatch基本原理
Xpatch的原理是对Apk文件进行二次打包，重新签名，并生成一个新的apk文件。
在Apk二次打包过程中，插入加载Xposed插件的逻辑，这样，新的Apk文件就可以加载任意Xposed插件，从而实现免Root Hook任意App的Java代码。

1.0~1.4版本，Hook框架使用的是Lody的[whale](https://github.com/asLody/whale)    
2.0版本开始，Hook框架底层使用的是ganyao114的[SandHook](https://github.com/ganyao114/SandHook)。  
3.0版本开始，默认使用SandHook，同时，兼容切换为whale

## Xpatch工具包下载
[点击我下载最新的Xpatch Jar包][1]    
或者进入Releases页面下载指定版本：[releases][2]

## Xpatch App版本(Xposed Tool)下载
[点我下载XposedTool Apk][15]

## Xpatch使用方法
Xpatch项目最终生成物是一个Jar包，此Jar使用起来非常简单，只需要一行命令，一个接入xposed hook功能的apk就生成：
```
$ java -jar XpatchJar包路径 apk文件路径

For example:
$ java -jar ../xpatch.jar ../Test.apk
```

这条命令之后，在原apk文件(Test.apk)相同的文件夹中，会生成一个名称为`Test-xposed-signed.apk`的新apk，这就是重新签名之后的支持xposed插件的apk。

**Note:** 由于签名与原签名不一致，因此需要先卸载掉系统上已经安装的原apk，才能安装这个Xpatch后的apk

当然，也可以增加`-o`参数，指定新apk生成的路径：
```
$ java -jar ../xpatch.jar ../Test.apk -o ../new-Test.apk
```

更多参数类型可以使用--help查看，或者不输入任何参数运行jar包：
```
$ java -jar ../xpatch.jar --h(可省略)
```
这行命令之后得到结果(v1.0-v2.0)：
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

## Xposed模块开关控制的两种方法
### 1. 手动修改sdcard文件控制模块开关
当新apk安装到系统之后，应用启动时，默认会加载所有已安装的Xposed插件(Xposed Module)。

一般情况下，Xposed插件中都会对包名过滤，有些Xposed插件有界面，并且在界面上可以设置开关，所以默认启用所有的Xposed插件的方式，大多数情形下都可行。

但在少数情况可能会出现问题，比如，同一个应用安装有多个Xposed插件（wechat插件就非常多），并且都没有独立的开关界面，同时启用这些插件可能会产生冲突。

为了解决此问题，当应用启动时，会查找系统中所有已安装的Xposed插件，并在文件目录下生成一个文件
`mnt/sdcard/xposed_config/modules.list`，记录这些Xposed插件App。
比如：
```
com.blanke.mdwechat#MDWechat
com.example.wx_plug_in3#畅玩微信
liubaoyua.customtext#文本自定义
```
记录的方式是：`插件app包名#插件app名称`

需要禁用某个插件，只需要修改此文件，在该插件包名前面增加一个`#`号即可。

比如，需要禁用`畅玩微信`和`文本自定义`两个插件，只需要修改该文本文件，增加一个`#`号即可：
```
com.blanke.mdwechat#MDWechat
#com.example.wx_plug_in3#畅玩微信
#liubaoyua.customtext#文本自定义
```
如果需要禁用所有插件，只需在所有的包名前面增加`#`。

**注意:**
有些App没有获取到sd卡文件读写权限，这会导致无法读取modules.list配置文件，此时会默认启用所有插件。这种情况下，需要手动打开app的文件读写权限。
### 2. 通过Xposed Tool App控制模块开关
下载并安装Xpatch App（Xposed Tool）
[点我下载XposedTool Apk][15]
通过`Xposed模块管理`页面来控制模块开关。（原理跟方法1一致）  
![Screenshot.png](https://upload-images.jianshu.io/upload_images/1639238-84d7a1dd814f314a.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/300)

## 可用的Xposed模块示例

 - [腾讯视频，爱奇艺去广告插件-RemoveVideoAdsPlugin](https://github.com/WindySha/RemoveVideoAdsPlugin)
 - [MDWechat][8]
 - [文本自定义][9]
 - ...
 - ...
 - **你自己编写的Xposed模块**
 
**Note：一般来说，只要app可以被Xpatch破解，与其相关的Xposed模块都是可用的。**


## 源码解析
Xpatch源码解析博文已发布到个人技术公众号**Android葵花宝典**上。  
扫一扫关注公众号，即可查看：  
![](https://upload-images.jianshu.io/upload_images/1639238-ab6e0fceabfffdda.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/180)

## 其他
assets目录下的classes.dex是来加载设备上已安装的Xposed插件，其源代码也已经开源：  
[xposed_module_loader](https://github.com/WindySha/xposed_module_loader)  
欢迎star and fork.

## 局限性
Xpatch是基于apk二次打包实现的，而且使用到了dex2Jar工具，因此，也存在不少的局限性。大概有以下几点：
 
1. Hook框架默认使用的是SandHook，此框架存在一些不稳定性，在少数机型上hook可能会崩溃。  
2. 对于校验了文件完整性的app，重打包后可能无法启动；
3. Xposed Hook框架暂时不支持Dalvik虚拟机。  
4. 暂时不支持Xposed插件中的资源Hook。
 
## Technology Discussion
**QQ Group: 977513757**  
or  
**Post comments under this article: [Xpatch: 免Root实现App加载Xposed插件的一种方案](https://windysha.github.io/2019/04/18/Xpatch-%E5%85%8DRoot%E5%AE%9E%E7%8E%B0App%E5%8A%A0%E8%BD%BDXposed%E6%8F%92%E4%BB%B6%E7%9A%84%E4%B8%80%E7%A7%8D%E6%96%B9%E6%A1%88/)** 

## 功能更新

----
### 1. 2019/4/15 updated  
增加自动破解签名检验的功能，此功能默认开启，如果需要关闭可以增加`-c`即可，比如：  
```
$ java -jar ../xpatch.jar ../Test.apk -c
```  
通过help(-h)可以查看到:  
>options:  
> -c,--crach                   disable craching the apk's signature.

### 2. 2019/4/25 updated
增加将Xposed modules打包到apk中的功能
通过help(-h)可以查看到: 
 >-xm,--xposed-modules <arg>   the xposed mpdule files to be packaged into the ap
 >                            k, multi files should be seperated by :(mac) or ;(
 >                             win) 

使用方式为在命令后面增加`-xm path`即可，比如：
```
$ java -jar ../xpatch.jar ../source.apk -xm ../module1.apk
```
假如需要将多个Xposed插件打包进去，在Mac中使用":"，在Windows下使用";"，隔开多个文件路径即可，比如：
```
mac
$  java -jar ../xpatch.jar ../source.apk -xm ../module1.apk:../module2.apk  

windows
$  java -jar ../xpatch.jar ../source.apk -xm ../module1.apk;../module2.apk
```

**注意：**
1. 多个Xposed modules使用`:`(mac)/`;`(win)分割;
2. 假如此module既被打包到apk中，又安装在设备上，则只会加载打包到apk中的module，不会加载安装的。
这里是通过包名区分是不是同一个module。

----

### 3. 2020/02/09 updated  (Version 3.0)
3.0版本增加了不少新功能，同时修复了一些用户反馈的Bug。

新增功能：
1. 支持android 10;
2. 支持更改植入的hook框架，默认使用Sandhook(支持android10)，可更改为whale(暂不支持android10)(-w);
3. 默认使用修改Maniest文件方式，植入初始化代码，同时，支持更改为老版本中的修改dex文件的方式植入代码(-dex)；
4. 支持修改apk包名（一般不建议使用，很多应用会校验包名，会导致无法使用）
5. 支持修改apk的version code;
6. 支持修改apk的version name;
7. 支持修改apk的debuggable为true或者false;

Bug修复：
1. 修复Manifest文件中未定义ApplicationName类，导致无法实现Hook的问题;
2. 修复破解无so文件的apk时，出现无法找到so的问题;
3. 修复签名可能会失败的问题；
4. 修复dex文件数超过65536的问题；

#### 新功能用法
在命令行中输入-h，可以看到3.0版本完整的帮助文档：
`$ java -jar ../xpatch-3.0.jar -h`
```
options:
 -c,--crach                   disable craching the apk's signature.
 -d,--debuggable <0 or 1>     set 1 to make the app debuggable = true, set 0 to 
                              make the app debuggable = false
 -dex,--dex                   insert code into the dex file, not modify manifest
                               application name attribute
 -f,--force                   force overwrite
 -h,--help                    Print this help message
 -k,--keep                    not delete the jar file that is changed by dex2jar
                               and the apk zip files
 -l,--log                     show all the debug logs
 -o,--output <out-apk-file>   output .apk file, default is $source_apk_dir/[file
                              -name]-xposed-signed.apk
 -pkg,--packageName <new package name>modify the apk package name
 -vc,--versionCode <new-version-code>set the app version code
 -vn,--versionName <new-version-name>set the app version name
 -w,--whale                   Change hook framework to Lody's whale
 -xm,--xposed-modules <xposed module file path>
                              the xposed module files to be packaged into the ap
                              k, multi files should be seperated by :(mac) or ;(
                              win) 
version: 3.0
```
具体用法：
1. 修改Apk的debuggable = true：  
 `$ java -jar ../xpatch-3.0.jar ../Test.apk -d 1` （false改为0）
2. 使用老版本的破解dex方法破解apk：  
`$ java -jar ../xpatch-3.0.jar ../Test.apk  -dex`
3. 修改包名，版本号：  
`$ java -jar ../xpatch-3.0.jar ../Test.apk  -pkg com.test.test -vc 1000 -vn 1.1.1`
2. 更改Hook框架为whale：  
`$ java -jar ../xpatch-3.0.jar ../Test.apk  -w`
## Thanks

 - [Xposed][10]
 - [whale][11]
 - [dex2jar][12]
 - [AXMLPrinter2][13]
 - [SandHook](https://github.com/ganyao114/SandHook)
 - [xposed_module_loader](https://github.com/WindySha/xposed_module_loader) 
 - [axml](https://github.com/Sable/axml)

  [1]: https://github.com/WindySha/Xpatch/releases/download/v3.0/xpatch-3.0.jar
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
  [15]: https://xposed-tool-app.oss-cn-beijing.aliyuncs.com/data/xposed_tool_v2.0.2.apk
  
