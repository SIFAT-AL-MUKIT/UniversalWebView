package com.webviewapp.template;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloadHelper {

    private static final String TAG = "DownloadHelper";
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private NotificationManager notificationManager;

    private static final Map<String, String> MIME_TO_EXT = new HashMap<>();
    static {
        MIME_TO_EXT.put("image/png", ".png");
        MIME_TO_EXT.put("image/jpeg", ".jpg");
        MIME_TO_EXT.put("image/gif", ".gif");
        MIME_TO_EXT.put("image/webp", ".webp");
        MIME_TO_EXT.put("image/svg+xml", ".svg");
        MIME_TO_EXT.put("image/bmp", ".bmp");
        MIME_TO_EXT.put("image/x-icon", ".ico");
        MIME_TO_EXT.put("image/avif", ".avif");
        MIME_TO_EXT.put("application/pdf", ".pdf");
        MIME_TO_EXT.put("application/msword", ".doc");
        MIME_TO_EXT.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
        MIME_TO_EXT.put("application/vnd.ms-excel", ".xls");
        MIME_TO_EXT.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
        MIME_TO_EXT.put("application/vnd.ms-powerpoint", ".ppt");
        MIME_TO_EXT.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx");
        MIME_TO_EXT.put("application/rtf", ".rtf");
        MIME_TO_EXT.put("text/plain", ".txt");
        MIME_TO_EXT.put("text/html", ".html");
        MIME_TO_EXT.put("text/css", ".css");
        MIME_TO_EXT.put("text/csv", ".csv");
        MIME_TO_EXT.put("application/json", ".json");
        MIME_TO_EXT.put("application/xml", ".xml");
        MIME_TO_EXT.put("text/xml", ".xml");
        MIME_TO_EXT.put("text/markdown", ".md");
        MIME_TO_EXT.put("text/x-markdown", ".md");
        MIME_TO_EXT.put("application/zip", ".zip");
        MIME_TO_EXT.put("application/x-rar-compressed", ".rar");
        MIME_TO_EXT.put("application/vnd.rar", ".rar");
        MIME_TO_EXT.put("application/x-7z-compressed", ".7z");
        MIME_TO_EXT.put("application/x-tar", ".tar");
        MIME_TO_EXT.put("application/gzip", ".gz");
        MIME_TO_EXT.put("application/x-gzip", ".gz");
        MIME_TO_EXT.put("application/x-bzip2", ".bz2");
        MIME_TO_EXT.put("application/x-xz", ".xz");
        MIME_TO_EXT.put("audio/mpeg", ".mp3");
        MIME_TO_EXT.put("audio/wav", ".wav");
        MIME_TO_EXT.put("audio/ogg", ".ogg");
        MIME_TO_EXT.put("audio/flac", ".flac");
        MIME_TO_EXT.put("audio/aac", ".aac");
        MIME_TO_EXT.put("audio/mp4", ".m4a");
        MIME_TO_EXT.put("audio/webm", ".weba");
        MIME_TO_EXT.put("video/mp4", ".mp4");
        MIME_TO_EXT.put("video/webm", ".webm");
        MIME_TO_EXT.put("video/x-msvideo", ".avi");
        MIME_TO_EXT.put("video/x-matroska", ".mkv");
        MIME_TO_EXT.put("video/quicktime", ".mov");
        MIME_TO_EXT.put("video/3gpp", ".3gp");
        MIME_TO_EXT.put("application/javascript", ".js");
        MIME_TO_EXT.put("text/javascript", ".js");
        MIME_TO_EXT.put("application/typescript", ".ts");
        MIME_TO_EXT.put("text/x-python", ".py");
        MIME_TO_EXT.put("text/x-java-source", ".java");
        MIME_TO_EXT.put("text/x-shellscript", ".sh");
        MIME_TO_EXT.put("application/x-sh", ".sh");
        MIME_TO_EXT.put("application/x-yaml", ".yml");
        MIME_TO_EXT.put("text/yaml", ".yml");
        MIME_TO_EXT.put("application/toml", ".toml");
        MIME_TO_EXT.put("application/wasm", ".wasm");
        MIME_TO_EXT.put("font/woff", ".woff");
        MIME_TO_EXT.put("font/woff2", ".woff2");
        MIME_TO_EXT.put("font/ttf", ".ttf");
        MIME_TO_EXT.put("font/otf", ".otf");
        MIME_TO_EXT.put("application/epub+zip", ".epub");
        MIME_TO_EXT.put("application/vnd.android.package-archive", ".apk");
        MIME_TO_EXT.put("application/octet-stream", ".bin");
        MIME_TO_EXT.put("application/x-sqlite3", ".db");
    }

    public DownloadHelper(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newFixedThreadPool(3);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Download progress");
            channel.setShowBadge(false);
            notificationManager = activity.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // ================================================================
    //  Main entry point
    // ================================================================
    public void handleDownload(String url, String userAgent,
            String contentDisposition, String mimeType, long contentLength) {
        Log.d(TAG, "Download: " + url + " | MIME: " + mimeType);

        if (url.startsWith("data:")) {
            handleDataUrlDownload(url);
        } else if (url.startsWith("blob:")) {
            handleBlobUrlDownload(url);
        } else {
            handleHttpDownload(url, userAgent, contentDisposition, mimeType);
        }
    }

    // ================================================================
    //  Data URL download
    // ================================================================
    private void handleDataUrlDownload(String dataUrl) {
        executor.execute(() -> {
            try {
                String[] parts = dataUrl.split(",", 2);
                if (parts.length < 2) { showError("Invalid data URL"); return; }

                String header = parts[0];
                String base64Data = parts[1];

                String mime = "application/octet-stream";
                if (header.contains(":") && header.contains(";")) {
                    mime = header.substring(header.indexOf(":") + 1, header.indexOf(";"));
                }

                byte[] fileBytes = Base64.decode(base64Data, Base64.DEFAULT);
                String extension = getExtensionFromMime(mime);
                String fileName = generateFileName(null, extension);

                saveFile(fileBytes, fileName, mime);
            } catch (Exception e) {
                Log.e(TAG, "Data URL download failed", e);
                showError("ডাউনলোড ব্যর্থ: " + e.getMessage());
            }
        });
    }

    // ================================================================
    //  Blob URL download
    // ================================================================
    private void handleBlobUrlDownload(String blobUrl) {
        String js = "(function() {" +
            "try {" +
            "var xhr = new XMLHttpRequest();" +
            "xhr.open('GET', '" + blobUrl + "', true);" +
            "xhr.responseType = 'blob';" +
            "xhr.onload = function() {" +
            "  if (xhr.status === 200) {" +
            "    var blob = xhr.response;" +
            "    var reader = new FileReader();" +
            "    reader.onloadend = function() {" +
            "      if (reader.result) {" +
            "        window.AndroidDownloader.downloadBase64(" +
            "          reader.result," +
            "          blob.type || 'application/octet-stream'," +
            "          '', blob.size);" +
            "      }" +
            "    };" +
            "    reader.readAsDataURL(blob);" +
            "  }" +
            "};" +
            "xhr.send();" +
            "} catch(e) { console.error('Blob error:', e); }" +
            "})();";

        mainHandler.post(() -> webView.evaluateJavascript(js, null));
    }

    // ================================================================
    //  HTTP download (DownloadManager)
    // ================================================================
    private void handleHttpDownload(String url, String userAgent,
            String contentDisposition, String mimeType) {
        try {
            String fileName = extractFileName(url, contentDisposition, mimeType);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);

            request.setTitle(fileName);
            request.setDescription("ডাউনলোড হচ্ছে...");
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = dm.enqueue(request);

            showToast("⬇️ ডাউনলোড শুরু: " + fileName);
            registerDownloadReceiver(downloadId, fileName);
        } catch (Exception e) {
            Log.e(TAG, "DownloadManager failed, trying manual", e);
            manualHttpDownload(url, userAgent, contentDisposition, mimeType);
        }
    }

    // ================================================================
    //  Manual HTTP download (fallback)
    //  ← এখানেই আগের error ছিল, এবার ঠিক করা হয়েছে
    // ================================================================
    private void manualHttpDownload(final String urlString, final String userAgent,
            final String contentDisposition, final String mimeType) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent);
                String cookies = CookieManager.getInstance().getCookie(urlString);
                if (cookies != null) connection.setRequestProperty("Cookie", cookies);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    showError("সার্ভার error: " + connection.getResponseCode());
                    return;
                }

                // local variable এ copy করো (lambda safe)
                String actualMime = mimeType;
                String actualDisposition = contentDisposition;

                String serverMime = connection.getContentType();
                String serverDisp = connection.getHeaderField("Content-Disposition");
                int contentLength = connection.getContentLength();

                if (serverMime != null) actualMime = serverMime;
                if (serverDisp != null) actualDisposition = serverDisp;

                String fileName = extractFileName(urlString, actualDisposition, actualMime);

                InputStream is = connection.getInputStream();
                File tempFile = new File(activity.getCacheDir(), fileName);
                FileOutputStream fos = new FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                int totalRead = 0;

                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (contentLength > 0) {
                        int progress = (int) ((totalRead * 100L) / contentLength);
                        showProgress(fileName, progress);
                    }
                }
                fos.close();
                is.close();

                byte[] fileBytes = new byte[(int) tempFile.length()];
                FileInputStream fis = new FileInputStream(tempFile);
                fis.read(fileBytes);
                fis.close();
                saveFile(fileBytes, fileName, actualMime);
                tempFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "Manual download failed", e);
                showError("ডাউনলোড ব্যর্থ: " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    // ================================================================
    //  Base64 download (from JavaScript interface)
    //  ← এখানেও আগের error ছিল, এবার ঠিক করা হয়েছে
    // ================================================================
    public void downloadBase64(final String base64Data, final String inputMimeType,
            final String suggestedName, final long size) {
        executor.execute(() -> {
            try {
                String pureBase64 = base64Data;
                String actualMime = inputMimeType;

                if (base64Data.contains(",")) {
                    String header = base64Data.substring(0, base64Data.indexOf(","));
                    pureBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
                    if (header.contains(":") && header.contains(";")) {
                        actualMime = header.substring(header.indexOf(":") + 1, header.indexOf(";"));
                    }
                }

                byte[] fileBytes = Base64.decode(pureBase64, Base64.DEFAULT);
                String extension = getExtensionFromMime(actualMime);
                String fileName;

                if (suggestedName != null && !suggestedName.isEmpty()) {
                    fileName = suggestedName;
                } else {
                    fileName = generateFileName(null, extension);
                }

                if (!hasExtension(fileName)) {
                    fileName = fileName + extension;
                }

                saveFile(fileBytes, fileName, actualMime);

            } catch (Exception e) {
                Log.e(TAG, "Base64 download failed", e);
                showError("ডাউনলোড ব্যর্থ");
            }
        });
    }

    // ================================================================
    //  Save file
    // ================================================================
    private void saveFile(byte[] fileBytes, String fileName, String mimeType) {
        try {
            Uri fileUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                fileUri = saveWithMediaStore(fileBytes, fileName, mimeType);
            } else {
                fileUri = saveWithLegacy(fileBytes, fileName);
            }
            if (fileUri != null) {
                showSuccess(fileName);
            } else {
                showError("ফাইল সেভ করা যায়নি");
            }
        } catch (Exception e) {
            Log.e(TAG, "Save failed", e);
            showError("ফাইল সেভ ব্যর্থ: " + e.getMessage());
        }
    }

    private Uri saveWithMediaStore(byte[] fileBytes, String fileName, String mimeType)
            throws IOException {
        ContentResolver resolver = activity.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri fileUri = resolver.insert(collection, values);

        if (fileUri != null) {
            OutputStream os = resolver.openOutputStream(fileUri);
            if (os != null) {
                os.write(fileBytes);
                os.flush();
                os.close();
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(fileUri, values, null, null);
        }
        return fileUri;
    }

    private Uri saveWithLegacy(byte[] fileBytes, String fileName) throws IOException {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, fileName);
        int counter = 1;
        String base = fileName.contains(".")
            ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        String ext = fileName.contains(".")
            ? fileName.substring(fileName.lastIndexOf(".")) : "";
        while (file.exists()) {
            file = new File(dir, base + " (" + counter + ")" + ext);
            counter++;
        }

        FileOutputStream fos = new FileOutputStream(file);
        fos.write(fileBytes);
        fos.close();

        MediaScannerConnection.scanFile(activity,
            new String[]{file.getAbsolutePath()}, null, null);
        return FileProvider.getUriForFile(activity,
            activity.getPackageName() + ".fileprovider", file);
    }

    // ================================================================
    //  Filename extraction
    // ================================================================
    private String extractFileName(String url, String contentDisposition, String mimeType) {
        String fileName = null;

        if (contentDisposition != null) {
            fileName = parseContentDisposition(contentDisposition);
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        }

        if (fileName == null || fileName.isEmpty() || fileName.equals("downloadfile")) {
            try {
                String path = Uri.parse(url).getLastPathSegment();
                if (path != null && path.contains(".")) {
                    fileName = URLDecoder.decode(path, "UTF-8");
                }
            } catch (Exception ignored) {}
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = generateFileName(null, getExtensionFromMime(mimeType));
        }

        if (!hasExtension(fileName)) {
            fileName = fileName + getExtensionFromMime(mimeType);
        }

        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return fileName;
    }

    private String parseContentDisposition(String cd) {
        try {
            Pattern p1 = Pattern.compile(
                "filename\\*=(?:UTF-8''|utf-8'')([^;\\s]+)", Pattern.CASE_INSENSITIVE);
            Matcher m1 = p1.matcher(cd);
            if (m1.find()) return URLDecoder.decode(m1.group(1), "UTF-8");

            Pattern p2 = Pattern.compile("filename=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(cd);
            if (m2.find()) return m2.group(1);

            Pattern p3 = Pattern.compile("filename=([^;\\s]+)", Pattern.CASE_INSENSITIVE);
            Matcher m3 = p3.matcher(cd);
            if (m3.find()) return m3.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private String getExtensionFromMime(String mimeType) {
        if (mimeType == null) return ".bin";
        if (mimeType.contains(";")) {
            mimeType = mimeType.substring(0, mimeType.indexOf(";")).trim();
        }
        mimeType = mimeType.toLowerCase().trim();

        if (MIME_TO_EXT.containsKey(mimeType)) return MIME_TO_EXT.get(mimeType);

        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (ext != null) return "." + ext;

        return ".bin";
    }

    private String generateFileName(String prefix, String extension) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        String p = (prefix != null && !prefix.isEmpty()) ? prefix : "download";
        return p + "_" + ts + extension;
    }

    private boolean hasExtension(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf(".");
        if (dot < 0) return false;
        String ext = name.substring(dot);
        return ext.length() > 1 && ext.length() <= 10;
    }

    // ================================================================
    //  Download complete receiver
    // ================================================================
    private void registerDownloadReceiver(long downloadId, String fileName) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    showSuccess(fileName);
                    context.unregisterReceiver(this);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    // ================================================================
    //  UI helpers
    // ================================================================
    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    private void showError(String msg) {
        mainHandler.post(() -> Toast.makeText(activity, "❌ " + msg, Toast.LENGTH_LONG).show());
    }

    private void showSuccess(String fileName) {
        mainHandler.post(() -> Toast.makeText(activity,
            "✅ ডাউনলোড সম্পূর্ণ!\n📁 " + fileName, Toast.LENGTH_LONG).show());
    }

    private void showProgress(String fileName, int progress) {
        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder b = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("ডাউনলোড হচ্ছে")
                .setContentText(fileName)
                .setProgress(100, progress, false)
                .setOngoing(true);
            notificationManager.notify(NOTIFICATION_ID, b.build());
        }
    }

    public void destroy() {
        executor.shutdown();
    }
}
