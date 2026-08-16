# TaCZ Indicator 仕様書 (TACZ_INDICATOR_DETAIL)

## 1. 概要

Minecraft Forge 1.20.1 環境において、TaCZ (Timeless and Classics Zero) の銃撃ダメージおよび通常ダメージを検知し、**2D HUDレイヤー上** または3D空間でダメージ数値をポップアップ表示するMODの仕様書です。
連続射撃や連撃時の **加算表示（累積ダメージスタック）**、**古いインジケータの上方スクロール（はけ）と表示上限数管理**、**アニメーションスタイル（浮遊上昇/拡散バウンス/重力落下/静止拡大）**、**カラーテーマ・プリセット（Default/Apex/Cyberpunk/Tactical/Valorant）**、**戦闘統計・DPSメーター（3秒スライディングウィンドウ＆マルチサーバー同期リセット）**、**カスタムビットマップフォントによる盾・盾貫通アイコン表示（防具:水色、貫通:白）**、**厳密な頭部判定($x-0.25 < y < x+0.25$)＆多層APIリフレクションによる高精度ヘッドショット判定**、**キル確定演出への距離表示($[100m]$)＆同種モブ連続キル置換更新**、**ヒット＆キル効果音**、および **被ダメージ時の画面赤色効果（ダメージヴィネット・画面フラッシュ）の完全なカスタマイズ・オンオフ制御** をサポートしています。

## 2. アーキテクチャ構成

### 2.1 サーバーサイド (`server` & `command`)

- **`DamageEventHandler`**:
  - `LivingDamageEvent` および `LivingDeathEvent` を購読し、攻撃元プレイヤー (`ServerPlayer`)、被弾モブ (`victim.getId()`)、ダメージ量、ダメージソースを高精度に解析。
  - **環境ダメージ誤加算の防止**: `victim.getLastHurtByMob()` による誤判定を完全撤廃し、プレイヤー自身の直接攻撃または発射物によるダメージのみを厳密に抽出。
  - **通常殴りヘッドショット除外**: 近接素手・剣等での通常殴りはヘッドショット対象外とし、銃器射撃・弾丸プロジェクタイルのみに限定。
  - **厳密頭部当たり判定**: 目の高さ $x$ に対して高さ $x - 0.25 < y < x + 0.25$（厳密不等式）、水平面はモブ本来の当たり判定AABBと同一サイズ・位置で3D Ray-Box交差レイキャスト判定を実行。
  - `DamageIndicatorPacket` にダメージ情報およびキル距離（`distanceMeters`）を格納して攻撃者プレイヤーへパケット送信。
- **`TaCZCompatHandler`**:
  - TaCZが環境に存在する場合、`EntityHurtByGunEvent` (Pre/Post, 各パッケージ階層) や `BulletHitEvent` の動的リスナーを包括的に登録。
  - クラス階層全体の深層リフレクション走査により、ヘッドショットフラグ、APフラグ、ヘッドショット倍率を高精度に抽出・短期キャッシュ。
- **`ModCommands`**:
  - `/taczstats reset [<targets>]` および `/taczindicator resetstats [<targets>]` コマンドの登録。
  - マルチサーバー環境において、OP管理者またはプレイヤー自身が指定ターゲットまたは全プレイヤー（`@a`）の戦闘統計（DPS・総ダメージ等）を一斉リセット可能。

### 2.2 ネットワーク層 (`network`)

- **`ModMessages`**:
  - Forge `SimpleChannel` を使用したパケット通信路の定義。
- **`DamageIndicatorPacket`**:
  - 対象エンティティID (`int entityId`)、発生座標 $(X, Y, Z)$、ダメージ値 (float)、ヘッドショットフラグ (boolean)、クリティカルフラグ (boolean)、TaCZフラグ (boolean)、防具貫通フラグ (boolean)、防具被弾フラグ (boolean)、キルフラグ (boolean)、ターゲット名 (String)、キル距離 (`int distanceMeters`) をバイナリシリアライズ/デシリアライズ。
- **`ServerHandshakePacket`**:
  - プレイヤー参加時にクライアントへ送信し、即時に `SERVER_SYNCED` モードを確立。
- **`ResetCombatStatsPacket`**:
  - サーバーからクライアントへ送信され、クライアント側の `CombatStatsManager` を初期化・リセット通知を表示。

### 2.3 クライアントサイド (`client`)

