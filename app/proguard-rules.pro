# Preserve JNI bridge & native synthesis callback methods
-keep class io.github.ultrapg.voicemobile.engine.VoiceEngineBridge { *; }
-keep class io.github.ultrapg.voicemobile.engine.NativeSynthCallback { *; }
-keepclasseswithmembers class * implements io.github.ultrapg.voicemobile.engine.NativeSynthCallback {
    public void onProgress(int, int);
    public boolean onAudioChunk(float[], int);
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.ultrapg.voicemobile.**$$serializer { *; }
-keepclassmembers class io.github.ultrapg.voicemobile.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.ultrapg.voicemobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
