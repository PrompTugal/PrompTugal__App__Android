# The app's own code uses no reflection, serialization or JNI. Compose, AndroidX Browser and Core ship their
# own consumer rules. LiteRT-LM (llm/) does use JNI and reflection and ships none, so it is kept below. Release
# builds are minified and resource-shrunk (see app/build.gradle.kts); mapping.txt is in build/outputs/mapping/release/.

# LiteRT-LM (on-device LLM, llm/): called from its JNI library and uses reflection for tools; ships no consumer rules.
-keep class com.google.ai.edge.litertlm.** { *; }
