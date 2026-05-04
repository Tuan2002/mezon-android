-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}

-keep class com.mezon.mezon.api.** { *; }
-keep class com.mezon.mezon.rtapi.** { *; }
-dontwarn com.google.protobuf.**
