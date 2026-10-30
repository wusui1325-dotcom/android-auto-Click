# android-auto-Click

最簡潔、好看、免廣告的自動點擊 / 連點器 Android App。

## 目標

- **極簡畫面**：iOS Settings 式黑白平面（個人中心同款）
- **好看設計**：乾淨層級、灰階開關、無霓虹／無廣告
- **完全免廣告**：無任何廣告、無內購干擾
- **最佳體驗**：穩定、精準、手勢串行

## 功能

- 單點 / 多點 / 滑動
- 連點（固定間隔或隨機）
- 錄製點擊序列與回放
- 浮動懸浮窗＋可拖曳標記點
- 設定持久化、深色／淺色主題

## 簽名金鑰

`release.keystore` **不進公開倉**。本機／CI 請從 Google Drive 資料夾 **`android-auto-Click-secrets`** 取得，並寫入 `local.properties`（已被 gitignore）：

```
storeFile=../release.keystore
storePassword=…   # 見 Drive 筆記
keyAlias=release
keyPassword=…
```

CI 使用 repo secrets：`KEYSTORE_BASE64`、`STORE_PASSWORD`、`KEY_PASSWORD`、`KEY_ALIAS`。

## 目前狀態

- 連點引擎（手勢完成後才下一發）
- 滑動模式
- 懸浮窗（簡潔狀態顯示）
- 序列錄製／回放／編輯
- 設定持久化、錯誤提示
- Settings 風格 UI（1.6.0）

## 授權

MIT License
