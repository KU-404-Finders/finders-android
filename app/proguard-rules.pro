# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Retrofit retains service methods from their HTTP annotations. Gson, however,
# reads DTO fields reflectively. Keep only the fields of the request/response
# shapes used by the API; classes themselves may still be optimized/renamed.
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.*Request { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.*Response { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.*Data { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.*Result { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.ApiResponse { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.AdminDashboard { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.AdminPage { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.AdminReport { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.AdminUser { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.AdminPost { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.AdminAudit { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.ChatRoomResponse { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.ChatMessageResponse { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.ChatReadReceipt { <fields>; }
-keepclassmembers,allowoptimization class com.ku.lostandfound.network.ChatReadStateResponse { <fields>; }
