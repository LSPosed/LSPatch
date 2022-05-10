package org.lsposed.lspatch.share;

public class LSPConfig {

    public static final LSPConfig instance = factory();

    public int API_CODE;
    public int VERSION_CODE;
    public String VERSION_NAME;
    public int CORE_VERSION_CODE;
    public String CORE_VERSION_NAME;

    private LSPConfig() {
    }

    private static LSPConfig factory() {
        LSPConfig config = new LSPConfig();
        config.API_CODE = ${apiCode};
        config.VERSION_CODE = ${verCode};
        config.VERSION_NAME = "${verName}";
        config.CORE_VERSION_CODE = ${coreVerCode};
        config.CORE_VERSION_NAME = "${coreVerName}";
        return config;
    }
}
