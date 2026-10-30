# android-auto-Click

極簡、免廣告的 Android 自動點擊／連點器。UI 採 iOS Settings 式黑白平面（與 Wusper 個人中心同款語彙）。

## 現況（1.6.2）

已可用：

- **連點引擎**：手勢完成 callback 後才發下一發（串行，不 burst 漏點）
- **模式**：單點／多點／滑動（相鄰點間 swipe）
- **設定持久化**：間隔、次數、時長、模式、座標、常用點、序列、主題等
- **懸浮窗**：前台服務保活、可拖曳面板與標記點、顯示座標開關
- **序列**：錄製（真實步間延遲）、回放、匯入／匯出文字
- **序列視覺編輯**：逐步列出點／滑／等待＋座標＋延遲；可改延遲、上移／下移、複製、刪除；變更即寫回 `SettingsStore`
- **Settings 風 UI**：`#000` / `#f5f5f7` / `#1c1c1e`，灰階開關，無霓虹／無毛玻璃／無系統藍 CTA

已知限制：

- **遊戲自繪錄製**：無障礙只能收到系統／原生控件的 `VIEW_CLICKED`。Unity／Unreal／OpenGL／自繪 Canvas 遊戲畫面通常**錄不到**觸控。請改用手動「加入點擊／滑動」、螢幕標記點，或匯入文字序列。
- 標記點在部分全面屏／異形屏可能有輕微座標偏移（仍可持續微調）。
- 進階開關（鏡像、漸慢、雙擊等）執行期生效；多數已接引擎，少數未全部寫入 SharedPreferences（重開可能回預設）。

## 簽名金鑰

`release.keystore` **不進公開倉**（已 gitignore）。本機／CI 從 Google Drive 資料夾 **`android-auto-Click-secrets`** 取得，寫入 `local.properties`：

```
storeFile=../release.keystore
storePassword=…
keyAlias=release
keyPassword=…
```

CI secrets：`KEYSTORE_BASE64`、`STORE_PASSWORD`、`KEY_PASSWORD`、`KEY_ALIAS`。

## 建置

```bash
./gradlew assembleRelease
```

Push `main` 會觸發 GitHub Actions 簽 release APK 並開 GitHub Release。

## 授權

MIT License
