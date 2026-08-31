# kotlinx.serialization keeps its generated serializers on the type itself.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class np.bill.** {
    *** Companion;
}
-keepclasseswithmembers class np.bill.** {
    kotlinx.serialization.KSerializer serializer(...);
}
