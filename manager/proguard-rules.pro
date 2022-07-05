-keep class com.beust.jcommander.** { *; }
-keep class org.lsposed.lspatch.Patcher$Options { *; }
-keep class org.lsposed.lspatch.share.LSPConfig { *; }
-keep class org.lsposed.lspatch.share.PatchConfig { *; }
-keepclassmembers class org.lsposed.patch.LSPatch {
    private <fields>;
}
