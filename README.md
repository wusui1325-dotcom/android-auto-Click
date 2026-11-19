# AutoClick 3.0.11

免廣告、最簡潔的 Android 自動點擊／連點器。UI 採 iOS Settings 黑白平面風格。介面以繁體中文為主，並內建簡體中文與 English。

## 功能

- 單點／多點／拖拽滑動／序列回放；可保存與載入方案
- 間隔、循環次數（可無限）、按壓時長、點擊抖動
- 懸浮控制列：中心拖動、橫豎排列、大小、展開／收起；可拖標記點
- 觸控點拖動改位置、長按調整間隔／次數／點擊時長
- 序列錄製（見限制）與手動加點
- 應用內切換繁體／简体／English
- 可選 Root 點擊方式（預設關閉，見下）

## 3.0.11

- 修正拖拽起始點（↑N）輕點仍被拖走：只有手指移動超過提交閾值（約 24dp 或 2.5 倍系統 touch slop，取較大者）才寫入新座標；視覺跟隨仍從一般 slop 開始，但未達閾值的輕點會彈回原位且不存檔。提交時以按下當下的螢幕位置加手指位移計算，不依賴可能過期的 layout 偏移，也不使用可能尚未更新的 getLocationOnScreen。
- 改從已在畫面上的控制列校正偏移，並驗證樣本（略過寬高為 0、位置為 (0,0) 但視窗不在原點、或明顯不合理的偏移）；不再在標記的短按放開時做第一次校正並立刻重擺，避免點看起來被帶走。
- 長按彈窗、刪除模式點選、取消手勢復位、不成對起點略過、新增點使用即時螢幕尺寸等行為維持不變。

本版本未在真機上操作驗證（無裝置實測）。

## 3.0.10

- 修正拖拽起始點（↑N）輕點就被拖走：短按不寫入新座標；放開時以標記實際螢幕位置存檔，不依賴可能尚未校正或中途變更的 layout 偏移；校正完成後立刻重擺標記，不必再等 400ms。取消手勢會回到按下前的位置。
- 新增拖拽／觸控點時使用目前螢幕尺寸，避免旋轉後仍用舊的寬高。
- 不成對的拖拽起點播放時略過，不會誤當成點擊。

本版本未在真機上操作驗證（無裝置實測）。

## 3.0.9

- 修正橫屏與 Root 點擊校正：螢幕尺寸改以目前 Display 的即時大小為準（不再先讀可能停留在直屏的 WindowMetrics／Application 設定），已在橫屏時授權 Root 也會用對的範圍去 CONFIG uinput。
- 左右橫屏對調或 180° 翻轉（寬高不變）也會重新校正偏移；控制列在校正後會依實際偏移再夾回螢幕，並避開劉海／鏡頭安全區。
- 懸浮列上播放／錄製失敗會跳出錯誤（例如 Root 模式未開無障礙卻按錄製）。
- Root 更新螢幕尺寸失敗會重試，再不行就結束工作階段並報錯，不會默默沿用舊的座標範圍；helper 的 stderr 不再混進 TAP／SWIPE／CONFIG 協議。

本版本未在真機上操作驗證（無裝置實測）。

## 3.0.8

- **橫屏／旋轉**：懸浮控制列監聽螢幕方向、尺寸與密度變化；重新校正 LayoutParams 與螢幕座標的 **X 與 Y** 偏移（異形屏／劉海常見缺口）；控制列會被限制在目前畫面內，避免轉橫後跑出短邊而無法點選。方案／觸控點座標仍以螢幕像素保存，**不會**在旋轉時用矩陣重映射（遊戲會重排畫面，把角落對過去是錯的）。若有點位落到畫面外，會短暫提示，但不自動搬家。
- **可選 Root 模式**：設定裡「點擊方式」可在「無障礙（預設）」與「Root」之間切換。開啟 Root 時會請求一次 su 並盡量保持連線。播放時若已授權 Root，不必再開無障礙；懸浮窗仍必須開啟。選擇 Root 但 su 不存在、被拒絕或尚未授權時會明確報錯，**不會**默默改回無障礙。
- Root 快路徑：以持續運行的特權行程透過 uinput 虛擬指標注入 TAP／SWIPE，按一下完成再進行下一筆（與無障礙手勢同樣嚴格串行）。若 uinput 無法建立，才退回系統 `input` 指令（每次都會另起行程，較慢；此說明只寫在程式註解，沒有嚇人的使用者橫幅）。不附帶 su 二進位，也沒有通用 Root 終端。

