package org.lsposed.lspatch.share;

public class Constants {
    final static public String ORIGINAL_APK_ASSET_PATH = "assets/origin_apk.bin";
    final static public String ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH = "assets/original_app_component_factory.ini";

    final static public String PROXY_APP_COMPONENT_FACTORY = "org.lsposed.lspatch.appstub.LSPAppComponentFactoryStub";
    final static public String MANAGER_PACKAGE_NAME = "org.lsposed.lspatch";
    final static public String CONFIG_NAME_SIGBYPASSLV = "lspatch_sigbypasslv";
    final static public int SIGBYPASS_LV_DISABLE = 0;
    final static public int SIGBYPASS_LV_PM = 1;
    final static public int SIGBYPASS_LV_PM_OPENAT = 2;
    final static public int SIGBYPASS_LV_MAX = 3;
}
