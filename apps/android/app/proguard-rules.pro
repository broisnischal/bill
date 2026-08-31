# kotlinx.serialization keeps its generated serializers on the type itself.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class np.bill.** {
    *** Companion;
}
-keepclasseswithmembers class np.bill.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The synthetic `$$serializer` the compiler plugin generates beside every @Serializable
# type. Nothing in the source names it, so R8 treats it as unreachable and strips it,
# and the response decodes to defaults instead of failing loudly: the app then reads a
# null store and asks the shopkeeper to register a business that already exists.
-keep,includedescriptorclasses class np.bill.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class np.bill.** {
    <fields>;
}