- **`IndicatorConfig`**:
  - 描画モード (`renderMode`: HUD_CROSSHAIR / HUD_PROJECTED / WORLD_3D)
  - 連続ダメージモード (`consecutiveMode`: ACCUMULATE / SCROLL_UP / OFF)
  - アニメーションスタイル (`animationStyle`: FLOAT_UP / SCATTER_POP / GRAVITY_DROP / STATIC)
  - カラーテーマ (`colorTheme`: DEFAULT / APEX / CYBERPUNK / TACTICAL_COD / VALORANT)
  - 戦闘統計HUD (`combatStatsMode`: COMBAT_ONLY / ALWAYS / OFF, `combatStatsPosition`: TOP_RIGHT 等)
  - スクロール上限数 (`maxScrolledIndicators`)、コンボ持続時間、HUDスケール、各種アイコントグル、被ダメージ画面効果、サウンド設定を管理。
- **`CombatStatsManager`**:
  - 直近3秒間のスライディングウィンドウによる瞬間DPS計算、ピークDPS、平均DPS、最大単発ダメージ、総与ダメージ、命中数、HS率(%)、クリティカル率(%)、AP/防具ヒット数、最長キル距離、およびリアルタイム戦闘ログ履歴（最新50件）の集計・管理。
- **`CombatStatsOverlay`**:
  - HUD上にコンパクトに常駐するリアルタイムDPSメーターカード（フェードイン/アウト対応）。
- **`CombatStatsScreen`**:
  - 専用の戦闘統計詳細GUI画面（キーバインド `J` または設定画面のボタンからオープン）。4つの詳細統計カード（ダメージ&DPS、命中分析、装甲貫通、キル記録）とスクロール可能な戦闘ログ一覧、クリップボードへの整形レポートコピー機能を搭載。
- **`ModKeyBindings`**:
  - `key.taczindicator.open_config` (デフォルト: `K` キー): 設定画面を開く
  - `key.taczindicator.open_stats` (デフォルト: `J` キー): 詳細戦闘統計画面を開く
- **`SoundHelper`**:
  - 通常ヒット音、ヘッドショット音（高音キーン音）、防具貫通音、キル確定音の再生ヘルパー（デフォルトOFF）。
- **`KillAlertInstance`**:
  - キル確定通知インスタンス。フォーマット: `Killed ゾンビ (x2) [100m]`。
  - 同種モブの連続キル発生時は、重複表示せず最新の距離とカウントでその場置換更新。
- **`DamageIndicatorManager`**:
  - アクティブなインジケータ群のライフサイクル管理。加算処理（ACCUMULATE）や `SCROLL_UP` 方式での表示上限数（`maxScrolledIndicators`）超過分の自動パージを制御。
- **`DamageIndicatorHudRenderer`**:
  - `RenderGuiEvent.Post` でHUD上に2Dテキストを描画（アニメーションオフセット対応）。
- **`DamageIndicatorRenderer`**:
  - `RenderLevelStageEvent` による3Dワールド空間描画（WORLD_3Dモード時・アニメーション物理補間対応）。
- **`DamageVignetteRenderer`**:
  - プレイヤーがダメージを受けた際に、画面四隅からの美しいグラデーションヴィネット（および画面フラッシュ）を描画。
- **`IndicatorConfigScreen`**:
  - ゲーム内設定GUI画面。タブ切り替えバー（全般/HUD/演出/サウンド）、カラーテーマ即時プレビュー、アニメーション切り替え、DPSメーター調整、詳細統計画面へのリンク、ドラッグ位置調整を完備。

### 2.4 リソース・カスタムフォント (`assets`)

- **ビットマップフォント定義 (`assets/minecraft/font/default.json`, `assets/taczindicator/font/default.json`)**:
  - `\uE001`: 防具軽減（通常盾）アイコン (`textures/font/shield.png`・純白アルファマスク化・ゲーム内水色 `§b` 表示)
  - `\uE002`: 防具貫通（盾貫通）アイコン (`textures/font/shield_penetration.png`・純白アルファマスク化・ゲーム内白 `§f` 表示)

## 3. ヘッドショット判定ロジック

ヘッドショット判定は以下の多層判定アルゴリズムにより行われます：

1. **通常殴り判定除外**:
   - 近接直接攻撃（非銃器・非弾丸）はヘッドショット判定を行わない。
