# Barcode Keyboard 2

自用 Android 英文輸入法，按下字母立即輸出、不顯示候選字。v1.5.0 以圓角卡片、淺色鍵帽、清晰工具列和較易觸控的按鍵整理鍵盤介面。

## 功能

- **英文直輸**：QWERTY 鍵盤，字母按下即寫入游標位置，不載入中文字典或計算候選；支援 Shift、數字／符號、空格、長按刪除及 Enter。
- **鍵盤內條碼掃描**：相機畫面限制在與鍵盤相同的 IME 視窗；條碼圖案難以辨識時，嘗試 OCR 讀取條碼下方數字。
- **掃描後自動 Enter**：條碼回填後觸發目前輸入欄位的 Enter／搜尋／完成動作；文字 OCR 僅回填文字。
- **商品 XLSX 匯入**：由系統文件選擇器讀取商品名稱、條碼及第二條碼；原始 XLSX 不會複製進 App 或打包進 APK，商品對照只儲存在手機本機。
- **快捷工具**：本機剪貼簿歷史、常用語、表情、游標移動、觸感／按鍵聲設定和線上更新入口。
- **線上更新**：從 `api.github.com/repos/danielwkh/barcodekeyboard2/releases/latest` 查詢公開 Release；使用者按「下載並安裝更新」後才下載，完成後由 Android 安裝器再次確認。安裝更新須由使用者允許此 App 安裝未知來源；不會背景靜默安裝。
- **隱私**：鍵盤內容、剪貼簿歷史及商品目錄在本機處理；更新器只連線至 GitHub 公開 Release API 與該 repo APK 附件。

## 安裝

1. 安裝 `BarcodeKeyboard2-v1.5.0-debug.apk`。
2. 到 Android「設定 → 系統 → 鍵盤／語言與輸入法 → 螢幕鍵盤」啟用 **Barcode Keyboard**。
3. 在文字欄位切換至 Barcode Keyboard；按字母即直接輸入。
4. 掃描首次使用時授予相機權限；商品匯入時從系統選擇器選 XLSX。

## 建置

需要 JDK 17+、Android SDK Platform 35 及 Gradle 8.7：

```sh
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`。此自用版本使用 Android debug 簽章；更新 APK 必須由相同簽章建置，使用不同簽章的 APK 無法覆蓋安裝。
