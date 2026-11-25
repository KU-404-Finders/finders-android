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

# Gson reflects over wire DTOs. Keep their classes, field names and constructors
# so R8 cannot make response-only types abstract or merge away nested types.
# Retrofit's consumer rules retain service signatures and HTTP annotations.
-keepattributes Signature
-keep,allowobfuscation class com.ku.lostandfound.network.*Response,com.ku.lostandfound.network.*Data,com.ku.lostandfound.network.*Result { <init>(...); }
-keep,allowobfuscation class com.ku.lostandfound.network.ApiResponse,com.ku.lostandfound.network.AdminDashboard,com.ku.lostandfound.network.AdminPage,com.ku.lostandfound.network.AdminReport,com.ku.lostandfound.network.AdminUser,com.ku.lostandfound.network.AdminPost,com.ku.lostandfound.network.AdminAudit,com.ku.lostandfound.network.ChatReadReceipt,com.ku.lostandfound.network.ReportReasonOption { <init>(...); }

# Request models are constructed by app code. Gson still requires their field names.
-keepclassmembers class com.ku.lostandfound.network.*Request,com.ku.lostandfound.network.NotificationSettingsPatch { <fields>; }

# Gson maps wire values to enum constant names.
-keepclassmembers enum com.ku.lostandfound.network.FoundItemLocationType,com.ku.lostandfound.network.ItemStatus,com.ku.lostandfound.network.MatchStatus { public static final ** *; }
-keepclassmembers class com.ku.lostandfound.network.*Response,com.ku.lostandfound.network.*Data,com.ku.lostandfound.network.*Result,com.ku.lostandfound.network.ApiResponse,com.ku.lostandfound.network.AdminDashboard,com.ku.lostandfound.network.AdminPage,com.ku.lostandfound.network.AdminReport,com.ku.lostandfound.network.AdminUser,com.ku.lostandfound.network.AdminPost,com.ku.lostandfound.network.AdminAudit,com.ku.lostandfound.network.ChatReadReceipt,com.ku.lostandfound.network.ReportReasonOption { <fields>; }
