# No custom rules needed: the app uses no reflection, serialization or JNI of its own.
# Compose, AndroidX Browser and Core ship their own consumer rules. Release builds are
# minified and resource-shrunk (see app/build.gradle.kts); mapping.txt is in build/outputs/mapping/release/.

# LiteRT-LM (on-device LLM, llm/): kept whole as a precaution. The AAR ships no consumer rules and its native (JNI)
# code may look classes and methods up by name, which minification would break. Narrow this once a minified build
# has been tested on a device (unverified: no claim here about which classes are actually needed).
-keep class com.google.ai.edge.litertlm.** { *; }
