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
