package com.danielwkh.barcodekeyboard2;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

public class ProductImportActivity extends Activity {
    private static final int PICK_XLSX = 40;
    private TextView status;
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(24), dp(22), dp(22)); page.setBackgroundColor(0xFFF6F7F8);
        TextView title = new TextView(this); title.setText("匯入商品資料"); title.setTextSize(23); title.setTextColor(0xFF18212B); title.setGravity(Gravity.CENTER_VERTICAL); page.addView(title);
        TextView guide = new TextView(this); guide.setText("選取本機 XLSX 活頁簿。第一列需包含條碼及商品名稱欄位，例如：\n\nBarcode | Product Name\n或\n條碼 | 商品名稱\n\n資料只儲存在此裝置。掃描鍵盤可切換輸出條碼或商品名稱。"); guide.setTextSize(16); guide.setTextColor(0xFF4E5965); guide.setPadding(0, dp(14), 0, dp(14)); page.addView(guide);
        status = new TextView(this); status.setTextSize(15); status.setTextColor(0xFF087F47); status.setText("已匯入商品：" + ProductCatalog.count(this) + " 筆"); status.setPadding(0, dp(10), 0, dp(18)); page.addView(status);
        Button pick = new Button(this); pick.setText("選取 XLSX 檔案"); pick.setAllCaps(false); pick.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF07C160)); pick.setTextColor(Color.WHITE); page.addView(pick, new LinearLayout.LayoutParams(-1, dp(50))); pick.setOnClickListener(v -> pickFile());
        Button done = new Button(this); done.setText("完成"); done.setAllCaps(false); page.addView(done); done.setOnClickListener(v -> finish());
        setContentView(page);
    }
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(i, PICK_XLSX); } catch (Exception e) { Toast.makeText(this, "無法開啟檔案選擇器", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_XLSX || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData(); if (uri == null) return;
        try {
            int rows = ProductCatalog.importWorkbook(this, uri);
            status.setText("成功匯入 " + rows + " 筆；目前商品總數 " + ProductCatalog.count(this) + " 筆");
            Toast.makeText(this, "商品資料匯入完成", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "無法讀取 XLSX" : e.getMessage();
            status.setText("匯入未完成：" + message); status.setTextColor(0xFFB3261E);
        }
    }
}
