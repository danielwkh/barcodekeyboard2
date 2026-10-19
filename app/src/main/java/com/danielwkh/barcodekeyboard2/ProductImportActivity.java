package com.danielwkh.barcodekeyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductImportActivity extends Activity {
    private static final int PICK_XLSX = 40;
    private TextView status;
    private Button pickButton;
    private Button clearButton;
    private ProgressBar progress;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String value, float size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private Button button(String value, int color, int foreground) {
        Button b = new Button(this); b.setText(value); b.setTextSize(14); b.setTextColor(foreground); b.setAllCaps(false);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(14), 0, dp(14), 0); b.setBackground(shape(color, 14)); b.setStateListAnimator(null); return b;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(20, 36, 58)); getWindow().setNavigationBarColor(Color.rgb(245, 247, 250));
        getWindow().getDecorView().setSystemUiVisibility(0x00000010);
        setContentView(buildPage());
    }

    private View buildPage() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(20), dp(22), dp(24)); page.setBackgroundColor(0xFFF5F7FA);
        TextView back = label("‹    設定與工具", 15, 0xFF465266); back.setGravity(Gravity.CENTER_VERTICAL); back.setOnClickListener(v -> finish());
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView title = label("商品資料", 28, 0xFF192A3F); title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, dp(44)); titleLp.topMargin = dp(16); page.addView(title, titleLp);
        TextView subtitle = label("匯入商品條碼對照，掃描時可快速輸出商品名稱。", 14, 0xFF66758A);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, dp(42)); page.addView(subtitle, subLp);

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(16), dp(18), dp(16)); card.setBackground(shape(Color.WHITE, 18)); card.setElevation(dp(2));
        TextView cardTitle = label("支援的 XLSX 欄位", 16, 0xFF2C394B); cardTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(cardTitle, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView fields = label("商品名稱     條碼     第二條碼\n\n第一張「商品」工作表會匯入；主條碼和第二條碼都會對應同一商品。其他工作表不會匯入。", 14, 0xFF516073);
        fields.setLineSpacing(dp(3), 1f); card.addView(fields, new LinearLayout.LayoutParams(-1, dp(96)));
        TextView privacy = label("原始 XLSX 只在匯入時讀取，不複製到 App，也不會打包進 APK。商品目錄只存在此裝置。", 12, 0xFF77869A);
        privacy.setLineSpacing(dp(2), 1f); card.addView(privacy, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2); cardLp.topMargin = dp(14); page.addView(card, cardLp);

        status = label("本機商品：" + ProductCatalog.count(this) + " 筆", 14, 0xFF087F47); status.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, dp(42)); statusLp.topMargin = dp(12); page.addView(status, statusLp);
        progress = new ProgressBar(this); progress.setVisibility(View.GONE); page.addView(progress, new LinearLayout.LayoutParams(dp(34), dp(34)));

        pickButton = button("選取 XLSX 檔案", 0xFF08A85B, Color.WHITE);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(-1, dp(52)); pickLp.topMargin = dp(12); page.addView(pickButton, pickLp);
        pickButton.setOnClickListener(v -> pickFile());
        clearButton = button("清空本機商品資料", 0xFFFFF0F0, 0xFFB3261E);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-1, dp(48)); clearLp.topMargin = dp(10); page.addView(clearButton, clearLp);
        clearButton.setOnClickListener(v -> confirmClear());
        Button done = button("完成", 0xFFE4EAF1, 0xFF334155);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-1, dp(48)); doneLp.topMargin = dp(10); page.addView(done, doneLp);
        done.setOnClickListener(v -> finish());
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xFFF5F7FA);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2)); return scroll;
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(i, PICK_XLSX); }
        catch (Exception e) { Toast.makeText(this, "無法開啟檔案選擇器", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_XLSX || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); pickButton.setEnabled(false); clearButton.setEnabled(false); progress.setVisibility(View.VISIBLE);
        status.setTextColor(0xFF465266); status.setText("正在背景匯入；大型檔案可能需要一點時間…");
        worker.execute(() -> {
            try {
                int rows = ProductCatalog.importWorkbook(getApplicationContext(), uri); int total = ProductCatalog.count(getApplicationContext());
                main.post(() -> { if (isFinishing() || isDestroyed()) return; progress.setVisibility(View.GONE); pickButton.setEnabled(true); clearButton.setEnabled(true); status.setTextColor(0xFF087F47); status.setText("匯入完成：" + rows + " 個條碼；目前共 " + total + " 個條碼"); Toast.makeText(this, "商品資料匯入完成", Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "無法讀取 XLSX" : e.getMessage();
                main.post(() -> { if (isFinishing() || isDestroyed()) return; progress.setVisibility(View.GONE); pickButton.setEnabled(true); clearButton.setEnabled(true); status.setTextColor(0xFFB3261E); status.setText("匯入未完成：" + message); });
            }
        });
    }
    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("清空本機商品資料？")
            .setMessage("這會刪除此裝置保存的所有商品名稱與條碼對照。原始 XLSX 不會受影響，之後可重新匯入。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空資料", (dialog, which) -> {
                ProductCatalog.clear(this); status.setTextColor(0xFF087F47); status.setText("本機商品：0 筆");
                Toast.makeText(this, "已清空本機商品資料", Toast.LENGTH_SHORT).show();
            }).show();
    }
    @Override protected void onDestroy() { worker.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
