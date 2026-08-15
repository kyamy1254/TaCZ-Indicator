# TaCZ Indicator (Minecraft Forge 1.20.1)

Minecraft Forge 1.20.1 向けの視認性・操作性に優れたダメージインジケータMODです。
特に **TaCZ (Timeless and Classics Zero)** のフルオート射撃や長距離狙撃に対応し、**「HUDレイヤーでの鮮明な表示」** および **「連続射撃の加算（スタック）/ 上方はけ（スクロール）」** 機能を提供します。

---

## 主な特徴

- 🖥️ **HUD 2D レンダリング**: 3Dワールド座標から2D画面座標への投影により、HUDレイヤー上にクリアで美麗なダメージ数値を描画。
- 💥 **連続ダメージの加算表示 (Accumulation)**: フルオート連射や連続ヒット時にダメージがリアルタイムに合算され、爽快なヒットパルス演出。
- 📜 **古いインジケータの上方スクロール (Scroll Up)**: 連射時に古いダメージ値が上へスムーズにはけていき、画面の見やすさを維持。
- 🎯 **距離非依存レンダリング**: 500m先のスナイピングでも至近距離でも、画面上で常に一定の見やすいサイズでダメージ数を表示。
- 🔫 **TaCZ 特化サポート**: TaCZの銃弾、ヘッドショット、クリティカルダメージを識別し、カラーやアイコンで強調表示。
- ⚙️ **充実したカスタマイズ**: 描画モード（HUD投影 / レティクル横 / 3D空間）、連続ダメージモード（加算 / スクロール / 個別）、スケール、カラーなどをクライアント設定で自由に変更可能。

---

## 設定項目 (`config/taczindicator-client.toml`)

- `renderMode`: `HUD_PROJECTED` (HUD投影), `HUD_CROSSHAIR` (照準横), `WORLD_3D` (3D空間)
- `consecutiveMode`: `ACCUMULATE` (加算), `SCROLL_UP` (上方スクロール), `OFF` (個別)
- `comboTimeoutTicks`: 連続ヒット判定の受付時間 (デフォルト: 30Ticks)
- `hudScale`: HUD文字スケール (デフォルト: 1.0)
- `scrollSpacing`: スクロール時の押し上げ間隔 (デフォルト: 12.0px)
- `showHitCount`: 加算時のヒット数表示 (`45.0 x3` 等)

---

## ビルド手順

Gradle Wrapper を使用してビルドします（Java 17 が必要です）：

```powershell
# 単体テストの実行
.\gradlew.bat test

# MOD jar のビルド (build/libs/taczindicator-1.0.0.jar が生成されます)
.\gradlew.bat build
```

---

## 導入方法
1. Minecraft Forge 1.20.1 (Forge 47.1.0以上、47.3.0推奨) をインストールします。
2. 生成された `build/libs/taczindicator-1.0.0.jar` を `.minecraft/mods` フォルダに配置します。
3. （任意）`config/taczindicator-client.toml` で好みの設定に調整します。
