-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
 public static void check*(...);
 public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}
-assumenosideeffects public class kotlin.coroutines.jvm.internal.DebugMetadataKt {
   private static ** getDebugMetadataAnnotation(...) return null;
}

-keep class com.beust.jcommander.** { *; }
-keep class org.lsposed.lspatch.Patcher$Options { *; }
-keep class org.lsposed.lspatch.share.LSPConfig { *; }
-keep class org.lsposed.lspatch.share.PatchConfig { *; }
-keepclassmembers class org.lsposed.patch.LSPatch {
    private <fields>;
}
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
