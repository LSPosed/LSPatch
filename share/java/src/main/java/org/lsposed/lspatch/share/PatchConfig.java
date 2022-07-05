package org.lsposed.lspatch.share;

public class PatchConfig {

    public final boolean useManager;
    public final boolean debuggable;
    public final boolean overrideVersionCode;
    public final boolean v1;
    public final boolean v2;
    public final int sigBypassLevel;
    public final String originalSignature;
    public final String appComponentFactory;
    public final LSPConfig lspConfig;

    public PatchConfig(
            boolean useManager,
            boolean debuggable,
            boolean overrideVersionCode,
            boolean v1,
            boolean v2,
            int sigBypassLevel,
            String originalSignature,
            String appComponentFactory
    ) {
        this.useManager = useManager;
        this.debuggable = debuggable;
        this.overrideVersionCode = overrideVersionCode;
        this.v1 = v1;
        this.v2 = v2;
        this.sigBypassLevel = sigBypassLevel;
        this.originalSignature = originalSignature;
        this.appComponentFactory = appComponentFactory;
        this.lspConfig = LSPConfig.instance;
    }
}
