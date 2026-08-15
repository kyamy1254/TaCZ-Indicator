# TaCZ Indicator 仕様書 (TACZ_INDICATOR_DETAIL)

## 1. 概要

Minecraft Forge 1.20.1 環境において、TaCZ (Timeless and Classics Zero) の銃撃ダメージおよび通常ダメージを検知し、**2D HUDレイヤー上** または3D空間でダメージ数値をポップアップ表示するMODの仕様書です。
連続射撃や連撃時の **加算表示（累積ダメージスタック）**、**古いインジケータの上方スクロール（はけ）**、**カスタムビットマップフォントによる盾・盾貫通アイコン表示**、**3D Ray-Box交差レイキャスト＆多層APIリフレクションによる高精度ヘッドショット判定**、および **被ダメージ時の画面赤色効果（ダメージヴィネット・画面フラッシュ）の完全なカスタマイズ・オンオフ制御** をサポートしています。

## 2. アーキテクチャ構成

### 2.1 サーバーサイド (`server`)

- **`DamageEventHandler`**:
  - `LivingDamageEvent` および `LivingDeathEvent` を購読し、攻撃元プレイヤー (`ServerPlayer`)、被弾モブ (`victim.getId()`)、ダメージ量、ダメージソースを高精度に解析。
  - TaCZのAPIイベントキャッシュ (`TaCZCompatHandler`)、多層リフレクション探索、および **攻撃者視線・弾道とモブ頭部領域AABBとの高精度3D Ray-Box交差判定（幾何学レイキャスト）** を用いてヘッドショット・クリティカル・防具貫通（AP）を判定。
  - `DamageIndicatorPacket` を生成して攻撃者プレイヤーへパケット送信。
- **`TaCZCompatHandler`**:
  - TaCZが環境に存在する場合、`EntityHurtByGunEvent` (Pre/Post, 各パッケージ階層) や `BulletHitEvent` の動的リスナーを包括的に登録。
  - クラス階層全体の深層リフレクション走査により、ヘッドショットフラグ、APフラグ、ヘッドショット倍率を高精度に抽出・短期キャッシュ。

### 2.2 ネットワーク層 (`network`)

- **`ModMessages`**:
  - Forge `SimpleChannel` を使用したパケット通信路の定義。
- **`DamageIndicatorPacket`**:
  - 対象エンティティID (`int entityId`)、発生座標 $(X, Y, Z)$、ダメージ値 (float)、ヘッドショットフラグ (boolean)、クリティカルフラグ (boolean)、TaCZフラグ (boolean)、防具貫通フラグ (boolean)、防具被弾フラグ (boolean)、キルフラグ (boolean)、ターゲット名 (String) をバイナリシリアライズ/デシリアライズ。
- **`ServerHandshakePacket`**:
  - プレイヤー参加時にクライアントへ送信し、即時に `SERVER_SYNCED` モードを確立。

### 2.3 クライアントサイド (`client`)

- **`IndicatorConfig`**:
  - 描画モード (`renderMode`: HUD_CROSSHAIR / HUD_PROJECTED / WORLD_3D)
  - 連続ダメージモード (`consecutiveMode`: ACCUMULATE / SCROLL_UP / OFF)
  - コンボ持続時間、HUDスケール、スクロール間隔、カラー設定、各種アイコントグル、および **被ダメージ画面効果（ヴィネット）** の設定管理。
- **`IndicatorInstance`**:
  - 個別のダメージ表示インスタンス。累積ダメージ計算、ヒット回数、上方スクロールオフセット、ポップアニメーション（バウンス拡大・減衰）、アルファ値フェードアウトを管理。
  - **フォントアイコン配置**:
    - 接頭辞 (Prefix): ヘッドショット (`§c☠ §l`)、クリティカル (`§6★ §l`)
    - 接尾辞 (Suffix): ヒット数 (`§7(x2)`)、盾貫通 (`§b\uE002`)、防具軽減 (`§f\uE001`)
- **`DamageIndicatorManager`**:
  - アクティブなインジケータ群のライフサイクル管理。加算処理（ACCUMULATE）や古いインジケータの押し上げ処理（SCROLL_UP）を制御。
- **`DamageIndicatorHudRenderer`**:
  - `RenderGuiEvent.Post` でHUD上に2Dテキストを描画。
- **`DamageIndicatorRenderer`**:
  - `RenderLevelStageEvent` による3Dワールド空間描画（WORLD_3Dモード時）。
- **`DamageVignetteRenderer`**:
  - プレイヤーがダメージを受けた際に、画面四隅からの美しいグラデーションヴィネット（および画面フラッシュ）を描画。二乗イージングによる滑らかなフェードアウト処理を搭載。
- **`IndicatorConfigScreen`**:
  - ゲーム内設定GUI画面。リアルタイムプレビュー（ヴィネット赤色効果含む）、ボタントグル、ドラッグによる位置調整を完備。

