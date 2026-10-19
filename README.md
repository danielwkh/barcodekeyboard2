# Barcode Keyboard 2

自用 Android 英文輸入法，字母按下即直接輸出、不顯示候選字。v1.6.0 進一步整理鍵盤和掃描介面，並加強相機與大型商品檔處理。

## 功能

- **英文直輸**：QWERTY 字母即按即輸；Shift 會同步顯示大寫鍵帽，並遵從輸入欄自動大寫提示。數字、電話和日期欄位會自動切換至數字／符號版面，提供小數點、逗號、空格、長按刪除及 Enter。
- **依欄位顯示 Enter**：鍵盤會依文字欄位顯示「搜尋」「完成」「前往」「傳送」「下一項」或換行；掃描條碼後執行欄位設定的 Enter 動作。
- **鍵盤內掃描**：相機預覽保持在鍵盤高度，新增條碼取景框與手電筒控制。掃描器一次處理一張影格，避免 OCR 併發造成卡頓；條碼難辨時會嘗試 OCR 讀取下方數字。
- **商品 XLSX 管理**：從 Android 文件選擇器匯入商品名稱、條碼及第二條碼。解析在背景執行，匯入頁顯示進度和結果；可在確認後清空本機商品目錄。原始 XLSX 不複製進 App，也不打包進 APK。
- **快捷工具**：本機剪貼簿歷史、常用語、表情、游標控制、觸感／按鍵聲設定及線上更新。
- **線上更新**：從 GitHub 公開 Release 檢查新版；下載由使用者主動開始，最後由 Android 安裝器顯示確認，不會靜默安裝。
- **隱私**：鍵盤內容、剪貼簿和商品目錄留在裝置本機；更新器只連線 GitHub Release API 和該 repo 的 APK 附件。

## 安裝

1. 安裝 `BarcodeKeyboard2-v1.6.0-debug.apk`。
2. 到 Android「設定 → 系統 → 鍵盤／語言與輸入法 → 螢幕鍵盤」啟用 **Barcode Keyboard**。
3. 在文字欄位切換至 Barcode Keyboard；按字母即直接輸入。
4. 首次掃描時授予相機權限；匯入商品時從系統文件選擇器挑選 XLSX。

## 建置

需要 JDK 17+、Android SDK Platform 35 及 Gradle 8.7：

```sh
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`。自用版使用 Android debug 簽章；線上更新 APK 必須使用相同簽章，否則 Android 不會允許覆蓋安裝。
