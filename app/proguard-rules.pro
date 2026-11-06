# Keep runtime annotations and generic signatures used by AndroidX and Room.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Android components are referenced by the manifest and are retained by the Android Gradle Plugin.
# Room implementation classes are generated and referenced by Room's generated database implementation.
