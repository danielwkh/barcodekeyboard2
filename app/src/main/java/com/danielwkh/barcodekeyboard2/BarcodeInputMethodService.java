package com.danielwkh.barcodekeyboard2;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
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
    private ChineseInputEngine engine;
    private ChineseInputEngine.Mode mode = ChineseInputEngine.Mode.ENGLISH;
    private String composition = "";
    private boolean scanning = false;
    private boolean textScan = false;
    private boolean outputProductName = false;
    private boolean shifted = false;
    private boolean numericMode = false;
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
        engine = new ChineseInputEngine(this); cameraExecutor = Executors.newSingleThreadExecutor();
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
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(3), 0, dp(3), 0); b.setBackground(shape(color, 8)); b.setOnClickListener(v -> action.run());
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
        root.setBackgroundColor(mode == ChineseInputEngine.Mode.STROKE && !scanning ? 0xFF202124 : 0xFFE9EDF2);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, dp(KEYBOARD_HEIGHT_DP)));
        if (scanning) buildScanner(); else buildKeyboard(); return root;
    }
    private void buildKeyboard() {
        LinearLayout toolbar = row(); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(3), 0, dp(3), dp(2));
        TextView brand = text("Barcode Keyboard", 13, 0xFF59636E); brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, dp(34), 1));
        toolbar.addView(button("▦ 掃描", 0xFF07C160, Color.WHITE, 13, this::requestScanner), new LinearLayout.LayoutParams(dp(82), dp(32)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(54), dp(32)); p.setMargins(dp(5), 0, 0, 0);
        toolbar.addView(button("商品", 0xFFD7DCE2, 0xFF202124, 12, this::openProductImport), p);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(36)));

        HorizontalScrollView modeScroll = new HorizontalScrollView(this); modeScroll.setHorizontalScrollBarEnabled(false); LinearLayout tabs = row();
        addMode(tabs, "EN", ChineseInputEngine.Mode.ENGLISH, 46); addMode(tabs, "拼音", ChineseInputEngine.Mode.PINYIN, 58);
        addMode(tabs, "筆畫", ChineseInputEngine.Mode.STROKE, 58); addMode(tabs, "速成", ChineseInputEngine.Mode.QUICK, 58);
        modeScroll.addView(tabs); root.addView(modeScroll, new LinearLayout.LayoutParams(-1, dp(32)));

        HorizontalScrollView choices = new HorizontalScrollView(this); choices.setHorizontalScrollBarEnabled(false); LinearLayout choiceRow = row();
        if (!composition.isEmpty()) {
            TextView composing = text(composition, 13, mode == ChineseInputEngine.Mode.STROKE ? 0xFFE9EDF2 : 0xFF5D6670); choiceRow.addView(composing, new LinearLayout.LayoutParams(dp(48), dp(29)));
            List<String> candidates = engine.candidates(mode, composition);
            for (int i = 0; i < candidates.size(); i++) {
                final String candidate = candidates.get(i); Button item = button((i + 1) + " " + candidate, Color.WHITE, 0xFF18212B, 15, () -> selectCandidate(candidate));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(29)); clp.setMargins(dp(2), 0, dp(2), 0); choiceRow.addView(item, clp);
            }
            if (candidates.isEmpty()) choiceRow.addView(text(engine.isReady(mode) ? "無候選" : "載入字典…", 12, 0xFF77818A), new LinearLayout.LayoutParams(-2, dp(29)));
        } else {
            String tip = mode == ChineseInputEngine.Mode.STROKE ? "常用筆畫候選：一丨丿丶乙" : mode == ChineseInputEngine.Mode.QUICK ? "速成候選按倉頡首尾碼排序" : mode == ChineseInputEngine.Mode.PINYIN ? "拼音常用字／詞候選" : "輸入英文顯示常用詞建議";
            choiceRow.addView(text(tip, 12, mode == ChineseInputEngine.Mode.STROKE ? 0xFFE9EDF2 : 0xFF77818A), new LinearLayout.LayoutParams(-2, dp(29)));
        }
        choices.addView(choiceRow); root.addView(choices, new LinearLayout.LayoutParams(-1, dp(31)));
        if (mode == ChineseInputEngine.Mode.STROKE && !numericMode) buildStrokeKeys(); else buildLetterKeys();
        View spacer = new View(this); root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private void addMode(LinearLayout tabs, String label, ChineseInputEngine.Mode selected, int width) {
        int bg = mode == selected ? 0xFF07C160 : 0xFFD9DEE4; int fg = mode == selected ? Color.WHITE : 0xFF26313B;
        Button b = button(label, bg, fg, 12, () -> { mode = selected; composition = ""; numericMode = false; if (selected != ChineseInputEngine.Mode.ENGLISH) engine.ensureLoaded(selected, this::rebuild); rebuild(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(27)); lp.setMargins(dp(2), 0, dp(2), 0); tabs.addView(b, lp);
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
            bottom.addView(enterButton(68), new LinearLayout.LayoutParams(dp(72), dp(46))); root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP))); return;
        }
        addKeyRow(new String[]{"q","w","e","r","t","y","u","i","o","p"});
        addKeyRow(new String[]{"a","s","d","f","g","h","j","k","l"});
        addKeyRow(new String[]{"⇧","z","x","c","v","b","n","m","⌫"});
        LinearLayout bottom = row();
        bottom.addView(button("?123", 0xFFD5D9DE, 0xFF202124, 12, () -> { numericMode = true; rebuild(); }), new LinearLayout.LayoutParams(dp(52), dp(46)));
        Button comma = button(",", Color.WHITE, 0xFF202124, 17, () -> punctuation(",")); LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(dp(38), dp(46)); c.setMargins(dp(3),0,dp(3),0); bottom.addView(comma,c);
        bottom.addView(button("空白", Color.WHITE, 0xFF202124, 14, this::space), new LinearLayout.LayoutParams(0, dp(46), 1));
        Button dot = button(".", Color.WHITE, 0xFF202124, 17, () -> punctuation(".")); LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(dp(38), dp(46)); d.setMargins(dp(3),0,dp(3),0); bottom.addView(dot,d);
        bottom.addView(enterButton(68), new LinearLayout.LayoutParams(dp(72), dp(46))); root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP)));
    }
    private void addKeyRow(String[] labels) {
        LinearLayout r = row(); for (String label : labels) {
            boolean special = label.equals("⇧") || label.equals("⌫"); int bg = special ? (label.equals("⇧") && shifted ? 0xFFB8E6CC : 0xFFD5D9DE) : Color.WHITE;
            Button b = button(label, bg, 0xFF202124, 17, () -> { if (label.equals("⇧")) { shifted = !shifted; rebuild(); } else typeKey(label); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), special ? 1.25f : 1f); lp.setMargins(dp(2), 0, dp(2), 0); r.addView(b, lp);
        }
        root.addView(r, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP)));
    }
    private void buildStrokeKeys() {
        addStrokeGridRow(new String[][]{{"一","h"},{"丨","s"},{"丿","p"},{"⌫","back"}});
        addStrokeGridRow(new String[][]{{"丶","n"},{"乙","z"},{"清除","clear"},{"換行","enter"}});
        addStrokeGridRow(new String[][]{{"@#","symbols"},{",","comma"},{"ABC","english"},{"☺","smile"}});
        LinearLayout bottom = row();
        bottom.addView(button("符號", 0xFF414141, Color.WHITE, 13, () -> { numericMode = true; rebuild(); }), new LinearLayout.LayoutParams(0, dp(46), 1));
        bottom.addView(button("123", 0xFF414141, Color.WHITE, 13, () -> { numericMode = true; rebuild(); }), new LinearLayout.LayoutParams(0, dp(46), 1));
        bottom.addView(button("空白／選字", 0xFF5D5D5D, Color.WHITE, 13, this::space), new LinearLayout.LayoutParams(0, dp(46), 2.2f));
        bottom.addView(button("🌐", 0xFF414141, Color.WHITE, 18, this::cycleMode), new LinearLayout.LayoutParams(0, dp(46), 1));
        bottom.addView(enterButton(0), new LinearLayout.LayoutParams(0, dp(46), 1.6f));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP)));
    }
    private void addStrokeGridRow(String[][] keys) {
        LinearLayout r = row(); for (String[] key : keys) {
            boolean action = key[0].equals("⌫") || key[0].equals("換行") || key[0].equals("清除");
            int bg = action ? (key[0].equals("換行") ? 0xFF414141 : 0xFF414141) : 0xFF5D5D5D;
            int size = key[0].length() > 1 ? 17 : 31;
            Button b = button(key[0], bg, Color.WHITE, size, () -> strokeAction(key[1]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1); lp.setMargins(dp(4), dp(3), dp(4), dp(3)); r.addView(b, lp);
        }
        root.addView(r, new LinearLayout.LayoutParams(-1, dp(KEY_ROW_HEIGHT_DP)));
    }
    private void strokeAction(String action) {
        switch (action) {
            case "h": case "s": case "p": case "n": case "z": modeEnsureThenAppend(action); break;
            case "back": backspace(); break;
            case "clear": composition = ""; rebuild(); break;
            case "enter": enter(); break;
            case "symbols": numericMode = true; rebuild(); break;
            case "comma": punctuation(","); break;
            case "english": mode = ChineseInputEngine.Mode.ENGLISH; composition = ""; rebuild(); break;
            case "smile": commit("☺"); break;
        }
    }
    private void cycleMode() {
        ChineseInputEngine.Mode[] modes = ChineseInputEngine.Mode.values(); mode = modes[(mode.ordinal() + 1) % modes.length]; composition = ""; numericMode = false;
        if (mode != ChineseInputEngine.Mode.ENGLISH) engine.ensureLoaded(mode, this::rebuild); rebuild();
    }
    private void typeKey(String key) {
        if (key.equals("⇧")) return; if (key.equals("⌫")) { backspace(); return; }
        if (numericMode) { commit(key); return; }
        if (mode == ChineseInputEngine.Mode.ENGLISH) {
            if (key.matches("[a-zA-Z]")) { composition += shifted ? key.toUpperCase(Locale.ROOT) : key.toLowerCase(Locale.ROOT); shifted = false; rebuild(); }
            else commit(key); return;
        }
        modeEnsureThenAppend(key.toLowerCase(Locale.ROOT));
    }
    private void modeEnsureThenAppend(String key) {
        if (mode == ChineseInputEngine.Mode.STROKE && !key.matches("[hspnz]")) return;
        if ((mode == ChineseInputEngine.Mode.PINYIN || mode == ChineseInputEngine.Mode.QUICK) && !key.matches("[a-z]")) return;
        composition += key; engine.ensureLoaded(mode, this::rebuild); rebuild();
    }
    private void selectCandidate(String value) {
        String output = value;
        if (mode == ChineseInputEngine.Mode.ENGLISH && !composition.isEmpty() && Character.isUpperCase(composition.charAt(0))) output = value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
        commit(output); composition = ""; rebuild();
    }
    private void commit(String value) { InputConnection ic = getCurrentInputConnection(); if (ic != null && value != null) ic.commitText(value, 1); }
    private void flushComposition(boolean chooseChineseCandidate) {
        if (composition.isEmpty()) return;
        if (chooseChineseCandidate && mode != ChineseInputEngine.Mode.ENGLISH) {
            List<String> candidates = engine.candidates(mode, composition); if (!candidates.isEmpty()) { commit(candidates.get(0)); composition = ""; return; }
        }
        commit(composition); composition = "";
    }
    private void space() {
        if (mode == ChineseInputEngine.Mode.ENGLISH) { flushComposition(false); commit(" "); rebuild(); return; }
        if (!composition.isEmpty()) {
            List<String> candidates = engine.candidates(mode, composition);
            if (!candidates.isEmpty()) commit(candidates.get(0)); else { commit(composition); commit(" "); }
            composition = ""; rebuild(); return;
        }
        commit(" ");
    }
    private void punctuation(String value) { if (!composition.isEmpty()) flushComposition(mode != ChineseInputEngine.Mode.ENGLISH); commit(value); rebuild(); }
    private void enter() {
        flushComposition(mode != ChineseInputEngine.Mode.ENGLISH);
        InputConnection ic = getCurrentInputConnection(); EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : (info.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (ic != null) { if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action); else ic.commitText("\n", 1); }
        composition = ""; rebuild();
    }
    private void backspace() {
        backspace(true);
    }
    private void backspace(boolean refresh) {
        if (!composition.isEmpty()) composition = composition.substring(0, composition.length() - 1);
        else { InputConnection ic = getCurrentInputConnection(); if (ic != null) ic.deleteSurroundingText(1, 0); }
        if (refresh) rebuild();
    }

    private void requestScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) enterScanner();
        else { Intent i = new Intent(this, ScannerActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
    }
    private void enterScanner() { composition = ""; scanning = true; scanHandled.set(false); rebuild(); }
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
        TextView hint = text(textScan ? "將文字置於鏡頭中" : "將 QR Code／條碼置於鏡頭中", 12, Color.WHITE); hint.setBackgroundColor(0x88000000); hint.setPadding(dp(8), dp(4), dp(8), dp(4));
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
                    com.google.android.gms.tasks.Task<?> task;
                    if (textScan) task = textRecognizer.process(image).addOnSuccessListener(result -> {
                        String value = result.getText().trim(); if (!value.isEmpty() && scanHandled.compareAndSet(false, true)) mainHandler.post(() -> deliverScan(value));
                    });
                    else task = barcodeScanner.process(image).addOnSuccessListener(codes -> {
                        for (Barcode code : codes) { String value = code.getRawValue(); if (value != null && !value.isEmpty() && scanHandled.compareAndSet(false, true)) { mainHandler.post(() -> deliverScan(value)); break; } }
                    });
                    task.addOnCompleteListener(done -> proxy.close());
                });
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) { Toast.makeText(this, "無法啟動相機", Toast.LENGTH_SHORT).show(); closeScanner(); }
        }, ContextCompat.getMainExecutor(this));
    }
    private void deliverScan(String value) {
        boolean barcodeScan = !textScan;
        String output = value;
        if (outputProductName && !textScan) { String name = ProductCatalog.nameFor(this, value); if (!name.isEmpty()) output = name; else Toast.makeText(this, "找不到此條碼的商品資料，已輸入條碼", Toast.LENGTH_SHORT).show(); }
        composition = ""; commitReplacingPreviousScan(output); closeScanner();
        if (barcodeScan) enter();
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
