# TaCZ Damage Indicator 仕様書 (TACZ_INDICATOR_DETAIL)

## 1. 概要

Minecraft Forge 1.20.1 環境において、TaCZ (Timeless and Classics Zero) の銃撃ダメージおよび通常ダメージを検知し、距離に関係なく画面上で一定の見かけサイズ（角度サイズ一定）でダメージ数値をポップアップ表示するMODの仕様書です。

## 2. アーキテクチャ構成

### 2.1 サーバーサイド (`server`)

- **`DamageEventHandler`**:
  - `LivingDamageEvent` をキャッチし、攻撃者 (`ServerPlayer`)、ダメージ量、ダメージソースを解析。
  - TaCZの弾丸・ダメージソース（銃撃、ヘッドショット）やバニラのクリティカル攻撃を判別。
  - 被弾モブの頭部位置座標を取得し、`DamageIndicatorPacket` を生成して攻撃者プレイヤーへパケット送信。

### 2.2 ネットワーク層 (`network`)

- **`ModMessages`**:
  - Forge `SimpleChannel` を使用したパケット通信路の定義。
- **`DamageIndicatorPacket`**:
  - 発生座標 $(X, Y, Z)$、ダメージ値 (float)、ヘッドショットフラグ (boolean)、クリティカルフラグ (boolean)、TaCZフラグ (boolean) をバイナリシリアライズ/デシリアライズ。

### 2.3 クライアントサイド (`client`)

- **`IndicatorConfig`**:
  - Forge Client Config による設定（有効/無効、基本スケール、距離スケール係数、生存Tick数、上昇速度、X-Ray透過表示、色設定など）。
- **`IndicatorInstance`**:
  - 個々のダメージ表示インスタンス。Tick経過による上昇アニメーションと終盤のアルファ値フェードアウトを管理。
- **`DamageIndicatorManager`**:
  - アクティブなインジケータ群のライフサイクル管理。高レート連射時の重なりを防ぐジッター（散乱）付与。
- **`DamageIndicatorRenderer`**:
  - `RenderLevelStageEvent` (AFTER_TRANSLUCENT_BLOCKS) で描画。
  - カメラ位置との距離 $d$ を計算し、透視投影の距離減衰を相殺するスケーリング行列を適用。
  - カメラ回転（ビルボード）を適用し、常にカメラ正面を向くようにテキストを描画。

## 3. 数学的モデル (距離非依存スケーリング)

3D透視投影空間において、対象物が見える画面上の角度サイズ $\theta$ は概ね $\theta \approx \frac{S_{\text{world}}}{d}$ となります。
本MODでは、描画時のワールドスケール $S_{\text{world}}$ を距離 $d$ に比例させることで画面上でのサイズ $\theta$ を一定に保ちます：

$$S_{\text{world}} = S_{\text{base}} \times \max(1.0, d \times \text{distanceScaleFactor})$$

- ヘッドショット時: $1.35 \times S_{\text{world}}$
- クリティカル時: $1.15 \times S_{\text{world}}$

## 4. 設定項目一覧 (`taczindicator-client.toml`)

| 項目名 | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `enabled` | boolean | `true` | インジケータ表示の有効/無効 |
| `enableConstantSize` | boolean | `true` | 距離に関わらず一定サイズで表示するか |
| `baseScale` | double | `0.025` | 基本描画スケール |
| `distanceScaleFactor` | double | `0.05` | 距離に応じた拡大係数 |
| `lifetimeTicks` | int | `30` | 表示持続時間 (20Ticks = 1秒) |
| `riseSpeed` | double | `0.03` | 上昇アニメーション速度 |
| `enableXRay` | boolean | `true` | 壁越しの透過表示 |
| `showHeadshotIcon` | boolean | `true` | ヘッドショット表示 (`[HS]`) の有無 |
| `decimalPlaces` | int | `1` | ダメージ値の小数点表示桁数 |
| `normalDamageColor` | int | `0xFFFFFF` | 通常ダメージ色 |
| `criticalDamageColor` | int | `0xFFFF55` | クリティカル色 |
| `headshotDamageColor` | int | `0xFF2222` | ヘッドショット色 |
| `taczDamageColor` | int | `0xFFA500` | TaCZ銃撃ダメージ色 |

## 5. ビルド方法

```bash
mvn clean test
```

単体テストの実行およびMavenビルドの検証が行われます。
