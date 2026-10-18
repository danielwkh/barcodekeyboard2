# Barcode Keyboard 2

自用 Android 輸入法，以微信輸入法公開的「鍵盤工具列＋快捷面板」互動作參考，使用獨立名稱、配色與程式碼；核心仍是鍵盤內條碼掃描和商品資料查找。

## 功能

- **鍵盤內掃描**：IME 視窗固定 340dp；相機預覽嵌在同一鍵盤視窗，掃描介面不會全螢幕展開。
- **條碼數字備援**：條碼圖案難解碼時，使用中文 OCR 讀取畫面中條碼下方的獨立數字列；短數字價錢不當作條碼候選。
- **掃碼後自動 Enter**：回填條碼後觸發欄位目前的 Enter／搜尋／完成動作；文字 OCR 只回填，不自動提交。
- **重掃取代**：同一輸入欄再次掃碼時，如果上一次掃描結果仍緊鄰游標之前，會替換上一碼，不會誤刪使用者其後編輯的內容。
- **輸入方式**：English、26 鍵拼音、九宮格拼音（以離線拼音詞庫作 T9 候選）、筆畫及速成。各輸入頁面有 Enter 鍵；刪除鍵支援長按連續刪除。
- **鍵盤快捷面板**：本機剪貼簿歷史（需使用者點按「讀取目前剪貼簿」才讀取）、可新增／刪除的常用語、表情選擇、游標左右移動與選取、觸感回饋及按鍵聲開關。
- **商品 XLSX 匯入**：從系統文件選擇器即時讀取使用者檔案，支援商品名稱、條碼、第二條碼；原始 XLSX 不複製進 App、不打包進 APK。匯入後商品名稱與條碼對照只儲存在手機本機。
- **隱私**：鍵盤文字、剪貼簿歷史、常用語及商品目錄均在本機保存；本 App 不設輸入內容上傳或跨裝置同步服務。

## 與微信輸入法的範圍差異

微信輸入法官方公開功能還包括成熟的手寫／雙拼／五筆方案、語音轉文字、拼寫檢查、雲端 Plus、跨裝置同步等。本 App 目前並未實作這些服務，亦不會以未連接後端的空按鈕冒充功能。特別是語音、雲端候選及同步需要額外權限、伺服器、隱私告知與明確同意。此專案參考公開互動概念，不複製微信商標、圖示、原始素材或私有程式碼。

## 安裝及設定

1. 安裝 `BarcodeKeyboard2-v1.4.0-debug.apk`。
2. 到 Android「設定 → 系統 → 鍵盤／語言與輸入法 → 螢幕鍵盤」啟用 **Barcode Keyboard**。
3. 在文字欄位切換至 Barcode Keyboard。
4. 首次掃描時授予相機權限；匯入商品時，以系統文件選擇器挑選 XLSX。

若條碼含前置零，請將 Excel 條碼欄設定為文字格式，避免試算表先移除前置零。

## 來源與字典授權

介面功能盤點參考官方 [微信輸入法 Android 更新紀錄](https://z.weixin.qq.com/web/change-log/android)、[Google Play 產品頁](https://play.google.com/store/apps/details?id=com.tencent.wetype&hl=zh) 及[隱私政策](https://z.weixin.qq.com/web/privacy)。研究摘錄見 [`research/wechat-ime-research.md`](research/wechat-ime-research.md)。拼音、筆畫及倉頡字典授權列於 `app/src/main/assets/dictionaries/NOTICE.txt`。

建置需要 JDK 17+、Android SDK Platform 35 及 Gradle 8.7：

```sh
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`。自用版本使用 Android debug 簽章；以不同簽章編譯的 APK 不能直接覆蓋安裝。
