package org.lsposed.lspatch.share;

public class PatchConfig {

    public final boolean useManager;
    public final boolean debuggable;
    public final boolean overrideVersionCode;
    public final int sigBypassLevel;
    public final String originalSignature;
    public final String appComponentFactory;
    public final String zygotePreloadName;
    public final LSPConfig lspConfig;

    public PatchConfig(
            boolean useManager,
            boolean debuggable,
            boolean overrideVersionCode,
            int sigBypassLevel,
            String originalSignature,
            String appComponentFactory,
            String zygotePreloadName
    ) {
        this.useManager = useManager;
        this.debuggable = debuggable;
        this.overrideVersionCode = overrideVersionCode;
        this.sigBypassLevel = sigBypassLevel;
        this.originalSignature = originalSignature;
        this.appComponentFactory = appComponentFactory;
        this.zygotePreloadName = zygotePreloadName;
        this.lspConfig = LSPConfig.instance;
    }
}
