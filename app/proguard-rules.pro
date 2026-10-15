# Libraries used here (Room, kotlinx-serialization, Play services ads/billing/review, WorkManager)
# ship their own consumer keep rules. Add app-specific rules below only when needed.

# Keep serializers for the saved puzzle states (looked up via generated companions).
-keepclassmembers @kotlinx.serialization.Serializable class com.tashlark.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
