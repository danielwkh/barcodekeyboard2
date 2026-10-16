package com.danielwkh.barcodekeyboard2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScannerActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 71;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private final AtomicBoolean handled = new AtomicBoolean(false);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        cameraExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient();
        FrameLayout frame = new FrameLayout(this);
        previewView = new PreviewView(this); previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        frame.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        TextView hint = new TextView(this); hint.setText("將 QR Code 或條碼置於框內\n辨識成功後會自動輸入");
        hint.setTextColor(Color.WHITE); hint.setTextSize(18); hint.setGravity(Gravity.CENTER);
        hint.setPadding(20, 18, 20, 18); hint.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        frame.addView(hint, hp); setContentView(frame);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (handled.get()) { imageProxy.close(); return; }
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (mediaImage == null) { imageProxy.close(); return; }
                    InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    scanner.process(image).addOnSuccessListener(codes -> {
                        for (Barcode code : codes) {
                            String value = code.getRawValue();
                            if (value != null && !value.isEmpty() && handled.compareAndSet(false, true)) {
                                runOnUiThread(() -> { BarcodeInputMethodService.commitScannedValue(value); finish(); }); break;
                            }
                        }
                    }).addOnFailureListener(e -> {}).addOnCompleteListener(task -> imageProxy.close());
                });
                provider.unbindAll(); provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) { Toast.makeText(this, "無法啟動相機", Toast.LENGTH_LONG).show(); finish(); }
        }, ContextCompat.getMainExecutor(this));
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else { Toast.makeText(this, "需要相機權限才能掃描條碼", Toast.LENGTH_LONG).show(); finish(); }
    }
    @Override protected void onDestroy() {
        super.onDestroy(); if (scanner != null) scanner.close(); if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