2. **TaCZ API イベントキャッシュ**:
   - `TaCZCompatHandler` が `EntityHurtByGunEvent` / `BulletHitEvent` から直接取得した `isHeadshot` フラグまたは倍率 (`multiplier > 1.05f`) を参照。
3. **DamageType / MsgId 判定**:
   - `DamageSource` のメッセージIDおよび `DamageType` リソース名に `headshot` / `head_shot` が含まれるか検査。
4. **深層リフレクション探索**:
   - `DamageSource`、`directEntity` (`EntityKineticBullet`)、および内部の `EntityResult` / `HitResult` オブジェクトの全クラス階層にわたる boolean フィールド/ゲッターを包括的に検査。
5. **厳密3D Ray-Box交差幾何学レイキャスト**:
   - 攻撃元プレイヤーの3D視線ベクトル（`eyePosition` および `viewVector`）からモブの頭部バウンディングボックス（`AABB headBox`: 目の高さ $x$ に対して $x - 0.25 < y < x + 0.25$ かつモブ本来の水平AABB）へのRay-AABBクリッピング交差判定（`headBox.clip(...)`）を実行。

## 4. 設定項目一覧 (`taczindicator-client.toml`)

### 4.1 全般・表示設定 (`[general]`, `[display]`, `[hud]`, `[world3d]`)

| 項目名 | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `enabled` | boolean | `true` | インジケータ表示の有効/無効 |
| `onlyPlayerDamage` | boolean | `true` | プレイヤー自身が与えたダメージのみ表示 |
| `onlyTaczDamage` | boolean | `false` | TaCZ銃器ダメージのみ表示 |
| `renderMode` | enum | `HUD_CROSSHAIR` | 描画モード (`HUD_CROSSHAIR`, `HUD_PROJECTED`, `WORLD_3D`) |
| `consecutiveMode` | enum | `ACCUMULATE` | 連続ダメージ処理 (`ACCUMULATE`, `SCROLL_UP`, `OFF`) |
| `animationStyle` | enum | `STATIC` | アニメーション挙動 (`STATIC`, `FLOAT_UP`, `SCATTER_POP`, `GRAVITY_DROP`) |
| `comboTimeoutTicks` | int | `30` | 連続ヒット判定時間 (20Ticks = 1秒) |
| `showHitCount` | boolean | `true` | 加算モード時のヒット数表示 (`(x3)` など) |
| `showKillAlert` | boolean | `true` | キル確定通知の表示 (`[100m]` 距離表示付き) |
| `maxScrolledIndicators` | int | `6` | SCROLL_UPモードで画面上に保持する最大インジケータ数 (1〜20) |
| `colorTheme` | enum | `DEFAULT` | カラーテーマ (`DEFAULT`, `APEX`, `CYBERPUNK`, `TACTICAL_COD`, `VALORANT`) |
| `showHeadshotIcon` | boolean | `true` | ☠ ドクロ表示の有無 |
| `showCriticalIcon` | boolean | `true` | ★ 星表示の有無 |
| `showArmorPiercingIcon` | boolean | `true` | \uE002 盾貫通表示の有無 |
| `showArmorDamageIcon` | boolean | `true` | \uE001 防具軽減表示の有無 |
| `decimalPlaces` | int | `1` | ダメージ値の小数点表示桁数 |

### 4.2 戦闘統計・DPS設定 (`[combat_stats]`)

| 項目名 | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `combatStatsMode` | enum | `OFF` | 戦闘統計カードの表示モード (`OFF`, `COMBAT_ONLY`, `ALWAYS`) |
| `combatStatsPosition` | enum | `TOP_RIGHT` | HUD配置位置 (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`) |
| `combatStatsScale` | double | `1.0` | 戦闘統計カードの拡大スケール (0.5〜2.5) |

### 4.3 サウンド設定 (`[sounds]`)

| 項目名 | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `enableHitSound` | boolean | `false` | ダメージ命中時のヒット効果音を再生するかどうか (競合防止のためデフォルトOFF) |
| `hitSoundVolume` | double | `0.8` | ヒット効果音の音量 (0.0〜1.0) |
| `enableHeadshotSound` | boolean | `false` | ヘッドショット命中時の高音キーン音を再生するかどうか |
| `enableKillSound` | boolean | `false` | 敵撃破時のキル確定音を再生するかどうか |
| `killSoundVolume` | double | `0.9` | キル確定音の音量 (0.0〜1.0) |

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
