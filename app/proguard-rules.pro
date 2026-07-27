# Canvas Studio uses no reflection-based serialization. Keep only source/line metadata
# so release crash reports remain actionable while R8 optimizes the app.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
