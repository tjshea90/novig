# Vigilant R8 rules. kotlinx.serialization and OkHttp ship their own consumer rules; these cover
# what they can't know about.

# Settings and tracked bets are persisted as JSON. Keep the @Serializable classes' generated
# serializers and the enums they store by name, so an update can always read an older file.
-keep,includedescriptorclasses class com.tjshea.vigilant.**$$serializer { *; }
-keepclassmembers class com.tjshea.vigilant.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum com.tjshea.vigilant.** { *; }

# OkHttp's optional platform integrations.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Readable crash stacks (Tj sends Diagnostics files to Claude; 2026-10-02 the file's only crash read "at n5.l.E0(…0c73:6) | at y4.f1.j(…)",
# which no one can map to code): the app's own classes and methods keep their names, and every frame its file and line. Shrinking and
# optimizing still run; only the renaming of Vigilant's own code is off (APK 6.9 -> 7.9 MB). Each Release also carries the build's
# mapping.txt.gz for exact lines (R8 may inline): `retrace mapping.txt stack.txt`.
-keepattributes SourceFile,LineNumberTable
-keepnames class com.tjshea.vigilant.** { *; }
