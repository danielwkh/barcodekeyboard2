package com.danielwkh.barcodekeyboard2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BarcodeInputMethodService extends InputMethodService implements LifecycleOwner {
    private static BarcodeInputMethodService active;
    private LifecycleRegistry lifecycle;
    private LinearLayout root;
    private FrameLayout previewStage;
    private PreviewView previewView;
    private TextView candidateStrip;
    private ChineseInputEngine engine;
    private ChineseInputEngine.Mode mode = ChineseInputEngine.Mode.ENGLISH;
    private String composition = "";
    private boolean scanning = false;
    private boolean textScan = false;
    private boolean outputProductName = false;
    private boolean shifted = false;
    private boolean numericMode = false;
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
    @Override public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (lifecycle.getCurrentState() == Lifecycle.State.CREATED || lifecycle.getCurrentState() == Lifecycle.State.INITIALIZED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
        if (lifecycle.getCurrentState() == Lifecycle.State.STARTED) lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        if (scanning) startInlineCamera();
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

    static void cameraPermissionResolved(boolean granted) {
        if (active == null) return;
        if (granted) { active.enterScanner(); active.requestShowSelf(0); }
        else { active.scanning = false; active.rebuild(); Toast.makeText(active, "需要相機權限才能掃描", Toast.LENGTH_LONG).show(); }
    }
    public static void commitScannedValue(String value) { if (active != null) active.commit(value); }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER); return t;
    }
    private Button button(String label, int color, int fg, int size, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(size); b.setTextColor(fg); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(3), 0, dp(3), 0); b.setBackground(shape(color, 7));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER); r.setPadding(dp(2), dp(2), dp(2), dp(2)); return r; }
    private void rebuild() { if (root == null) return; View v = buildView(); setInputView(v); if (scanning) startInlineCamera(); }
    private View buildView() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(5), dp(3), dp(5), dp(4)); root.setBackgroundColor(0xFFE9EDF2);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, dp(264)));
        if (scanning) buildScanner(); else buildKeyboard();
        return root;
    }
    private void buildKeyboard() {
        LinearLayout toolbar = row(); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(3), 0, dp(3), dp(2));
        TextView brand = text("Barcode Keyboard", 13, 0xFF59636E); brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, dp(34), 1));
        Button scan = button("▦ 掃描", 0xFF07C160, Color.WHITE, 13, this::requestScanner);
        toolbar.addView(scan, new LinearLayout.LayoutParams(dp(82), dp(32)));
        Button products = button("商品", 0xFFD7DCE2, 0xFF202124, 12, this::openProductImport);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(54), dp(32)); p.setMargins(dp(5), 0, 0, 0); toolbar.addView(products, p);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(36)));

        HorizontalScrollView modeScroll = new HorizontalScrollView(this); modeScroll.setHorizontalScrollBarEnabled(false); LinearLayout tabs = row();
        addMode(tabs, "EN", ChineseInputEngine.Mode.ENGLISH, 46); addMode(tabs, "拼音", ChineseInputEngine.Mode.PINYIN, 58);
        addMode(tabs, "筆畫", ChineseInputEngine.Mode.STROKE, 58); addMode(tabs, "速成", ChineseInputEngine.Mode.QUICK, 58);
        modeScroll.addView(tabs); root.addView(modeScroll, new LinearLayout.LayoutParams(-1, dp(32)));

        HorizontalScrollView choices = new HorizontalScrollView(this); choices.setHorizontalScrollBarEnabled(false);
        LinearLayout choiceRow = row();
        if (!composition.isEmpty()) {
            TextView composing = text(composition, 13, 0xFF5D6670); choiceRow.addView(composing, new LinearLayout.LayoutParams(dp(48), dp(27)));
            List<String> candidates = engine.candidates(mode, composition);
            for (int i = 0; i < candidates.size(); i++) {
                final String candidate = candidates.get(i); int number = i + 1;
                Button item = button(number + " " + candidate, Color.WHITE, 0xFF18212B, 15, () -> selectCandidate(candidate));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(27)); clp.setMargins(dp(2), 0, dp(2), 0); choiceRow.addView(item, clp);
            }
            if (candidates.isEmpty()) {
                TextView tip = text(engine.isReady(mode) ? "無候選" : "正在載入字典…", 12, 0xFF77818A); choiceRow.addView(tip, new LinearLayout.LayoutParams(-2, dp(27)));
            }
        } else {
            TextView tip = text(mode == ChineseInputEngine.Mode.STROKE ? "筆畫：一丨丿丶乙，按序輸入" : mode == ChineseInputEngine.Mode.QUICK ? "倉頡速成：輸入字根碼首尾碼" : mode == ChineseInputEngine.Mode.PINYIN ? "輸入拼音，再選候選字" : "輸入英文", 12, 0xFF77818A);
            choiceRow.addView(tip, new LinearLayout.LayoutParams(-2, dp(27)));
        }
        choices.addView(choiceRow); root.addView(choices, new LinearLayout.LayoutParams(-1, dp(29)));

        if (mode == ChineseInputEngine.Mode.STROKE) buildStrokeKeys(); else buildLetterKeys();
    }
    private void addMode(LinearLayout tabs, String label, ChineseInputEngine.Mode selected, int width) {
        int bg = mode == selected ? 0xFF07C160 : 0xFFD9DEE4; int fg = mode == selected ? Color.WHITE : 0xFF26313B;
        Button b = button(label, bg, fg, 12, () -> { mode = selected; composition = ""; if (selected != ChineseInputEngine.Mode.ENGLISH) engine.ensureLoaded(selected, this::rebuild); rebuild(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(27)); lp.setMargins(dp(2), 0, dp(2), 0); tabs.addView(b, lp);
    }
    private void buildLetterKeys() {
        if (numericMode) {
            addKeyRow(new String[]{"1","2","3","4","5","6","7","8","9","0"});
            addKeyRow(new String[]{"@","#","$","%","&","-","+","(",")"});
            addKeyRow(new String[]{"/",":",";","!","?","'","\"","⌫"});
            LinearLayout numericBottom = row();
            numericBottom.addView(button("ABC", 0xFFD5D9DE, 0xFF202124, 12, () -> { numericMode = false; rebuild(); }), new LinearLayout.LayoutParams(dp(52), dp(36)));
            numericBottom.addView(button("空白", Color.WHITE, 0xFF202124, 13, this::space), new LinearLayout.LayoutParams(0, dp(36), 1));
            numericBottom.addView(button("↵", 0xFF07C160, Color.WHITE, 17, this::enter), new LinearLayout.LayoutParams(dp(44), dp(36)));
            root.addView(numericBottom, new LinearLayout.LayoutParams(-1, dp(40))); return;
        }
        addKeyRow(new String[]{"q","w","e","r","t","y","u","i","o","p"});
        addKeyRow(new String[]{"a","s","d","f","g","h","j","k","l"});
        addKeyRow(new String[]{"⇧","z","x","c","v","b","n","m","⌫"});
        LinearLayout bottom = row();
        bottom.addView(button("?123", 0xFFD5D9DE, 0xFF202124, 12, () -> { numericMode = true; rebuild(); }), new LinearLayout.LayoutParams(dp(48), dp(36)));
        Button comma = button(",", Color.WHITE, 0xFF202124, 16, () -> punctuation(",")); LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(dp(34), dp(36)); c.setMargins(dp(3),0,dp(3),0); bottom.addView(comma,c);
        bottom.addView(button("空白", Color.WHITE, 0xFF202124, 13, this::space), new LinearLayout.LayoutParams(0, dp(36), 1));
        Button dot = button(".", Color.WHITE, 0xFF202124, 16, () -> punctuation(".")); LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(dp(34), dp(36)); d.setMargins(dp(3),0,dp(3),0); bottom.addView(dot,d);
        bottom.addView(button("↵", 0xFF07C160, Color.WHITE, 17, this::enter), new LinearLayout.LayoutParams(dp(44), dp(36)));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(40)));
    }
    private void addKeyRow(String[] labels) {
        LinearLayout r = row(); for (String label : labels) {
            boolean special = label.equals("⇧") || label.equals("⌫");
            int bg = special ? (label.equals("⇧") && shifted ? 0xFFB8E6CC : 0xFFD5D9DE) : Color.WHITE;
            Button b = button(label, bg, 0xFF202124, 16, () -> { if (label.equals("⇧")) { shifted = !shifted; rebuild(); } else typeKey(label); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), special ? 1.25f : 1f); lp.setMargins(dp(2), 0, dp(2), 0); r.addView(b, lp);
        }
        root.addView(r, new LinearLayout.LayoutParams(-1, dp(40)));
    }
    private void buildStrokeKeys() {
        String[][] strokes = {{"一","h"},{"丨","s"},{"丿","p"},{"丶","n"},{"乙","z"}};
        LinearLayout r = row(); for (String[] stroke : strokes) {
            Button b = button(stroke[0], Color.WHITE, 0xFF202124, 22, () -> typeKey(stroke[1]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(45), 1); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); r.addView(b, lp);
        }
        root.addView(r, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout bottom = row();
        bottom.addView(button("刪除", 0xFFD5D9DE, 0xFF202124, 13, this::backspace), new LinearLayout.LayoutParams(dp(56), dp(36)));
        bottom.addView(button("空白／選字", Color.WHITE, 0xFF202124, 13, this::space), new LinearLayout.LayoutParams(0, dp(36), 1));
        bottom.addView(button("↵", 0xFF07C160, Color.WHITE, 16, this::enter), new LinearLayout.LayoutParams(dp(44), dp(36)));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(40)));
    }
    private void typeKey(String key) {
        if (key.equals("⇧")) return;
        if (key.equals("⌫")) { backspace(); return; }
        if (mode == ChineseInputEngine.Mode.ENGLISH || numericMode) {
            commit(mode == ChineseInputEngine.Mode.ENGLISH && shifted ? key.toUpperCase(Locale.ROOT) : key.toLowerCase(Locale.ROOT));
            if (shifted) { shifted = false; rebuild(); } return;
        }
        modeEnsureThenAppend(key.toLowerCase(Locale.ROOT));
    }
    private void modeEnsureThenAppend(String key) {
        if (mode == ChineseInputEngine.Mode.STROKE && !key.matches("[hspnz]")) return;
        if (mode == ChineseInputEngine.Mode.PINYIN && !key.matches("[a-z]")) return;
        if (mode == ChineseInputEngine.Mode.QUICK && !key.matches("[a-z]")) return;
        composition += key; engine.ensureLoaded(mode, this::rebuild); rebuild();
    }
    private void selectCandidate(String value) { commit(value); composition = ""; rebuild(); }
    private void commit(String value) { InputConnection ic = getCurrentInputConnection(); if (ic != null && value != null) ic.commitText(value, 1); }
    private void space() {
        if (!composition.isEmpty()) { List<String> c = engine.candidates(mode, composition); if (!c.isEmpty()) { selectCandidate(c.get(0)); return; } }
        commit(" "); composition = ""; rebuild();
    }
    private void punctuation(String value) { if (!composition.isEmpty()) space(); commit(value); }
    private void enter() { if (!composition.isEmpty()) space(); commit("\n"); }
    private void backspace() {
        if (!composition.isEmpty()) composition = composition.substring(0, composition.length() - 1);
        else { InputConnection ic = getCurrentInputConnection(); if (ic != null) ic.deleteSurroundingText(1, 0); }
        rebuild();
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
                    if (textScan) {
                        task = textRecognizer.process(image).addOnSuccessListener(result -> {
                            String value = result.getText().trim(); if (!value.isEmpty() && scanHandled.compareAndSet(false, true)) mainHandler.post(() -> deliverScan(value));
                        });
                    } else {
                        task = barcodeScanner.process(image).addOnSuccessListener(codes -> {
                            for (Barcode code : codes) { String value = code.getRawValue(); if (value != null && !value.isEmpty() && scanHandled.compareAndSet(false, true)) { mainHandler.post(() -> deliverScan(value)); break; } }
                        });
                    }
                    task.addOnCompleteListener(done -> proxy.close());
                });
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) { Toast.makeText(this, "無法啟動相機", Toast.LENGTH_SHORT).show(); closeScanner(); }
        }, ContextCompat.getMainExecutor(this));
    }
    private void deliverScan(String value) {
        String output = value;
        if (outputProductName && !textScan) { String name = ProductCatalog.nameFor(this, value); if (!name.isEmpty()) output = name; else Toast.makeText(this, "找不到此條碼的商品資料，已輸入條碼", Toast.LENGTH_SHORT).show(); }
        commit(output); closeScanner();
    }
    private void stopCamera() { try { if (cameraProvider != null) cameraProvider.unbindAll(); } catch (Exception ignored) { } previewView = null; imageAnalysis = null; previewStage = null; }
}
