# kotlinx.serialization -----------------------------------------------------
# Keep the generated serializers for our DTOs; without this, release builds
# fail at runtime when reading or writing app-data JSON.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.amteen.paisa.**$$serializer { *; }
-keepclassmembers class com.amteen.paisa.** {
    *** Companion;
}
-keepclasseswithmembers class com.amteen.paisa.** {
    kotlinx.serialization.KSerializer serializer(...);
}
