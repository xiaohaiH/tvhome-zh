# Keep release optimisation/shrinking, but make photographed app frames interpretable.
-keepattributes SourceFile,LineNumberTable
-keep,allowoptimization,allowshrinking class com.gathin.tvhome.** { *; }
