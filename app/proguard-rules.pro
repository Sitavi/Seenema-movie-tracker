# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class sitavi.seenema.**$$serializer { *; }
-keepclassmembers class sitavi.seenema.** {
    *** Companion;
}
-keepclasseswithmembers class sitavi.seenema.** {
    kotlinx.serialization.KSerializer serializer(...);
}
