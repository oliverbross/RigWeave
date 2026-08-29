# RigWeave's native bridge exports JNI symbols by their Kotlin class and method names.
# Preserve only classes that actually declare native methods; library consumer rules
# continue to own their own reflection and resource requirements.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
