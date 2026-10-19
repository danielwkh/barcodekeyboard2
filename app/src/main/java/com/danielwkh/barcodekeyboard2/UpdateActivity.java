package com.danielwkh.barcodekeyboard2;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateActivity extends AppCompatActivity {
    private static final String RELEASE_API = "https://api.github.com/repos/danielwkh/barcodekeyboard2/releases/latest";
    private static final int REQUEST_INSTALL_PERMISSION = 5412;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView versionInfo;
    private Button checkButton;
    private Button downloadButton;
    private ProgressBar progress;
    private String releaseVersion;
    private String downloadUrl;
    private String currentVersion = "0.0.0";
    private long downloadId = -1;
    private boolean permissionPrompted;
    private final Runnable pollDownload = new Runnable() {
        @Override public void run() { pollDownloadStatus(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(20, 36, 58));
        getWindow().setNavigationBarColor(Color.rgb(245, 247, 250));
        getWindow().getDecorView().setSystemUiVisibility(0x00000010);
        try { currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        setContentView(buildUi());
        checkUpdates();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(20)); page.setBackgroundColor(Color.rgb(245, 247, 250));
        TextView back = label("‹    設定與工具", 15, Color.rgb(70, 82, 98));
        back.setGravity(Gravity.CENTER_VERTICAL); back.setOnClickListener(v -> finish());
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView title = label("線上更新", 29, Color.rgb(25, 42, 63));
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, dp(48)); titleLp.topMargin = dp(20); page.addView(title, titleLp);
        TextView subtitle = label("安全地從官方 GitHub Release 檢查並下載新版本。", 14, Color.rgb(102, 115, 132));
        page.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable cardBg = new GradientDrawable(); cardBg.setColor(Color.WHITE); cardBg.setCornerRadius(dp(18)); card.setBackground(cardBg); card.setElevation(dp(2));
        TextView appName = label("Barcode Keyboard", 17, Color.rgb(44, 57, 75)); appName.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(appName, new LinearLayout.LayoutParams(-1, dp(30)));
        versionInfo = label("目前版本：" + currentVersion, 13, Color.rgb(112, 125, 141));
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(-1, dp(28)); verLp.topMargin = dp(4); card.addView(versionInfo, verLp);
        status = label("正在檢查 GitHub 最新版本…", 14, Color.rgb(57, 72, 91)); status.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, dp(50)); statusLp.topMargin = dp(8); card.addView(status, statusLp);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setVisibility(View.GONE);
        card.addView(progress, new LinearLayout.LayoutParams(-1, dp(5)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT); cardLp.topMargin = dp(18); page.addView(card, cardLp);

        checkButton = actionButton("重新檢查", Color.rgb(226, 232, 240), Color.rgb(45, 58, 76));
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(-1, dp(52)); checkLp.topMargin = dp(22); page.addView(checkButton, checkLp);
        checkButton.setOnClickListener(v -> checkUpdates());
        downloadButton = actionButton("下載並安裝更新", Color.rgb(8, 168, 91), Color.WHITE);
        LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(-1, dp(52)); dlLp.topMargin = dp(10); page.addView(downloadButton, dlLp);
        downloadButton.setVisibility(View.GONE);
        downloadButton.setOnClickListener(v -> beginDownload());

        TextView note = label("下載完成後會由 Android 安裝器顯示確認畫面。安裝需允許此 App 安裝未知來源；你可隨時取消。更新僅從 danielwkh/barcodekeyboard2 公開 Release 取得。", 12, Color.rgb(112, 125, 141));
        note.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, dp(80)); noteLp.topMargin = dp(18); page.addView(note, noteLp);
        return page;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private Button actionButton(String text, int bg, int fg) {
        Button b = new Button(this); b.setText(text); b.setTextSize(15); b.setTextColor(fg); b.setAllCaps(false);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable d = new GradientDrawable(); d.setColor(bg); d.setCornerRadius(dp(14)); b.setBackground(d); b.setStateListAnimator(null); return b;
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void checkUpdates() {
        checkButton.setEnabled(false); downloadButton.setVisibility(View.GONE); status.setText("正在連線檢查版本…"); progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true);
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                connection.setRequestMethod("GET"); connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/vnd.github+json"); connection.setRequestProperty("User-Agent", "BarcodeKeyboard2-Android-Updater");
                int code = connection.getResponseCode();
                if (code != 200) throw new IllegalStateException("GitHub 回應 HTTP " + code);
                StringBuilder body = new StringBuilder();
                try (InputStream in = connection.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject release = new JSONObject(body.toString());
                String tag = release.optString("tag_name", ""); releaseVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                JSONArray assets = release.optJSONArray("assets");
                downloadUrl = null;
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i); String name = asset.optString("name", "");
                    if (name.startsWith("BarcodeKeyboard2-") && name.endsWith(".apk")) { downloadUrl = asset.optString("browser_download_url", null); break; }
                }
                if (releaseVersion.isEmpty() || downloadUrl == null) throw new IllegalStateException("最新 Release 沒有找到 APK 附件");
                Uri releaseUri = Uri.parse(downloadUrl);
                if (!"https".equals(releaseUri.getScheme()) || !"github.com".equals(releaseUri.getHost()) || !releaseUri.getPath().startsWith("/danielwkh/barcodekeyboard2/releases/download/"))
                    throw new IllegalStateException("下載網址並非指定 GitHub Release");
                final String latest = releaseVersion;
                final boolean newer = compareVersions(latest, currentVersion) > 0;
                main.post(() -> {
                    progress.setVisibility(View.GONE); progress.setIndeterminate(false); checkButton.setEnabled(true);
                    versionInfo.setText("目前版本：" + currentVersion + "    ·    最新版本：" + latest);
                    if (newer) { status.setText("發現新版本，可按下方按鈕下載。\n" + release.optString("name", "")); downloadButton.setVisibility(View.VISIBLE); }
                    else status.setText("目前已是最新版本。\n檢查來源：GitHub 官方 Release");
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "網路連線失敗" : error.getMessage();
                main.post(() -> { progress.setVisibility(View.GONE); checkButton.setEnabled(true); status.setText("無法檢查更新：" + message + "\n請檢查網路後重試。"); });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private int compareVersions(String a, String b) {
        String[] aa = a.split("[.-]"); String[] bb = b.split("[.-]");
        for (int i = 0; i < Math.max(aa.length, bb.length); i++) {
            int x = i < aa.length ? numericPart(aa[i]) : 0, y = i < bb.length ? numericPart(bb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }
    private int numericPart(String value) { String digits = value.replaceAll("[^0-9].*$", ""); try { return Integer.parseInt(digits); } catch (Exception e) { return 0; } }

    private void beginDownload() {
        if (downloadUrl == null) return;
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("Barcode Keyboard 更新 " + releaseVersion); request.setDescription("正在下載 APK");
            request.setMimeType("application/vnd.android.package-archive"); request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (Build.VERSION.SDK_INT >= 24) request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            String safeVersion = releaseVersion.replaceAll("[^A-Za-z0-9._-]", "_");
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "BarcodeKeyboard2-v" + safeVersion + ".apk");
            downloadId = manager.enqueue(request);
            getSharedPreferences("updater", MODE_PRIVATE).edit().putLong("download_id", downloadId).apply();
            status.setText("已開始下載 v" + releaseVersion + "…"); checkButton.setEnabled(false); downloadButton.setEnabled(false);
            progress.setIndeterminate(false); progress.setMax(100); progress.setProgress(0); progress.setVisibility(View.VISIBLE);
            main.removeCallbacks(pollDownload); main.post(pollDownload);
        } catch (Exception error) { status.setText("無法開始下載：" + error.getMessage()); }
    }

    @Override protected void onResume() {
        super.onResume();
        long saved = getSharedPreferences("updater", MODE_PRIVATE).getLong("download_id", -1);
        if (saved != -1 && downloadId == -1) { downloadId = saved; main.post(pollDownload); }
    }
    @Override protected void onPause() { main.removeCallbacks(pollDownload); super.onPause(); }
    @Override protected void onDestroy() { main.removeCallbacks(pollDownload); worker.shutdownNow(); super.onDestroy(); }

    private void pollDownloadStatus() {
        if (downloadId == -1) return;
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) { status.setText("找不到下載記錄，請重新檢查更新。"); clearDownload(); return; }
            int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int state = cursor.getInt(statusCol);
            if (state == DownloadManager.STATUS_SUCCESSFUL) {
                main.removeCallbacks(pollDownload); progress.setVisibility(View.GONE); status.setText("下載完成，正在開啟 Android 安裝確認…");
                installDownloaded(manager); return;
            }
            if (state == DownloadManager.STATUS_FAILED) {
                int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)); status.setText("下載失敗（" + reason + "），請檢查網路後重試。"); clearDownload(); return;
            }
            int soFarCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR), totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
            long soFar = cursor.getLong(soFarCol), total = cursor.getLong(totalCol);
            if (total > 0) { int percent = (int)(100 * soFar / total); progress.setProgress(percent); status.setText("下載更新中… " + percent + "%"); }
            main.postDelayed(pollDownload, 800);
        } catch (Exception e) { status.setText("讀取下載狀態失敗：" + e.getMessage()); clearDownload(); }
    }

    private void installDownloaded(DownloadManager manager) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            if (permissionPrompted) { status.setText("尚未允許安裝此更新。可到設定允許後，再重新檢查並下載。"); clearDownload(); return; }
            permissionPrompted = true;
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("允許安裝更新")
                .setMessage("Android 需要你允許 Barcode Keyboard 安裝自己下載的更新。只會在你確認後開啟安裝器；你也可以取消。")
                .setNegativeButton("取消", (d, w) -> { status.setText("已取消安裝；更新檔已下載，可重新檢查後再試。"); clearDownload(); })
                .setPositiveButton("前往設定", (d, w) -> {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(settings, REQUEST_INSTALL_PERMISSION);
                }).show();
            return;
        }
        Uri apk = manager.getUriForDownloadedFile(downloadId);
        if (apk == null) { status.setText("找不到已下載 APK，請重新下載。"); clearDownload(); return; }
        try {
            Intent install = new Intent(Intent.ACTION_VIEW); install.setDataAndType(apk, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            clearDownload(); startActivity(install);
        } catch (Exception e) { status.setText("無法開啟 Android 安裝器：" + e.getMessage()); clearDownload(); }
    }

    private void clearDownload() {
        downloadId = -1; getSharedPreferences("updater", MODE_PRIVATE).edit().remove("download_id").apply();
        checkButton.setEnabled(true); downloadButton.setEnabled(true);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) {
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE); installDownloaded(manager);
            } else { status.setText("未允許安裝；更新檔仍在下載資料夾。你可以稍後再試。"); clearDownload(); }
        }
    }
}
