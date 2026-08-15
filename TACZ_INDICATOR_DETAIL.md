# TaCZ Damage Indicator 仕様書 (TACZ_INDICATOR_DETAIL)

## 1. 概要

Minecraft Forge 1.20.1 環境において、TaCZ (Timeless and Classics Zero) の銃撃ダメージおよび通常ダメージを検知し、**2D HUDレイヤー上** または3D空間でダメージ数値をポップアップ表示するMODの仕様書です。
連続射撃や連撃時の **加算表示（累積ダメージスタック）** および **古いインジケータの上方スクロール（はけ）** をサポートしています。

## 2. アーキテクチャ構成

### 2.1 サーバーサイド (`server`)

- **`DamageEventHandler`**:
  - `LivingDamageEvent` をキャッチし、攻撃者 (`ServerPlayer`)、被弾モブ (`victim.getId()`)、ダメージ量、ダメージソースを解析。
  - TaCZの弾丸・ダメージソース（銃撃、ヘッドショット）やバニラのクリティカル攻撃を判別。
  - 被弾モブの頭部位置座標を取得し、`DamageIndicatorPacket` を生成して攻撃者プレイヤーへパケット送信。

### 2.2 ネットワーク層 (`network`)

- **`ModMessages`**:
  - Forge `SimpleChannel` を使用したパケット通信路の定義。
- **`DamageIndicatorPacket`**:
  - 対象エンティティID (`int entityId`)、発生座標 $(X, Y, Z)$、ダメージ値 (float)、ヘッドショットフラグ (boolean)、クリティカルフラグ (boolean)、TaCZフラグ (boolean) をバイナリシリアライズ/デシリアライズ。

### 2.3 クライアントサイド (`client`)

- **`IndicatorConfig`**:
  - 描画モード (`renderMode`: HUD_PROJECTED / HUD_CROSSHAIR / WORLD_3D)
  - 連続ダメージモード (`consecutiveMode`: ACCUMULATE / SCROLL_UP / OFF)
  - コンボ持続時間、HUDスケール、スクロール間隔、カラー設定などの設定管理。
- **`IndicatorInstance`**:
  - 個別のダメージ表示インスタンス。累積ダメージ計算、ヒット回数、上方スクロールオフセット、ポップアニメーション（バウンス拡大・減衰）、アルファ値フェードアウトを管理。
- **`DamageIndicatorManager`**:
  - アクティブなインジケータ群のライフサイクル管理。
  - 同一ターゲットへの連続ヒット時の加算処理（ACCUMULATE）や古いインジケータの押し上げ処理（SCROLL_UP）を制御。
- **`ScreenProjectionUtil`**:
  - 3Dワールド座標からMinecraftの2D GUI画面座標への高精度な透視投影計算。
- **`DamageIndicatorHudRenderer`**:
  - `RenderGuiEvent.Post` でHUD上に2Dテキストを描画。鮮明なフォントとスムーズなアニメーションを提供。
- **`DamageIndicatorRenderer`**:
  - `RenderLevelStageEvent` による3Dワールド空間描画（WORLD_3Dモード時）。

## 3. 連続ダメージ処理仕様

1. **加算モード (`ACCUMULATE` - デフォルト)**:
   - 同一エンティティへ一定時間内（デフォルト30Ticks = 1.5秒）に連続でダメージを与えた場合、数値を合算（例: `15.0` → `30.0` → `45.0`）。
   - ヒットごとにポップアップが拡大バウンスし、表示タイマーをリセット。
   - `showHitCount` を有効にすると `45.0 x3` のようにヒット回数も付加表示。
2. **上方スクロールモード (`SCROLL_UP`)**:
   - 新しいダメージが発生するたびに、直前のインジケータを一定ピクセル（`scrollSpacing`）上方向へ押し上げ。
   - 連続ヒットした数値が画面上に整然と並びながら上方へはけてフェードアウト。
3. **個別モード (`OFF`)**:
   - 従来の個別ポップアップ表示。

## 4. 設定項目一覧 (`taczindicator-client.toml`)

| 項目名 | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `enabled` | boolean | `true` | インジケータ表示の有効/無効 |
| `renderMode` | enum | `HUD_PROJECTED` | 描画モード (`HUD_PROJECTED`, `HUD_CROSSHAIR`, `WORLD_3D`) |
| `consecutiveMode` | enum | `ACCUMULATE` | 連続ダメージ処理 (`ACCUMULATE`, `SCROLL_UP`, `OFF`) |
| `comboTimeoutTicks` | int | `30` | 連続ヒット判定時間 (20Ticks = 1秒) |
| `hudScale` | double | `1.0` | HUD表示時の文字拡大スケール |
| `scrollSpacing` | double | `12.0` | SCROLL_UPモード時の押し上げ間隔（px） |
| `crosshairOffsetX` | double | `15.0` | HUD_CROSSHAIR時の画面中心Xオフセット |
| `crosshairOffsetY` | double | `-8.0` | HUD_CROSSHAIR時の画面中心Yオフセット |
| `showHitCount` | boolean | `false` | 加算モード時のヒット数表示 (`x3` など) |
| `enableConstantSize` | boolean | `true` | WORLD_3D時: 距離に関わらず一定サイズで表示 |
| `baseScale` | double | `0.025` | WORLD_3D時: 基本描画スケール |
| `distanceScaleFactor` | double | `1.0` | WORLD_3D時: 距離に応じた拡大係数 |
| `lifetimeTicks` | int | `35` | 表示持続時間 |
| `riseSpeed` | double | `0.025` | 上昇アニメーション速度 |
| `enableXRay` | boolean | `true` | WORLD_3D時: 壁越しの透過表示 |
| `showHeadshotIcon` | boolean | `true` | ヘッドショット表示 (`[HS]`) の有無 |
| `decimalPlaces` | int | `1` | ダメージ値の小数点表示桁数 |
| `normalDamageColor` | int | `0xFFFFFF` | 通常ダメージ色 |
| `criticalDamageColor` | int | `0xFFFF55` | クリティカル色 |
| `headshotDamageColor` | int | `0xFF2222` | ヘッドショット色 |
| `taczDamageColor` | int | `0xFFA500` | TaCZ銃撃ダメージ色 |

## 5. ビルドおよび動作環境

- **Minecraft**: 1.20.1
- **Forge**: 47.3.0 (47.1.0+)
- **Java**: 17 (Eclipse Temurin / OpenJDK 17)
- **ビルドツール**: Gradle 8.4 + ForgeGradle 6.0

### ビルド手順

```powershell
# 単体テストの実行
.\gradlew.bat test

# MOD jar のビルド (build/libs/taczindicator-1.0.0.jar が生成されます)
.\gradlew.bat build
```
