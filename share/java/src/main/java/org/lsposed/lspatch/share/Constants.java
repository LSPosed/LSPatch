package org.lsposed.lspatch.share;

public class Constants {

    final static public String CONFIG_ASSET_PATH = "assets/lspatch/config.json";
    final static public String LOADER_DEX_ASSET_PATH = "assets/lspatch/loader.dex";
    final static public String META_LOADER_DEX_ASSET_PATH = "assets/lspatch/metaloader.dex";
    final static public String ORIGINAL_APK_ASSET_PATH = "assets/lspatch/origin.apk";
    final static public String EMBEDDED_MODULES_ASSET_PATH = "assets/lspatch/modules/";

    final static public String PATCH_FILE_SUFFIX = "-lspatched.apk";
    final static public String PROXY_APP_COMPONENT_FACTORY = "org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub";
    final static public String MANAGER_PACKAGE_NAME = "org.lsposed.lspatch";
    final static public int MIN_ROLLING_VERSION_CODE = 348;

    final static public int SIGBYPASS_LV_DISABLE = 0;
    final static public int SIGBYPASS_LV_PM = 1;
    final static public int SIGBYPASS_LV_PM_OPENAT = 2;
    final static public int SIGBYPASS_LV_MAX = 3;
}
