# AutoClick 2.0

免廣告、最簡潔的 Android 自動點擊／連點器。UI 採 iOS Settings 黑白平面風格。

## 功能

- **單點／多點／相鄰滑動／序列回放**
- 間隔、次數（可無限）、按壓時長、啟動延遲、座標／間隔抖動
- 懸浮窗：拖曳、單擊起停、長按選單、可拖標記點（異形屏／cutout 校正）
- 序列：真實時間戳錄製＋視覺編輯（增刪改排序延遲）＋命名持久化
- 重開 App 後設定／點／序列全部保留
- 無障礙未開時開始會明確報錯

## 架構（2.0 重構）

```
engine/   ClickEngine · GestureDispatcher（嚴格串行）· Models
data/     SettingsRepository（StateFlow 單一真相來源）
service/  ClickAccessibilityService · OverlayService（真 FGS）· 權限／通知
ui/       Compose Settings 風單 Activity（總覽／點擊／序列／懸浮／權限／關於）
```

## 平台契約（依官方文件）

- **Gesture**：`canPerformGestures` + `dispatchGesture`；座標為螢幕像素；點擊時長 ≥ `ViewConfiguration.getTapTimeout()`；上限 `GestureDescription.getMaxGestureDuration()`；嚴格等 `GestureResultCallback` 再下一筆
- **Overlay**：`TYPE_APPLICATION_OVERLAY`；`layoutInDisplayCutoutMode=ALWAYS`；`setFitInsetsTypes(0)`；標記用 `getLocationOnScreen`／`rawX/Y` 對齊手勢座標系
- **FGS**：`FOREGROUND_SERVICE_SPECIAL_USE` + manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`；`ServiceCompat.startForeground(..., SPECIAL_USE)`；通知 `PRIORITY_LOW+`
- **Compose**：`enableEdgeToEdge()`；勿設 `statusBarColor`（Android 15+ 失效）；`WindowInsetsController` 調圖示對比；內容 `WindowInsets.safeDrawing`

## 已知限制

- 序列錄製依賴無障礙 `TYPE_VIEW_CLICKED`；遊戲自繪 View 通常錄不到，請用懸浮標記手動建步驟。
- 需使用者手動開啟無障礙與懸浮窗權限。

## 建置

```bash
./gradlew assembleRelease
```

簽名金鑰放 CI secrets／本機 `local.properties`（見 Drive `android-auto-Click-secrets`）。`*.keystore` 不進 git。

## 版本

**2.0.3**（versionCode 23）— 連點預設、序列快捷、匯入匯出、執行統計、精簡懸浮選單、空狀態引導。

**2.0.2**（versionCode 22）— 懸浮觸發防打斷、執行中鎖定標記、觸覺回饋、通知停止、點／步座標可編、長按錄製。

**2.0.1**（versionCode 21）— debug／優化：懸浮鈕點擊修復、標記中心座標、手勢取消軟重試、序列手動建步、權限引導、錄製批次持久化。

**2.0.0**（versionCode 20）— 功能全重做、邏輯全重構。
