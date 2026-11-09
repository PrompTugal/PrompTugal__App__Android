# No custom rules needed: the app uses no reflection, serialization or JNI of its own.
# Compose, AndroidX Browser and Core ship their own consumer rules. Release builds are
# minified and resource-shrunk (see app/build.gradle.kts); mapping.txt is in build/outputs/mapping/release/.

# LiteRT-LM (on-device LLM, llm/): called from its JNI library and uses reflection for tools; ships no consumer rules.
-keep class com.google.ai.edge.litertlm.** { *; }
