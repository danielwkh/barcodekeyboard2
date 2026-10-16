package com.danielwkh.barcodekeyboard2;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BarcodeInputMethodService extends InputMethodService {
    private static BarcodeInputMethodService active;
    private LinearLayout root;
    private boolean chinese = false;

    @Override public void onCreate() { super.onCreate(); active = this; }
    @Override public void onDestroy() { if (active == this) active = null; super.onDestroy(); }
    @Override public View onCreateInputView() { buildKeyboard(); return root; }

    public static void commitScannedValue(String value) {
        if (active != null && value != null && !value.isEmpty()) {
            InputConnection ic = active.getCurrentInputConnection();
            if (ic != null) ic.commitText(value, 1);
        }
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.DEFAULT, Typeface.NORMAL); return t;
    }
    private Button key(String label, int color, int fg, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(17); b.setTextColor(fg);
        b.setAllCaps(false); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0); b.setBackground(shape(color, 6));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void addRow(LinearLayout parent, String[] labels, boolean special) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER); row.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (String label : labels) {
            boolean wide = label.equals("⌫") || label.equals("?123") || label.equals("中/EN");
            int bg = (label.equals("⌫") || label.equals("?123")) ? 0xFFD5D9DE : Color.WHITE;
            Button b = key(label, bg, 0xFF202124, () -> press(label));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), wide ? 1.35f : 1f);
            lp.setMargins(dp(2), 0, dp(2), 0); row.addView(b, lp);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    private void press(String key) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        switch (key) {
            case "⌫": ic.deleteSurroundingText(1, 0); break;
            case "↵": ic.commitText("\n", 1); break;
            case "空白": ic.commitText(" ", 1); break;
            case "中/EN": chinese = !chinese; buildKeyboard(); break;
            case "?123": ic.commitText("?123", 1); break;
            default: ic.commitText(chinese ? key : key.toLowerCase(), 1);
        }
    }
    private void buildKeyboard() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(6), dp(5), dp(6), dp(5));
        root.setBackgroundColor(0xFFE9EDF2);
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(3), 0, dp(3), dp(2));
        TextView brand = text("Barcode Keyboard", 14, 0xFF56616C); brand.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button scan = key("▦  掃描", 0xFF07C160, Color.WHITE, () -> {
            Intent i = new Intent(this, ScannerActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
        });
        toolbar.addView(scan, new LinearLayout.LayoutParams(dp(112), dp(38)));
        Button lang = key(chinese ? "中文" : "EN", 0xFFD5D9DE, 0xFF202124, () -> { chinese = !chinese; buildKeyboard(); });
        LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(dp(54), dp(38)); langLp.setMargins(dp(6), 0, 0, 0); toolbar.addView(lang, langLp);
        root.addView(toolbar);
        addRow(root, new String[]{"q","w","e","r","t","y","u","i","o","p"}, false);
        addRow(root, new String[]{"a","s","d","f","g","h","j","k","l"}, false);
        addRow(root, new String[]{"?123","z","x","c","v","b","n","m","⌫"}, true);
        LinearLayout bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(3), dp(3), dp(3), 0);
        Button symbols = key("?123", 0xFFD5D9DE, 0xFF202124, () -> press("?123"));
        bottom.addView(symbols, new LinearLayout.LayoutParams(dp(62), dp(46)));
        Button comma = key(",", Color.WHITE, 0xFF202124, () -> press(","));
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(dp(42), dp(46)); c.setMargins(dp(5),0,dp(5),0); bottom.addView(comma,c);
        Button space = key("空白", Color.WHITE, 0xFF202124, () -> press("空白")); bottom.addView(space, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button period = key(".", Color.WHITE, 0xFF202124, () -> press("."));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(42), dp(46)); p.setMargins(dp(5),0,dp(5),0); bottom.addView(period,p);
        Button enter = key("↵", 0xFF07C160, Color.WHITE, () -> press("↵")); bottom.addView(enter, new LinearLayout.LayoutParams(dp(58), dp(46)));
        root.addView(bottom);
    }
}
