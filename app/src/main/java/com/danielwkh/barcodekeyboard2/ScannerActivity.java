package com.danielwkh.barcodekeyboard2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Only requests camera permission; the live scanner is rendered inside the IME-sized keyboard view. */
public class ScannerActivity extends Activity {
    private static final int CAMERA_REQUEST = 71;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            BarcodeInputMethodService.cameraPermissionResolved(true); finish();
        } else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = requestCode == CAMERA_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        BarcodeInputMethodService.cameraPermissionResolved(granted);
        finish();
    }
}
