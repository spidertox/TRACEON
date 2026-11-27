# Entities are serialized for backup/restore.
-keep class com.traceon.data.local.entity.** { *; }
-keepclassmembers class com.traceon.** { *** Companion; }
