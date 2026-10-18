package com.danielwkh.barcodekeyboard2;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.HapticFeedbackConstants;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BarcodeInputMethodService extends InputMethodService implements LifecycleOwner {
    private static final int KEYBOARD_HEIGHT_DP = 340;
    private static final int KEY_ROW_HEIGHT_DP = 50;
    private static BarcodeInputMethodService active;
    private LifecycleRegistry lifecycle;
    private LinearLayout root;
    private FrameLayout previewStage;
    private PreviewView previewView;
    private boolean scanning = false;
    private boolean textScan = false;
    private boolean outputProductName = false;
    private boolean shifted = false;
    private boolean numericMode = false;
    private String toolPage;
    private String lastScannedText;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private TextRecognizer textRecognizer;
    private final AtomicBoolean scanHandled = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate(); active = this; lifecycle = new LifecycleRegistry(this); lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        cameraExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient();
        textRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }
    @Override public Lifecycle getLifecycle() { return lifecycle; }
    @Override public View onCreateInputView() { return buildView(); }
    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        if (!restarting) lastScannedText = null;
    }
    @Override public void onWindowShown() { super.onWindowShown(); applyImeWindowHeight(); }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (lifecycle.getCurrentState() == Lifecycle.State.CREATED || lifecycle.getCurrentState() == Lifecycle.State.INITIALIZED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
        if (lifecycle.getCurrentState() == Lifecycle.State.STARTED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        applyImeWindowHeight(); if (scanning) startInlineCamera();
    }
    @Override public void onFinishInputView(boolean finishingInput) {
        stopCamera();
        if (lifecycle.getCurrentState() == Lifecycle.State.RESUMED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        if (lifecycle.getCurrentState() == Lifecycle.State.STARTED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onFinishInputView(finishingInput);
    }
    @Override public void onDestroy() {
        stopCamera();
        if (lifecycle.getCurrentState() == Lifecycle.State.RESUMED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        if (lifecycle.getCurrentState() == Lifecycle.State.STARTED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        if (barcodeScanner != null) barcodeScanner.close(); if (textRecognizer != null) textRecognizer.close();
        if (cameraExecutor != null) cameraExecutor.shutdown(); if (active == this) active = null; super.onDestroy();
    }
    private void applyImeWindowHeight() {
        try { Dialog dialog = getWindow(); Window window = dialog == null ? null : dialog.getWindow();
            if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP));
        } catch (Exception ignored) { }
    }
    static void cameraPermissionResolved(boolean granted) {
        if (active == null) return;
        if (granted) { active.enterScanner(); active.requestShowSelf(0); }
        else { active.scanning = false; active.rebuild(); Toast.makeText(active, "需要相機權限才能掃描", Toast.LENGTH_LONG).show(); }
    }
    public static void commitScannedValue(String value) { if (active != null) active.commit(value); }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER); return t; }
    private Button button(String label, int color, int fg, int size, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(size); b.setTextColor(fg); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(3), 0, dp(3), 0); b.setBackground(shape(color, 8)); b.setOnClickListener(v -> {
            android.content.SharedPreferences prefs = getSharedPreferences("keyboard_settings", MODE_PRIVATE);
            if (prefs.getBoolean("haptics", false)) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (prefs.getBoolean("key_sound", false)) v.playSoundEffect(SoundEffectConstants.CLICK);
            action.run();
        });
        if (label.equals("⌫") || label.equals("刪除")) enableRepeatDelete(b);
        return b;
    }
    private void enableRepeatDelete(Button target) {
        final boolean[] held = {false}, repeated = {false};
        final Runnable[] repeat = new Runnable[1];
        repeat[0] = () -> { if (held[0]) { repeated[0] = true; backspace(false); mainHandler.postDelayed(repeat[0], 65); } };
        target.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) { held[0] = true; repeated[0] = false; mainHandler.postDelayed(repeat[0], 380); return true; }
            if (action == MotionEvent.ACTION_UP) {
                held[0] = false; mainHandler.removeCallbacks(repeat[0]);
                if (repeated[0]) rebuild(); else backspace(); return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) { held[0] = false; mainHandler.removeCallbacks(repeat[0]); if (repeated[0]) rebuild(); return true; }
            return true;
        });
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER); r.setPadding(dp(2), dp(2), dp(2), dp(2)); return r; }
    private void rebuild() { if (root == null) return; View v = buildView(); setInputView(v); applyImeWindowHeight(); if (scanning) startInlineCamera(); }
    private View buildView() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(5), dp(3), dp(5), dp(4));
        root.setBackgroundColor(0xFFE9EDF2);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, dp(KEYBOARD_HEIGHT_DP)));
        if (scanning) buildScanner(); else if (toolPage != null) buildToolPanel(); else buildKeyboard(); return root;
    }
    private void buildKeyboard() {
        LinearLayout toolbar = row(); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(3), 0, dp(3), dp(2));
        toolbar.addView(button("☷", 0xFFD7DCE2, 0xFF202124, 19, () -> { toolPage = "tools"; rebuild(); }), new LinearLayout.LayoutParams(dp(40), dp(32)));
        TextView brand = text("Barcode Keyboard", 13, 0xFF59636E); brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, dp(34), 1));
        toolbar.addView(button("▦ 掃碼", 0xFF07C160, Color.WHITE, 13, this::requestScanner), new LinearLayout.LayoutParams(dp(74), dp(32)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(50), dp(32)); p.setMargins(dp(5), 0, 0, 0);
        toolbar.addView(button("商品", 0xFFD7DCE2, 0xFF202124, 12, this::openProductImport), p);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(36)));

        TextView englishLabel = text("英文直輸", 12, 0xFF77818A); englishLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        root.addView(englishLabel, new LinearLayout.LayoutParams(-1, dp(28)));
        buildLetterKeys();
        View spacer = new View(this); root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private void buildToolPanel() {
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("‹", 0xFFD5D9DE, 0xFF202124, 22, () -> { toolPage = null; rebuild(); }), new LinearLayout.LayoutParams(dp(42), dp(32)));
        String title = "tools".equals(toolPage) ? "快捷工具" : "clipboard".equals(toolPage) ? "剪貼簿（本機）" : "phrases".equals(toolPage) ? "常用語" : "emoji".equals(toolPage) ? "表情" : "cursor".equals(toolPage) ? "游標控制" : "鍵盤設定";
        header.addView(text(title, 15, 0xFF26313B), new LinearLayout.LayoutParams(0, dp(32), 1));
        header.addView(button("▦ 掃碼", 0xFF07C160, Color.WHITE, 12, () -> { toolPage = null; requestScanner(); }), new LinearLayout.LayoutParams(dp(72), dp(32)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));
        if ("tools".equals(toolPage)) {
            String[][] tools = {{"剪貼簿", "clipboard"}, {"常用語", "phrases"}, {"表情", "emoji"}, {"游標", "cursor"}, {"設定", "settings"}, {"商品資料", "products"}};
            for (int i = 0; i < tools.length; i += 3) {
                LinearLayout r = row();
                for (int j = i; j < Math.min(i + 3, tools.length); j++) {
                    String page = tools[j][1]; Button b = button(tools[j][0], Color.WHITE, 0xFF25313C, 14, () -> {
                        if (page.equals("products")) { toolPage = null; openProductImport(); } else { toolPage = page; rebuild(); }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1); lp.setMargins(dp(3), dp(2), dp(3), dp(2)); r.addView(b, lp);
                }
                root.addView(r, new LinearLayout.LayoutParams(-1, dp(58)));
            }
            root.addView(text("本機快捷工具；商品掃描仍可隨時使用", 11, 0xFF75808B), new LinearLayout.LayoutParams(-1, dp(24)));
        } else if ("clipboard".equals(toolPage)) buildClipboardPanel();
        else if ("phrases".equals(toolPage)) buildPhrasesPanel();
        else if ("emoji".equals(toolPage)) buildEmojiPanel();
        else if ("cursor".equals(toolPage)) buildCursorPanel();
        else buildSettingsPanel();
    }
    private JSONArray toolArray(String key) {
        try { return new JSONArray(getSharedPreferences("keyboard_tools", MODE_PRIVATE).getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    private void buildClipboardPanel() {
        LinearLayout actions = row();
        actions.addView(button("讀取目前剪貼簿", 0xFF07C160, Color.WHITE, 13, this::saveCurrentClipboard), new LinearLayout.LayoutParams(0, dp(34), 1));
        actions.addView(button("清空", 0xFFD5D9DE, 0xFF202124, 12, () -> { getSharedPreferences("keyboard_tools", MODE_PRIVATE).edit().putString("clipboard", "[]").apply(); rebuild(); }), new LinearLayout.LayoutParams(dp(62), dp(34)));
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(38)));
        JSONArray history = toolArray("clipboard");
        if (history.length() == 0) root.addView(text("點按上方按鈕後，複製文字會保存在本機。", 12, 0xFF727D88), new LinearLayout.LayoutParams(-1, dp(40)));
        for (int i = 0; i < Math.min(history.length(), 4); i++) {
            String value = history.optString(i, ""); if (value.isEmpty()) continue;
            Button item = button(value, Color.WHITE, 0xFF202124, 12, () -> { commit(value); toolPage = null; rebuild(); }); item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            root.addView(item, new LinearLayout.LayoutParams(-1, dp(36)));
        }
    }
    private void saveCurrentClipboard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) { Toast.makeText(this, "剪貼簿沒有文字", Toast.LENGTH_SHORT).show(); return; }
        ClipData clip = manager.getPrimaryClip(); if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this); if (text == null || text.toString().trim().isEmpty()) return;
        String value = text.toString().trim(); JSONArray old = toolArray("clipboard"), next = new JSONArray(); next.put(value);
        for (int i = 0; i < old.length() && next.length() < 20; i++) { String v = old.optString(i, ""); if (!v.equals(value)) next.put(v); }
        getSharedPreferences("keyboard_tools", MODE_PRIVATE).edit().putString("clipboard", next.toString()).apply(); rebuild();
    }
    private void buildPhrasesPanel() {
        root.addView(button("＋ 新增常用語", 0xFF07C160, Color.WHITE, 14, this::addPhrase), new LinearLayout.LayoutParams(-1, dp(36)));
        JSONArray phrases = toolArray("phrases");
        if (phrases.length() == 0) root.addView(text("點按輸入；長按項目可刪除。", 12, 0xFF727D88), new LinearLayout.LayoutParams(-1, dp(32)));
        for (int i = 0; i < Math.min(phrases.length(), 5); i++) {
            String value = phrases.optString(i, ""); if (value.isEmpty()) continue;
            Button item = button(value, Color.WHITE, 0xFF202124, 12, () -> { commit(value); toolPage = null; rebuild(); }); item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setMessage("刪除此常用語？").setNegativeButton("取消", null).setPositiveButton("刪除", (d,w) -> removePhrase(value)).show(); return true; });
            root.addView(item, new LinearLayout.LayoutParams(-1, dp(33)));
        }
    }
    private void addPhrase() {
        EditText input = new EditText(this); input.setHint("輸入要保存的文字");
        new AlertDialog.Builder(this).setTitle("新增常用語").setView(input).setNegativeButton("取消", null).setPositiveButton("保存", (d,w) -> {
            String value = input.getText().toString().trim(); if (!value.isEmpty()) { JSONArray a = toolArray("phrases"); a.put(value); getSharedPreferences("keyboard_tools", MODE_PRIVATE).edit().putString("phrases", a.toString()).apply(); rebuild(); }
        }).show();
    }
    private void removePhrase(String remove) {
        JSONArray old = toolArray("phrases"), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) { String value = old.optString(i, ""); if (!value.equals(remove)) next.put(value); }
        getSharedPreferences("keyboard_tools", MODE_PRIVATE).edit().putString("phrases", next.toString()).apply(); rebuild();
    }
    private void buildEmojiPanel() {
        String[] emojis = {"😀","😁","😂","🤣","😊","😍","🥰","😘","😎","😭","😅","🙏","👍","👏","❤️","💯","🎉","✨","🔥","✅","❌","🙂","😉","🤔"};
        for (int i = 0; i < emojis.length; i += 6) {
            LinearLayout r = row(); for (int j = i; j < Math.min(i + 6, emojis.length); j++) {
                String value = emojis[j]; r.addView(button(value, Color.WHITE, 0xFF202124, 22, () -> { commit(value); toolPage = null; rebuild(); }), new LinearLayout.LayoutParams(0, dp(42), 1));
            } root.addView(r, new LinearLayout.LayoutParams(-1, dp(45)));
        }
    }
    private void buildCursorPanel() {
        LinearLayout r = row();
        r.addView(button("◀", Color.WHITE, 0xFF202124, 20, () -> moveCursor(KeyEvent.KEYCODE_DPAD_LEFT, false)), new LinearLayout.LayoutParams(0, dp(52), 1));
        r.addView(button("▶", Color.WHITE, 0xFF202124, 20, () -> moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT, false)), new LinearLayout.LayoutParams(0, dp(52), 1));
        r.addView(button("選左", 0xFFD5D9DE, 0xFF202124, 12, () -> moveCursor(KeyEvent.KEYCODE_DPAD_LEFT, true)), new LinearLayout.LayoutParams(0, dp(52), 1));
        r.addView(button("選右", 0xFFD5D9DE, 0xFF202124, 12, () -> moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT, true)), new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(r, new LinearLayout.LayoutParams(-1, dp(58)));
    }
    private void moveCursor(int keyCode, boolean select) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return; int meta = select ? KeyEvent.META_SHIFT_ON : 0;
        ic.sendKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_DOWN,keyCode,0,meta)); ic.sendKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_UP,keyCode,0,meta));
    }
    private void buildSettingsPanel() {
        android.content.SharedPreferences prefs = getSharedPreferences("keyboard_settings", MODE_PRIVATE);
        root.addView(button("觸感回饋：" + (prefs.getBoolean("haptics", false) ? "開" : "關"), Color.WHITE, 0xFF202124, 14, () -> { prefs.edit().putBoolean("haptics", !prefs.getBoolean("haptics", false)).apply(); rebuild(); }), new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(button("按鍵聲音：" + (prefs.getBoolean("key_sound", false) ? "開" : "關"), Color.WHITE, 0xFF202124, 14, () -> { prefs.edit().putBoolean("key_sound", !prefs.getBoolean("key_sound", false)).apply(); rebuild(); }), new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(text("鍵盤與掃描面板維持相同高度。", 12, 0xFF727D88), new LinearLayout.LayoutParams(-1, dp(28)));
    }
    private Button enterButton(int width) { return button("↵ 換行", 0xFF07C160, Color.WHITE, 13, this::enter, width); }
    private Button button(String label, int color, int fg, int size, Runnable action, int width) {
        Button b = button(label, color, fg, size, action); b.setMinWidth(dp(width)); b.setMinimumWidth(dp(width)); return b;
    }
    private void buildLetterKeys() {
        if (numericMode) {
            addKeyRow(new String[]{"1","2","3","4","5","6","7","8","9","0"});
            addKeyRow(new String[]{"@","#","$","%","&","-","+","(",")"});
            addKeyRow(new String[]{"/",":",";","!","?","'","\"","⌫"});
            LinearLayout bottom = row();
            bottom.addView(button("ABC", 0xFFD5D9DE, 0xFF202124, 13, () -> { numericMode = false; rebuild(); }), new LinearLayout.LayoutParams(dp(58), dp(46)));
            bottom.addView(button("空白", Color.WHITE, 0xFF202124, 14, this::space), new LinearLayout.LayoutParams(0, dp(46), 1));
            bottom.addView(enterButton(68), new LinearLayout.LayoutParams(dp(72), dp(46)));
            root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP))); return;
        }
        addKeyRow(new String[]{"q","w","e","r","t","y","u","i","o","p"});
        addKeyRow(new String[]{"a","s","d","f","g","h","j","k","l"});
        addKeyRow(new String[]{"⇧","z","x","c","v","b","n","m","⌫"});
        LinearLayout bottom = row();
        bottom.addView(button("?123", 0xFFD5D9DE, 0xFF202124, 12, () -> { numericMode = true; rebuild(); }), new LinearLayout.LayoutParams(dp(52), dp(46)));
        Button comma = button(",", Color.WHITE, 0xFF202124, 17, () -> commit(",")); LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(dp(38), dp(46)); c.setMargins(dp(3),0,dp(3),0); bottom.addView(comma,c);
        bottom.addView(button("空白", Color.WHITE, 0xFF202124, 14, this::space), new LinearLayout.LayoutParams(0, dp(46), 1));
        Button dot = button(".", Color.WHITE, 0xFF202124, 17, () -> commit(".")); LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(dp(38), dp(46)); d.setMargins(dp(3),0,dp(3),0); bottom.addView(dot,d);
        bottom.addView(enterButton(68), new LinearLayout.LayoutParams(dp(72), dp(46)));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP)));
    }
    private void addKeyRow(String[] labels) {
        LinearLayout r = row();
        for (String label : labels) {
            boolean shiftKey = label.equals("⇧"), deleteKey = label.equals("⌫");
            int bg = shiftKey && shifted ? 0xFFB8E6CC : (shiftKey || deleteKey ? 0xFFD5D9DE : Color.WHITE);
            Button b = button(label, bg, 0xFF202124, 17, () -> { if (shiftKey) { shifted = !shifted; rebuild(); } else typeKey(label); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), shiftKey || deleteKey ? 1.25f : 1f); lp.setMargins(dp(2), 0, dp(2), 0); r.addView(b, lp);
        }
        root.addView(r, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP)));
    }
    private void typeKey(String key) {
        if (key.equals("⌫")) { backspace(); return; }
        if (numericMode) { commit(key); return; }
        if (key.matches("[a-zA-Z]")) {
            commit(shifted ? key.toUpperCase(Locale.ROOT) : key.toLowerCase(Locale.ROOT));
            if (shifted) { shifted = false; rebuild(); }
        } else commit(key);
    }
    private void commit(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && value != null) ic.commitText(value, 1);
    }
    private void space() { commit(" "); }
    private void enter() {
        InputConnection ic = getCurrentInputConnection(); EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : (info.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (ic != null) { if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action); else ic.commitText("\n", 1); }
        rebuild();
    }
    private void backspace() {
        backspace(true);
    }
    private void backspace(boolean refresh) {
        InputConnection ic = getCurrentInputConnection(); if (ic != null) ic.deleteSurroundingText(1, 0);
        if (refresh) rebuild();
    }

    private void requestScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) enterScanner();
        else { Intent i = new Intent(this, ScannerActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
    }
    private void enterScanner() { scanning = true; scanHandled.set(false); rebuild(); }
    private void closeScanner() { scanning = false; stopCamera(); rebuild(); }
    private void openProductImport() { Intent i = new Intent(this, ProductImportActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
    private void buildScanner() {
        LinearLayout toolbar = row(); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(button("‹", 0xFFD6DBE1, 0xFF202124, 20, this::closeScanner), new LinearLayout.LayoutParams(dp(38), dp(32)));
        toolbar.addView(button("條碼", !textScan ? 0xFF07C160 : 0xFFD5D9DE, !textScan ? Color.WHITE : 0xFF202124, 12, () -> { textScan = false; scanHandled.set(false); rebuild(); }), new LinearLayout.LayoutParams(dp(54), dp(30)));
        toolbar.addView(button("文字", textScan ? 0xFF07C160 : 0xFFD5D9DE, textScan ? Color.WHITE : 0xFF202124, 12, () -> { textScan = true; scanHandled.set(false); rebuild(); }), new LinearLayout.LayoutParams(dp(54), dp(30)));
        String outputLabel = outputProductName ? "輸出品名" : "輸出條碼";
        toolbar.addView(button(outputLabel, 0xFFD5D9DE, 0xFF202124, 11, () -> { outputProductName = !outputProductName; rebuild(); }), new LinearLayout.LayoutParams(0, dp(30), 1));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(36)));
        previewStage = new FrameLayout(this); previewView = new PreviewView(this); previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewStage.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        TextView hint = text(textScan ? "將文字置於鏡頭中" : "將條碼置中；難辨識時會讀取下方數字", 12, Color.WHITE); hint.setBackgroundColor(0x88000000); hint.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); hp.bottomMargin = dp(8); previewStage.addView(hint, hp);
        root.addView(previewStage, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bottom = row(); bottom.addView(button("取消掃描", 0xFFD5D9DE, 0xFF202124, 12, this::closeScanner), new LinearLayout.LayoutParams(0, dp(32), 1));
        bottom.addView(button("商品資料", Color.WHITE, 0xFF202124, 12, this::openProductImport), new LinearLayout.LayoutParams(dp(94), dp(32)));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(36)));
    }
    private void startInlineCamera() {
        if (!scanning || previewView == null || lifecycle.getCurrentState() != Lifecycle.State.RESUMED) return;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                if (!scanning || previewView == null) return;
                cameraProvider = future.get(); cameraProvider.unbindAll();
                Preview preview = new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                imageAnalysis.setAnalyzer(cameraExecutor, proxy -> {
                    if (scanHandled.get()) { proxy.close(); return; }
                    android.media.Image media = proxy.getImage(); if (media == null) { proxy.close(); return; }
                    InputImage image = InputImage.fromMediaImage(media, proxy.getImageInfo().getRotationDegrees());
                    if (textScan) {
                        textRecognizer.process(image).addOnSuccessListener(result -> {
                            String value = result.getText().trim(); if (!value.isEmpty() && scanHandled.compareAndSet(false, true)) mainHandler.post(() -> deliverScan(value));
                        }).addOnCompleteListener(done -> proxy.close());
                    } else {
                        barcodeScanner.process(image).addOnSuccessListener(codes -> {
                            String decoded = null;
                            for (Barcode code : codes) { String value = code.getRawValue(); if (value != null && !value.trim().isEmpty()) { decoded = value.trim(); break; } }
                            if (decoded != null) {
                                String value = decoded;
                                if (scanHandled.compareAndSet(false, true)) mainHandler.post(() -> deliverScan(value));
                                proxy.close();
                            } else scanDigitsWithOcr(image, proxy);
                        }).addOnFailureListener(error -> scanDigitsWithOcr(image, proxy));
                    }
                });
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) { Toast.makeText(this, "無法啟動相機", Toast.LENGTH_SHORT).show(); closeScanner(); }
        }, ContextCompat.getMainExecutor(this));
    }
    private void deliverScan(String value) {
        boolean barcodeScan = !textScan;
        String output = value;
        if (outputProductName && !textScan) { String name = ProductCatalog.nameFor(this, value); if (!name.isEmpty()) output = name; else Toast.makeText(this, "找不到此條碼的商品資料，已輸入條碼", Toast.LENGTH_SHORT).show(); }
        commitReplacingPreviousScan(output); closeScanner();
        if (barcodeScan) enter();
    }
    private void scanDigitsWithOcr(InputImage image, ImageProxy proxy) {
        if (scanHandled.get()) { proxy.close(); return; }
        textRecognizer.process(image).addOnSuccessListener(result -> {
            String digits = bestPrintedDigits(result);
            if (digits != null && scanHandled.compareAndSet(false, true)) mainHandler.post(() -> deliverScan(digits));
        }).addOnCompleteListener(done -> proxy.close());
    }
    private String bestPrintedDigits(com.google.mlkit.vision.text.Text text) {
        String best = null;
        for (com.google.mlkit.vision.text.Text.TextBlock block : text.getTextBlocks()) {
            for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                String raw = line.getText().trim();
                // 只接受印刷碼常見的純數字／空格／連字號，避免從品名或價錢文字抽取數字。
                if (!raw.matches("[0-9][0-9\\s-]*")) continue;
                String digits = raw.replaceAll("[\\s-]", "");
                if (digits.length() < 5 || digits.length() > 18) continue;
                if (best == null || digits.length() > best.length()) best = digits;
            }
        }
        return best;
    }
    private void commitReplacingPreviousScan(String value) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        ic.beginBatchEdit();
        try {
            if (lastScannedText != null && !lastScannedText.isEmpty()) {
                CharSequence before = ic.getTextBeforeCursor(lastScannedText.length(), 0);
                if (before != null && before.toString().equals(lastScannedText)) ic.deleteSurroundingText(lastScannedText.length(), 0);
            }
            ic.commitText(value, 1); lastScannedText = value;
        } finally { ic.endBatchEdit(); }
    }
    private void stopCamera() { try { if (cameraProvider != null) cameraProvider.unbindAll(); } catch (Exception ignored) { } previewView = null; imageAnalysis = null; previewStage = null; }
}