本版本未在真機上操作驗證（無裝置實測）。

## 架構

```
engine/    ClickEngine · GestureDispatcher（無障礙，嚴格串行）· Models
overlay/   OverlayGeometry（純座標／限制邏輯，含單元測試）· ScreenMetrics
root/      RootClickBackend（可選；uinput 助手 + input 後備）
data/      AppStore（StateFlow 單一真相來源）
service/   ClickAccessibilityService · OverlayService（真 FGS）· 權限／通知
ui/        Compose Settings 風單 Activity
native/    root_uinput.c（預編譯進 assets/root_uinput/<abi>/；CI 不編 NDK）
```

## 平台契約（依官方文件）

- **Gesture**：`canPerformGestures` + `dispatchGesture`；座標為螢幕像素；點擊時長下限 1ms、上限 `GestureDescription.getMaxGestureDuration()`；嚴格等 `GestureResultCallback` 再下一筆
- **Overlay**：`TYPE_APPLICATION_OVERLAY`；`layoutInDisplayCutoutMode=ALWAYS`；`setFitInsetsTypes(0)`；標記用 `getLocationOnScreen`／`rawX/Y` 對齊手勢座標系；旋轉後重算 X/Y 偏移
- **FGS**：`FOREGROUND_SERVICE_SPECIAL_USE` + manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`；`ServiceCompat.startForeground(..., SPECIAL_USE)`；通知 `PRIORITY_LOW+`
- **Compose**：`enableEdgeToEdge()`；勿設 `statusBarColor`（Android 15+ 失效）；`WindowInsetsController` 調圖示對比；內容 `WindowInsets.safeDrawing`

## 已知限制

- 序列錄製仍依賴無障礙 `TYPE_VIEW_CLICKED`，即使點擊方式選了 Root 也一樣；遊戲自繪 View 通常錄不到，請用懸浮標記手動建步驟。
- 以無障礙播放時，需使用者手動開啟無障礙與懸浮窗。
- 以 Root 播放時，需使用者授予 su，並開啟懸浮窗。部分裝置可能無法建立 uinput，此時會改用較慢的系統 `input` 指令。
- 旋轉後不重映射已存座標；畫面外的點仍按原座標點擊。
- 本版本無真機操作驗證。

## 建置

```bash
./gradlew assembleRelease
```

簽名金鑰放 CI secrets／本機 `local.properties`（見 Drive `android-auto-Click-secrets`）。`*.keystore` 不進 git。

若要重編 Root 助手（需本機 NDK，CI 工作流程不會編譯它）：

```bash
ANDROID_NDK_HOME=/path/to/ndk ./native/build.sh
```

## 版本

**3.0.11**（versionCode 42）— 修正拖拽起始點輕點位移（更大提交閾值、按下座標加位移存檔、控制列校正且短按不再重擺）。

**3.0.10**（versionCode 41）— 修正拖拽起始點輕點位移（短按不搬家、放開以螢幕實位存檔、校正後立刻重擺）。

**3.0.9**（versionCode 40）— 修正橫屏／Root 座標與懸浮列錯誤提示（即時 Display 尺寸、180° 翻轉校正、劉海安全區、CONFIG 失敗可見、協議與 stderr 分離）。

**3.0.8**（versionCode 39）— 橫屏／旋轉校正（雙軸偏移、控制列限制在畫面內、彈窗限高）；可選 Root 點擊方式（uinput 快路徑）。

**3.0.7**（versionCode 38）— 語言切換實際生效（recreate + zh-TW/CN）。

**3.0.6**（versionCode 37）— 應用內語言：繁體／简体／English。

**3.0.5**（versionCode 36）— 控制列展開／收起與排列方向過渡。

**3.0.4**（versionCode 35）— 觸控點彈窗可設單點按壓時長，並寫入方案。

**3.0.3**（versionCode 34）— 真實連點速度：移除 TapTimeout 下限與多餘等待。

較早的 3.0 系列重做了懸浮控制與播放引擎；2.0.x 為更早的架構。
