# TaCZ Damage Indicator (Minecraft Forge 1.20.1)

Minecraft Forge 1.20.1 向けのダメージインジケータMODです。
特に **TaCZ (Timeless and Classics Zero)** の長距離狙撃やフルオート射撃に対応し、**「遠距離でも近距離でも画面上で同じ大きさで表示される」** 視認性の高いダメージ表示機能を提供します。

---

## 主な特徴
- 🎯 **距離非依存レンダリング**: 500m先のスナイピングでも至近距離の射撃でも、画面上で常に一定の見やすいサイズでダメージ数を表示。
- 🔫 **TaCZ 特化サポート**: TaCZの銃弾、ヘッドショット、クリティカルダメージを識別し、カラーやアイコンで強調表示。
- 👁️ **壁越し透過（X-Ray）**: 障害物や壁の奥にいる敵に与えたダメージも視認可能。
- ⚙️ **充実したカスタマイズ**: 表示サイズ、表示時間、上昇速度、カラー、小数点桁数などをクライアント設定で自由に変更可能。

---

## ビルド手順

Maven Wrapper を使用してビルドします：

```bash
# Windows
.\mvnw.cmd clean package

# Linux / macOS
./mvnw clean package
```

ビルドが完了すると、`target/tacz-indicator-1.0.0.jar` にMODファイルが生成されます。

---

## 導入方法
1. Minecraft Forge 1.20.1 (Forge 47.1.0以上推奨) をインストールします。
2. 生成された `tacz-indicator-1.0.0.jar` を `.minecraft/mods` フォルダに配置します。
3. （任意）`config/taczindicator-client.toml` で好みの設定に調整します。
