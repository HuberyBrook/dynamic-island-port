# Xposed modules must not obfuscate entry classes
-keep class com.hubery.dynamicislandport.HookEntry { *; }
-keep class com.hubery.dynamicislandport.** { *; }
