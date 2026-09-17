package life.momnt.preview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

/** Thin Android shell; the supplied application remains the source of UI behavior. */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    private static final int PICK_FILE = 100;
    private static final int SAVE_FILE = 101;
    private static final int MAX_EXPORT_BYTES = 24 * 1024 * 1024;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private byte[] pendingExport;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(250, 250, 247));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isLocal(uri)) return false;
                if (request.isForMainFrame() && request.hasGesture()
                        && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ex) { showToast("No browser available."); }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                chooseFile(callback, params);
                return true;
            }
        });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "MomntExport",
                    Collections.singleton(ORIGIN), (view, message, sourceOrigin, mainFrame, reply) -> {
                        if (mainFrame && ORIGIN.equals(sourceOrigin.toString())) {
                            receiveExport(message.getData());
                        }
                    });
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Update Android System WebView")
                    .setMessage("You can explore Momn\u2019t, but exports need a newer Android System WebView. Update it in the Play Store.")
                    .setPositiveButton("OK", null).show();
        }
        if (state == null || web.restoreState(state) == null) web.loadUrl(HOME);
    }
    private boolean isLocal(Uri uri) {
        return "https".equals(uri.getScheme())
                && "appassets.androidplatform.net".equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/assets/");
    }
    private void chooseFile(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = callback;
        cameraUri = null;
        File cameraDir = new File(getCacheDir(), "camera");
        File[] old = cameraDir.listFiles();
        if (old != null) for (File file : old) {
            if (System.currentTimeMillis() - file.lastModified() > 86400000L) file.delete();
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        ArrayList<String> types = new ArrayList<>();
        for (String accepted : params.getAcceptTypes()) {
            for (String type : accepted.split(",")) {
                type = type.trim();
                if (".pdf".equals(type)) type = "application/pdf";
                if (".txt".equals(type)) type = "text/plain";
                if (type.contains("/")) types.add(type);
            }
        }
        if (types.size() == 1) picker.setType(types.get(0));
        else {
            picker.setType("*/*");
            if (!types.isEmpty()) picker.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
        }
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(picker, "Choose a capture");
        if (params.isCaptureEnabled()) {
            try {
                if (!cameraDir.exists() && !cameraDir.mkdirs()) throw new java.io.IOException();
                File photo = File.createTempFile("capture-", ".jpg", cameraDir);
                cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", photo);
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                camera.setClipData(ClipData.newRawUri("Momnt capture", cameraUri));
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
            } catch (Exception ex) { cameraUri = null; }
        }
        try { startActivityForResult(chooser, PICK_FILE); }
        catch (ActivityNotFoundException ex) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
            showToast("No file picker available.");
        }
    }
    private void receiveExport(String payload) {
        try {
            if (payload == null || payload.length() > MAX_EXPORT_BYTES * 1.5) {
                throw new IllegalArgumentException("Export too large");
            }
            if (pendingExport != null) {
                showToast("Finish saving the previous export first.");
                return;
            }
            JSONObject data = new JSONObject(payload);
            String name = data.getString("name").replaceAll("[^A-Za-z0-9._-]", "_");
            String mime = data.getString("mime");
            if (name.isEmpty() || name.length() > 120) throw new IllegalArgumentException();
            if (!mime.equals("application/json") && !mime.equals("text/csv")
                    && !mime.equals("image/svg+xml")) throw new IllegalArgumentException();
            byte[] content = Base64.decode(data.getString("base64"), Base64.DEFAULT);
            if (content.length > MAX_EXPORT_BYTES) throw new IllegalArgumentException();
            pendingExport = content;
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType(mime);
            save.putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(save, SAVE_FILE);
        } catch (Exception ex) {
            pendingExport = null;
            showToast("Could not prepare the export. Try a smaller file.");
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FILE && fileCallback != null) {
            Uri selected = null;
            if (result == RESULT_OK) {
                selected = data != null && data.getData() != null ? data.getData() : cameraUri;
            }
            fileCallback.onReceiveValue(selected == null ? null : new Uri[]{selected});
            fileCallback = null;
            cameraUri = null;
        } else if (request == SAVE_FILE) {
            byte[] content = pendingExport;
            pendingExport = null;
            if (result == RESULT_OK && data != null && data.getData() != null && content != null) {
                Uri destination = data.getData();
                new Thread(() -> {
                    try (OutputStream out = getContentResolver().openOutputStream(destination)) {
                        if (out == null) throw new java.io.IOException();
                        out.write(content);
                        runOnUiThread(() -> showToast("Export saved."));
                    } catch (Exception ex) {
                        runOnUiThread(() -> showToast("Could not save the file. Please export again."));
                    }
                }, "momnt-export").start();
            } else if (result == RESULT_OK && content == null) {
                showToast("Export interrupted. Please export again.");
            }
        }
    }
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        web.evaluateJavascript("(function(){var d=document.querySelector('[role=\\\"dialog\\\"]');"
                + "if(d){var b=d.querySelector('button.absolute');if(b){b.click();return true;}"
                + "var e=new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true});"
                + "document.dispatchEvent(e);return true;}return false;})()", result -> {
            if (!"true".equals(result)) {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        web.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        pendingExport = null;
        web.destroy();
        super.onDestroy();
    }
    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
