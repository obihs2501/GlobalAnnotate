# Keep the LSPosed entry class and everything it reaches reflectively.
-keep class com.globalannotate.xposed.** { *; }
-keepnames class com.globalannotate.xposed.GlobalAnnotateHook { *; }