### 2.4 リソース・カスタムフォント (`assets`)

- **ビットマップフォント定義 (`assets/minecraft/font/default.json`, `assets/taczindicator/font/default.json`)**:
  - `\uE001`: 防具軽減（通常盾）アイコン (`textures/font/shield.png`)
  - `\uE002`: 防具貫通（盾貫通）アイコン (`textures/font/shield_penetration.png`)

## 3. ヘッドショット判定ロジック

ヘッドショット判定は以下の多層判定アルゴリズムにより行われます：

1. **TaCZ API イベントキャッシュ**:
   - `TaCZCompatHandler` が `EntityHurtByGunEvent` / `BulletHitEvent` から直接取得した `isHeadshot` フラグまたは倍率 (`multiplier > 1.05f`) を参照。
2. **DamageType / MsgId 判定**:
   - `DamageSource` のメッセージIDおよび `DamageType` リソース名に `headshot` / `head_shot` が含まれるか検査。
3. **深層リフレクション探索**:
   - `DamageSource`、`directEntity` (`EntityKineticBullet`)、および内部の `EntityResult` / `HitResult` オブジェクトの全クラス階層にわたる boolean フィールド/ゲッターを包括的に検査。
4. **3D Ray-Box交差幾何学レイキャスト (高精度フォールバック)**:
   - 攻撃元プレイヤーの3D視線ベクトル（`eyePosition` および `viewVector`）からモブの頭部バウンディングボックス（`AABB headBox`: $y \ge \max(y + \text{height} \times 0.68, \text{eyeY} - 0.35)$）へのRay-AABBクリッピング交差判定（`headBox.clip(...)`）を実行。
   - 弾丸の移動ベクトル（`deltaMovement`）および着弾点検証、エンダードラゴンの頭部パート（`dragon.head`）にも完全対応。

## 4. 設定項目一覧 (`taczindicator-client.toml`)

### 4.1 全般・表示設定 (`[general]`, `[display]`, `[hud]`, `[world3d]`)

| 項目名 | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `enabled` | boolean | `true` | インジケータ表示の有効/無効 |
| `onlyPlayerDamage` | boolean | `true` | プレイヤー自身が与えたダメージのみ表示 |
| `onlyTaczDamage` | boolean | `false` | TaCZ銃器ダメージのみ表示 |
| `renderMode` | enum | `HUD_CROSSHAIR` | 描画モード (`HUD_CROSSHAIR`, `HUD_PROJECTED`, `WORLD_3D`) |
| `consecutiveMode` | enum | `ACCUMULATE` | 連続ダメージ処理 (`ACCUMULATE`, `SCROLL_UP`, `OFF`) |
| `comboTimeoutTicks` | int | `30` | 連続ヒット判定時間 (20Ticks = 1秒) |
| `showHitCount` | boolean | `true` | 加算モード時のヒット数表示 (`(x3)` など) |
| `showKillAlert` | boolean | `true` | キル確定通知の表示 |
| `showHeadshotIcon` | boolean | `true` | ☠ ドクロ表示の有無 |
| `showCriticalIcon` | boolean | `true` | ★ 星表示の有無 |
| `showArmorPiercingIcon` | boolean | `true` | \uE002 盾貫通表示の有無 |
| `showArmorDamageIcon` | boolean | `true` | \uE001 防具軽減表示の有無 |
| `decimalPlaces` | int | `1` | ダメージ値の小数点表示桁数 |
| `normalDamageColor` | int | `0xFFFFFF` | 通常ダメージ色 |
| `criticalDamageColor` | int | `0xFFCC00` | クリティカル色 |
| `headshotDamageColor` | int | `0xFF3333` | ヘッドショット色 |
| `armorPiercingColor` | int | `0x33CCFF` | 防具貫通ダメージ色 |
| `taczDamageColor` | int | `0xFFFFFF` | TaCZ銃撃ダメージ色 |

### 4.2 被ダメージ画面赤色効果設定 (`[damage_vignette]`)

| 項目名 | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `enableDamageVignette` | boolean | `true` | プレイヤー被ダメージ時の画面赤色効果（ヴィネット/フラッシュ）の有効/無効 |
| `damageVignetteOpacity` | double | `0.45` | 画面赤色効果の最大不透明度 (0.0〜1.0) |
| `damageVignetteDurationTicks` | int | `14` | 画面赤色効果の表示持続時間 (Ticks: 20Ticks = 1秒) |
| `damageVignetteColor` | int | `0xFF0000` | 画面赤色効果の色 (RGB Hex 0xRRGGBB) |
| `damageVignetteScaleWithDamage` | boolean | `true` | 受けたダメージ量に応じて濃さを自動調整するかどうか |

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
