package org.lsposed.lspatch.share;

public class LSPConfig {

    public static final LSPConfig instance = new LSPConfig();

    public final int API_CODE = ${apiCode};
    public final int VERSION_CODE = ${verCode};
    public final String VERSION_NAME = "${verName}";
    public final int CORE_VERSION_CODE = ${coreVerCode};
    public final String CORE_VERSION_NAME = "${coreVerName}";

    private LSPConfig() {
    }
}
