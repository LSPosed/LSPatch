package org.lsposed.lspatch.share;

public class PatchConfig {

    public final boolean useManager;
    public final int sigBypassLevel;
    public final String originalSignature;
    public final String appComponentFactory;
    public final LSPConfig lspConfig;

    public PatchConfig(boolean useManager, int sigBypassLevel, String originalSignature, String appComponentFactory) {
        this.useManager = useManager;
        this.sigBypassLevel = sigBypassLevel;
        this.originalSignature = originalSignature;
        this.appComponentFactory = appComponentFactory;
        this.lspConfig = LSPConfig.instance;
    }
}
