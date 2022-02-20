-keep class com.beust.jcommander.** { *; }
-keep class org.lsposed.lspatch.Patcher$Options { *; }
-keepclassmembers class org.lsposed.patch.LSPatch {
    private <fields>;
}
