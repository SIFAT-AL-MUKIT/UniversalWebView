package com.myapp.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "UniversalWebView";

    // ★★★ আপনার URL এখানে চেঞ্জ করুন ★★★
    private static final String HOME_URL = "file:///android_asset/www/index.html";

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout errorLayout;

    // File Upload
    private ValueCallback<Uri[]> uploadCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorLayout = findViewById(R.id.errorLayout);
        Button btnRetry = findViewById(R.id.btnRetry);

        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl(HOME_URL);
            }
        });

        requestPermissions();
        setupWebView();
        webView.loadUrl(HOME_URL);
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT <= 28) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }, 100);
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        String ua = s.getUserAgentString().replace("; wv", "");
        s.setUserAgentString(ua);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JSInterface(), "AndroidDownloader");

        // ============ DOWNLOAD LISTENER ============
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                    String contentDisposition, String mimeType, long contentLength) {

                Log.d(TAG, "=== DOWNLOAD ===");
                Log.d(TAG, "URL: " + url);
                Log.d(TAG, "MIME: " + mimeType);

                if (url.startsWith("data:")) {
                    downloadDataUrl(url);
                } else if (url.startsWith("blob:")) {
                    downloadBlobUrl(url);
                } else {
                    downloadHttpUrl(url, userAgent, contentDisposition, mimeType);
                }
            }
        });

        // ============ WEB VIEW CLIENT ============
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                errorLayout.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                injectBlobCatcher(view);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame() && !isOnline()) {
                    webView.setVisibility(View.GONE);
                    errorLayout.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.startsWith("tel:") || url.startsWith("mailto:")
                        || url.startsWith("sms:") || url.startsWith("whatsapp:")
                        || url.startsWith("intent:")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {}
                    return true;
                }

                if (url.contains("play.google.com") || url.startsWith("market:")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {}
                    return true;
                }

                return false;
            }
        });

        // ============ WEB CHROME CLIENT ============
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (uploadCallback != null) {
                    uploadCallback.onReceiveValue(null);
                }
                uploadCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                Intent chooser = Intent.createChooser(intent, "Select File");
                startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setCancelable(false)
                    .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setNegativeButton("Cancel", (d, w) -> result.cancel())
                    .setCancelable(false)
                    .show();
                return true;
            }
        });
    }

    private void injectBlobCatcher(WebView view) {
        String js = "(function(){" +
            "if(window._blobHooked)return;" +
            "window._blobHooked=true;" +
            "var oc=HTMLAnchorElement.prototype.click;" +
            "HTMLAnchorElement.prototype.click=function(){" +
            "  var h=this.href,d=this.download||'';" +
            "  if(h&&h.startsWith('blob:')){" +
            "    _fetchBlob(h,d);return;" +
            "  }" +
            "  return oc.apply(this,arguments);" +
            "};" +
            "document.addEventListener('click',function(e){" +
            "  var t=e.target;" +
            "  while(t&&t.tagName!=='A')t=t.parentElement;" +
            "  if(t&&t.href&&t.href.startsWith('blob:')){" +
            "    e.preventDefault();e.stopPropagation();" +
            "    _fetchBlob(t.href,t.download||'');" +
            "  }" +
            "},true);" +
            "var wo=window.open;" +
            "window.open=function(u){" +
            "  if(u&&u.startsWith('blob:')){_fetchBlob(u,'');return null;}" +
            "  return wo.apply(this,arguments);" +
            "};" +
            "function _fetchBlob(url,name){" +
            "  var x=new XMLHttpRequest();" +
            "  x.open('GET',url,true);" +
            "  x.responseType='blob';" +
            "  x.onload=function(){" +
            "    var r=new FileReader();" +
            "    r.onloadend=function(){" +
            "      AndroidDownloader.saveBase64(r.result,name,x.response.type,x.response.size);" +
            "    };" +
            "    r.readAsDataURL(x.response);" +
            "  };" +
            "  x.send();" +
            "}" +
            "console.log('BlobCatcher injected');" +
            "})();";

        view.evaluateJavascript(js, null);
    }

    private void downloadDataUrl(String dataUrl) {
        new Thread(() -> {
            try {
                String[] parts = dataUrl.split(",", 2);
                String header = parts[0];
                String base64 = parts[1];

                String mime = "application/octet-stream";
                if (header.contains(":") && header.contains(";")) {
                    mime = header.substring(header.indexOf(":") + 1, header.indexOf(";"));
                }

                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String ext = mimeToExt(mime);
                String name = "download_" + timestamp() + ext;

                saveBytes(bytes, name, mime);

            } catch (Exception e) {
                Log.e(TAG, "Data URL download failed", e);
                toast("Download failed");
            }
        }).start();
    }

    private void downloadBlobUrl(String blobUrl) {
        String js = "(function(){" +
            "var x=new XMLHttpRequest();" +
            "x.open('GET','" + blobUrl + "',true);" +
            "x.responseType='blob';" +
            "x.onload=function(){" +
            "  var r=new FileReader();" +
            "  r.onloadend=function(){" +
            "    AndroidDownloader.saveBase64(r.result,'download',x.response.type,x.response.size);" +
            "  };" +
            "  r.readAsDataURL(x.response);" +
            "};" +
            "x.send();" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void downloadHttpUrl(String url, String userAgent,
            String contentDisposition, String mimeType) {
        try {
            String fileName = guessFileName(url, contentDisposition, mimeType);

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType);

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) req.addRequestHeader("Cookie", cookies);
            if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);

            req.setTitle(fileName);
            req.setDescription("Downloading...");
            req.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);

            toast("Downloading: " + fileName);

        } catch (Exception e) {
            Log.e(TAG, "HTTP download failed", e);
            toast("Download failed");
        }
    }

    public class JSInterface {

        @JavascriptInterface
        public void saveBase64(String dataUrl, String fileName, String mimeType, long size) {
            Log.d(TAG, "JS saveBase64: name=" + fileName + " mime=" + mimeType + " size=" + size);

            new Thread(() -> {
                try {
                    String base64 = dataUrl.contains(",")
                        ? dataUrl.substring(dataUrl.indexOf(",") + 1)
                        : dataUrl;

                    if (dataUrl.contains(":") && dataUrl.contains(";")) {
                        String headerMime = dataUrl.substring(
                            dataUrl.indexOf(":") + 1, dataUrl.indexOf(";"));
                        if (!headerMime.isEmpty()) mimeType = headerMime;
                    }

                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

                    String ext = mimeToExt(mimeType);
                    if (fileName == null || fileName.isEmpty() || fileName.equals("download")) {
                        fileName = "download_" + timestamp() + ext;
                    }
                    if (!fileName.contains(".")) {
                        fileName += ext;
                    }

                    saveBytes(bytes, fileName, mimeType);

                } catch (Exception e) {
                    Log.e(TAG, "saveBase64 failed", e);
                    toast("Save failed");
                }
            }).start();
        }
    }

    private void saveBytes(byte[] bytes, String fileName, String mimeType) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(bytes);
                        os.flush();
                        os.close();
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);

                int c = 1;
                String base = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
                String ext = fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf(".")) : "";
                while (file.exists()) {
                    file = new File(dir, base + "(" + c + ")" + ext);
                    c++;
                }

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bytes);
                fos.flush();
                fos.close();
            }

            toast("Saved: " + fileName + "\nLocation: Downloads folder");
            Log.d(TAG, "File saved: " + fileName + " (" + bytes.length + " bytes)");

        } catch (Exception e) {
            Log.e(TAG, "saveBytes failed", e);
            toast("Save failed: " + e.getMessage());
        }
    }

    private String guessFileName(String url, String contentDisposition, String mimeType) {
        String name = null;

        if (contentDisposition != null) {
            name = parseDisposition(contentDisposition);
        }

        if (name == null || name.isEmpty()) {
            name = URLUtil.guessFileName(url, contentDisposition, mimeType);
        }

        if (name == null || name.isEmpty()) {
            name = "download_" + timestamp() + mimeToExt(mimeType);
        }

        if (!name.contains(".")) {
            name += mimeToExt(mimeType);
        }

        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String parseDisposition(String cd) {
        try {
            Pattern p1 = Pattern.compile("filename\\*=(?:UTF-8''|utf-8'')([^;\\s]+)");
            Matcher m1 = p1.matcher(cd);
            if (m1.find()) return java.net.URLDecoder.decode(m1.group(1), "UTF-8");

            Pattern p2 = Pattern.compile("filename=\"([^\"]+)\"");
            Matcher m2 = p2.matcher(cd);
            if (m2.find()) return m2.group(1);

            Pattern p3 = Pattern.compile("filename=([^;\\s]+)");
            Matcher m3 = p3.matcher(cd);
            if (m3.find()) return m3.group(1);

        } catch (Exception ignored) {}
        return null;
    }

    private String mimeToExt(String mime) {
        if (mime == null) return ".bin";
        mime = mime.toLowerCase().split(";")[0].trim();
        switch (mime) {
            case "image/png": return ".png";
            case "image/jpeg": case "image/jpg": return ".jpg";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/svg+xml": return ".svg";
            case "image/bmp": return ".bmp";
            case "application/pdf": return ".pdf";
            case "application/zip": return ".zip";
            case "application/x-rar-compressed": return ".rar";
            case "application/x-7z-compressed": return ".7z";
            case "application/msword": return ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return ".docx";
            case "application/vnd.ms-excel": return ".xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return ".xlsx";
            case "application/vnd.ms-powerpoint": return ".ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation": return ".pptx";
            case "text/plain": return ".txt";
            case "text/html": return ".html";
            case "text/csv": return ".csv";
            case "application/json": return ".json";
            case "audio/mpeg": return ".mp3";
            case "audio/wav": return ".wav";
            case "video/mp4": return ".mp4";
            case "video/webm": return ".webm";
            case "application/vnd.android.package-archive": return ".apk";
            default: return ".bin";
        }
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (uploadCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String s = data.getDataString();
                    if (s != null) results = new Uri[]{Uri.parse(s)};
                }
                uploadCallback.onReceiveValue(results);
                uploadCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() { super.onResume(); webView.onResume(); }

    @Override
    protected void onPause() { super.onPause(); webView.onPause(); }

    @Override
    protected void onDestroy() {
        webView.stopLoading();
        webView.destroy();
        super.onDestroy();
    }
}
