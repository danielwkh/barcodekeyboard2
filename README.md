# Barcode Keyboard 2

Android 輸入法鍵盤，提供基本 QWERTY 中／英文文字輸入，並可從鍵盤開啟相機掃描 QR Code／Barcode，辨識結果會直接輸入到目前使用的文字欄位。

## 使用方式
1. 安裝 `BarcodeKeyboard2-debug.apk`。
2. 到 Android「設定 → 系統 → 鍵盤／語言與輸入法 → 螢幕鍵盤」啟用 **Barcode Keyboard**。
3. 在需要輸入文字的 App 叫出鍵盤，透過輸入法切換按鈕選取 **Barcode Keyboard**。
4. 點鍵盤上的「掃描」，首次使用時允許相機權限；將 QR Code／條碼對準相機即可自動回填。

鍵盤可在「EN／中文」間切換字母輸入大小寫與顯示標記；中文標記是基礎鍵盤模式，不含中文拼音輸入法／詞庫，也不仿製微信的私有功能或品牌素材。掃描由裝置相機與 ML Kit 本機條碼辨識執行；相機畫面不會上傳本專案伺服器。

## 建置
使用 JDK 17+、Android SDK Platform 35 及 Gradle 8.7：

```sh
./gradlew assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。此自用版本為 Android debug 簽章；重新建置的 debug APK 不能直接覆蓋以不同簽章安裝的版本。
