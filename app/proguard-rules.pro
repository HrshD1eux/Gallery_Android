# Add project specific ProGuard rules here.
# By default, the ProGuard rules file is configured in build.gradle.kts.

# Compose
-dontwarn androidx.compose.**
-keepclassmembers class * extends androidx.compose.ui.node.LayoutNode { *; }
-keep class androidx.compose.runtime.snapshots.** { *; }
-dontwarn androidx.compose.runtime.snapshots.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *
-keep class * implements androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Hilt & Dagger
-keep class * extends android.app.Application
-keep class * extends androidx.lifecycle.ViewModel
-keep class **_HiltModules* { *; }
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
    @javax.inject.Inject <init>(...);
}

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Media3 / ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# Data Models & Entities
-keep class com.HrshD1eux.Gallery.data.model.** { *; }
-keep class com.HrshD1eux.Gallery.data.database.** { *; }
