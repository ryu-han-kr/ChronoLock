# ChronoLock R8 rules. Libraries (Room, Hilt, Compose, Billing, Ads, Credentials) ship their own consumer rules.

# Keep readable stack traces in Play's crash reports; Play deobfuscates with the uploaded mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Stored as names in preferences and backups (enum.valueOf / name); keep the constant names stable.
-keepclassmembers enum com.chronolock.** { <fields>; }
