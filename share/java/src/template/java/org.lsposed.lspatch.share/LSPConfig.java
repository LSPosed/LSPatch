package org.lsposed.lspatch.share;

public class LSPConfig {

    public static final LSPConfig instance;

    public int API_CODE;
    public int VERSION_CODE;
    public String VERSION_NAME;
    public int CORE_VERSION_CODE;
    public String CORE_VERSION_NAME;

    private LSPConfig() {
    }

    static {
        instance = new LSPConfig();
        instance.API_CODE = ${apiCode};
        instance.VERSION_CODE = ${verCode};
        instance.VERSION_NAME = "${verName}";
        instance.CORE_VERSION_CODE = ${coreVerCode};
        instance.CORE_VERSION_NAME = "${coreVerName}";
    }
}
