package com.webviewapp.template;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WebViewApp";

    // ============================================================
    //  ⭐ এখানে তোমার URL বসাও
    //  Local HTML: "file:///android_asset/www/index.html"
    //  Online:     "https://example.com"
    // ============================================================
    private static final String HOME_URL = "file:///android_asset/www/index.html";
    private static final int SPLASH_DELAY = 2000; // 2 seconds
    // ============================================================

    private WebView webView;
    private LinearProgressIndicator progressBar;
    private LinearLayout errorLayout;
    private LinearLayout splashLayout;
    private CoordinatorLayout mainContent;
    private FrameLayout fullscreenContainer;
    private MaterialButton btnRetry;

    private DownloadHelper downloadHelper;

    // File Upload
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;

    // Fullscreen Video
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int originalSystemUiVisibility;

    // Launchers
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    private boolean splashDone = false;
    private boolean pageLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Splash Screen API (Android 12+)
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Full screen (hide status bar)
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        initViews();
        initLaunchers();
        checkPermissions();
        setupWebView();

        downloadHelper = new DownloadHelper(this, webView);

        loadHomePage();

        // Splash delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            splashDone = true;
            if (pageLoaded) showMainContent();
        }, SPLASH_DELAY);
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorLayout = findViewById(R.id.errorLayout);
        splashLayout = findViewById(R.id.splashLayout);
        mainContent = findViewById(R.id.mainContent);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        btnRetry = findViewById(R.id.btnRetry);

        btnRetry.setOnClickListener(v -> {
            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            loadHomePage();
        });
    }

    private void showMainContent() {
        splashLayout.setVisibility(View.GONE);
        mainContent.setVisibility(View.VISIBLE);
    }

    private void initLaunchers() {
        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (fileUploadCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        String dataString = data.getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        } else if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = data.getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                    if (results == null && cameraImageUri != null) {
                        results = new Uri[]{cameraImageUri};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            });

        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {});
    }

    private void checkPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.CAMERA);
        if (!perms.isEmpty())
            permissionLauncher.launch(perms.toArray(new String[0]));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();

        // Core
        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // File Access
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        // Display
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(true);

        // Media
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);

        // Mixed Content
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Cache
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Geolocation
        s.setGeolocationEnabled(true);

        // User Agent (remove wv indicator)
        String ua = s.getUserAgentString().replace("; wv", "");
        s.setUserAgentString(ua);

        // Cookies
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        // JavaScript Interfaces
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidDownloader");
        webView.addJavascriptInterface(new PrintInterface(), "AndroidPrint");

        // Download Listener
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Log.d(TAG, "Download: " + url);
            downloadHelper.handleDownload(url, userAgent, contentDisposition, mimeType, contentLength);
        });

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                errorLayout.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                pageLoaded = true;
                if (splashDone) showMainContent();

                // Inject JavaScript support
                injectBlobDownloadSupport(view);
                injectPrintOverride(view);

                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !isNetworkAvailable()) {
                    webView.setVisibility(View.GONE);
                    errorLayout.setVisibility(View.VISIBLE);
                    pageLoaded = true;
                    if (splashDone) showMainContent();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.startsWith("tel:") || url.startsWith("mailto:") ||
                    url.startsWith("sms:") || url.startsWith("whatsapp:") ||
                    url.startsWith("intent:")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Exception e) { Log.e(TAG, "Cannot open: " + url); }
                    return true;
                }

                if (url.contains("play.google.com") || url.startsWith("market:")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Exception e) { Log.e(TAG, "Cannot open Play Store"); }
                    return true;
                }

                return false;
            }
        });

        // WebChromeClient
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            // File Upload
            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) fileUploadCallback.onReceiveValue(null);
                fileUploadCallback = filePathCallback;
                openFileChooser(fileChooserParams);
                return true;
            }

            // target="_blank" support
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                    boolean isUserGesture, android.os.Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String url = result.getExtra();
                if (url != null) {
                    view.loadUrl(url);
                }
                return false;
            }

            // Fullscreen Video
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();

                fullscreenContainer.addView(customView);
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);

                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                customView = null;

                getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            }

            // JS Alert
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setCancelable(false).show();
                return true;
            }

            // JS Confirm
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setNegativeButton("Cancel", (d, w) -> result.cancel())
                    .setCancelable(false).show();
                return true;
            }

            // Geolocation
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, true);
            }

            // WebRTC
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

            // Console Log
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.d(TAG, "JS: " + msg.message() + " (line " + msg.lineNumber() + ")");
                return true;
            }
        });
    }

    // ================================================================
    //  Print Support
    // ================================================================
    private void printWebPage() {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("Document");
        printManager.print("WebView Print", adapter, null);
    }

    private void injectPrintOverride(WebView view) {
        String js = "(function() {" +
            "if (window._printOverridden) return;" +
            "window._printOverridden = true;" +
            "window.print = function() { window.AndroidPrint.print(); };" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    public class PrintInterface {
        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> printWebPage());
        }
    }

    // ================================================================
    //  Blob Download Support
    // ================================================================
    private void injectBlobDownloadSupport(WebView view) {
        String js = "(function() {" +
            "if (window._blobInjected) return;" +
            "window._blobInjected = true;" +

            "document.addEventListener('click', function(e) {" +
            "  var t = e.target;" +
            "  while (t && t.tagName !== 'A') t = t.parentElement;" +
            "  if (t && t.href && t.href.startsWith('blob:')) {" +
            "    e.preventDefault();" +
            "    e.stopPropagation();" +
            "    var dn = t.download || t.getAttribute('download') || '';" +
            "    convertBlob(t.href, dn);" +
            "  }" +
            "}, true);" +

            "var origOpen = window.open;" +
            "window.open = function(url) {" +
            "  if (url && url.startsWith('blob:')) { convertBlob(url, ''); return null; }" +
            "  return origOpen.apply(this, arguments);" +
            "};" +

            "function convertBlob(blobUrl, fileName) {" +
            "  var xhr = new XMLHttpRequest();" +
            "  xhr.open('GET', blobUrl, true);" +
            "  xhr.responseType = 'blob';" +
            "  xhr.onload = function() {" +
            "    if (xhr.status === 200) {" +
            "      var blob = xhr.response;" +
            "      var reader = new FileReader();" +
            "      reader.onloadend = function() {" +
            "        window.AndroidDownloader.downloadBase64(" +
            "          reader.result, blob.type || 'application/octet-stream'," +
            "          fileName, blob.size);" +
            "      };" +
            "      reader.readAsDataURL(blob);" +
            "    }" +
            "  };" +
            "  xhr.send();" +
            "}" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    // ================================================================
    //  JavaScript Interface
    // ================================================================
    public class WebAppInterface {
        @JavascriptInterface
        public void downloadBase64(String data, String mime, String name, long size) {
            downloadHelper.downloadBase64(data, mime, name, size);
        }

        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    // ================================================================
    //  File Upload
    // ================================================================
    private void openFileChooser(WebChromeClient.FileChooserParams params) {
        Intent intent = params.createIntent();
        if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        Intent cameraIntent = null;
        String[] acceptTypes = params.getAcceptTypes();
        if (acceptTypes != null && acceptTypes.length > 0 && acceptTypes[0].contains("image")) {
            cameraIntent = createCameraIntent();
        }

        Intent chooser = Intent.createChooser(intent, "ফাইল নির্বাচন করুন");
        if (cameraIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        }
        fileChooserLauncher.launch(chooser);
    }

    private Intent createCameraIntent() {
        Intent ci = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (ci.resolveActivity(getPackageManager()) != null) {
            try {
                File photo = createImageFile();
                cameraImageUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photo);
                ci.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                return ci;
            } catch (IOException e) { Log.e(TAG, "Camera error", e); }
        }
        return null;
    }

    private File createImageFile() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return File.createTempFile("IMG_" + ts + "_", ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES));
    }

    // ================================================================
    //  Helpers
    // ================================================================
    private void loadHomePage() {
        if (isNetworkAvailable() || HOME_URL.startsWith("file:")) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.VISIBLE);
            pageLoaded = true;
            if (splashDone) showMainContent();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(network);
        return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    // ================================================================
    //  Back Button & Lifecycle
    // ================================================================
    @Override
    public void onBackPressed() {
        if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (downloadHelper != null) downloadHelper.destroy();
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.onPause();
        webView.removeAllViews();
        webView.destroy();
        super.onDestroy();
    }
}
