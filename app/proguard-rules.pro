# Preserve line numbers for Crashlytics stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve App Data Models & Entities for JSON/Database serialization
-keep class com.mudasir.todoapp.data.** { *; }

# Firebase & Google Credential Manager
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**