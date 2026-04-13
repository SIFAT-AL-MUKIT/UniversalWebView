# WebView JavaScript Interface
-keepclassmembers class com.webviewapp.template.MainActivity$WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.webviewapp.template.MainActivity$PrintInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep download helper
-keep class com.webviewapp.template.DownloadHelper { *; }
