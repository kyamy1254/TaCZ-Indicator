package com.kyamy.taczindicator.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * ダメージインジケータの設定管理クラス
 * わかりやすく整理されたカテゴリ構造（全般・表示・HUD・3D空間・アイコン/カラー・被ダメ画面エフェクト・サウンド）
 */
public class IndicatorConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    public enum RenderMode {
        HUD_CROSSHAIR,
        WORLD_3D,
        HUD_PROJECTED
    }

    public enum ConsecutiveMode {
        ACCUMULATE,
        SCROLL_UP,
        OFF
    }

    public enum ColorTheme {
        DEFAULT("taczindicator.theme.default", 0xFFFFFF, 0xFFCC00, 0xFF3333),
        APEX("taczindicator.theme.apex", 0xFFFFFF, 0xFFFF00, 0xFF4500),
        CYBERPUNK("taczindicator.theme.cyberpunk", 0x00F0FF, 0xFFE600, 0xFF0055),
        TACTICAL_COD("taczindicator.theme.tactical", 0xDCDCDC, 0xFF8C00, 0xDC143C),
        VALORANT("taczindicator.theme.valorant", 0xEEEEEE, 0xFFD700, 0xFF4655);

        private final String translationKey;
        public final int normalColor;
        public final int criticalColor;
        public final int headshotColor;

        ColorTheme(String translationKey, int normalColor, int criticalColor, int headshotColor) {
            this.translationKey = translationKey;
            this.normalColor = normalColor;
            this.criticalColor = criticalColor;
            this.headshotColor = headshotColor;
        }

        public String getTranslationKey() { return translationKey; }
    }

    public enum AnimationStyle {
        STATIC_POP("taczindicator.animation.static_pop"),
        STATIC_FADE("taczindicator.animation.static_fade"),
        SUBTLE_POP("taczindicator.animation.subtle_pop");

        private final String translationKey;

        AnimationStyle(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() { return translationKey; }
    }

    public enum CombatStatsDisplayMode {
        OFF("taczindicator.stats.mode.off"),
        COMBAT_ONLY("taczindicator.stats.mode.combat_only"),
        ALWAYS("taczindicator.stats.mode.always");

        private final String translationKey;

        CombatStatsDisplayMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() { return translationKey; }
    }

    public enum CombatStatsPosition {
        TOP_LEFT("taczindicator.stats.pos.top_left"),
        TOP_RIGHT("taczindicator.stats.pos.top_right"),
        BOTTOM_LEFT("taczindicator.stats.pos.bottom_left"),
        BOTTOM_RIGHT("taczindicator.stats.pos.bottom_right");

        private final String translationKey;

        CombatStatsPosition(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() { return translationKey; }
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "taczindicator-client.toml");
    }

    public static void saveConfig() {
        CLIENT_SPEC.save();
    }

    // --- 静的ヘルパーメソッド ---
    // [general]
    public static boolean isEnabled() { return CLIENT.isEnabled(); }
    public static boolean isOnlyPlayerDamage() { return CLIENT.isOnlyPlayerDamage(); }
    public static boolean isOnlyTaczDamage() { return CLIENT.isOnlyTaczDamage(); }
    public static boolean isDebugMode() { return CLIENT.isDebugMode(); }

    // [display]
    public static RenderMode getRenderMode() { return CLIENT.getRenderMode(); }
    public static ConsecutiveMode getConsecutiveMode() { return CLIENT.getConsecutiveMode(); }
    public static AnimationStyle getAnimationStyle() { return CLIENT.getAnimationStyle(); }
    public static int getComboTimeoutTicks() { return CLIENT.getComboTimeoutTicks(); }
    public static boolean isShowHitCount() { return CLIENT.isShowHitCount(); }
    public static boolean isShowKillAlert() { return CLIENT.isShowKillAlert(); }
    public static int getDecimalPlaces() { return CLIENT.getDecimalPlaces(); }
    public static int getMaxScrolledIndicators() { return CLIENT.getMaxScrolledIndicators(); }

    // [hud]
    public static double getHudScale() { return CLIENT.getHudScale(); }
    public static double getCrosshairOffsetX() { return CLIENT.getCrosshairOffsetX(); }
    public static double getCrosshairOffsetY() { return CLIENT.getCrosshairOffsetY(); }
    public static double getScrollSpacing() { return CLIENT.getScrollSpacing(); }
    public static double getKillAlertOffsetY() { return CLIENT.getKillAlertOffsetY(); }
    public static double getKillAlertScale() { return CLIENT.getKillAlertScale(); }

    // [world3d]
    public static boolean isConstantSize() { return CLIENT.isConstantSize(); }
    public static double getBaseScale() { return CLIENT.getBaseScale(); }
    public static double getDistanceScaleFactor() { return CLIENT.getDistanceScaleFactor(); }
    public static boolean isXRay() { return CLIENT.isXRay(); }
    public static double getRiseSpeed() { return CLIENT.getRiseSpeed(); }
    public static int getLifetime() { return CLIENT.getLifetime(); }

    // [icons_and_colors]
    public static ColorTheme getColorTheme() { return CLIENT.getColorTheme(); }
    public static boolean isShowHeadshotIcon() { return CLIENT.isShowHeadshotIcon(); }
    public static boolean isShowCriticalIcon() { return CLIENT.isShowCriticalIcon(); }
    public static boolean isShowArmorPiercingIcon() { return CLIENT.isShowArmorPiercingIcon(); }
    public static boolean isShowArmorDamageIcon() { return CLIENT.isShowArmorDamageIcon(); }
    public static int getNormalColor() { return CLIENT.getNormalColor(); }
    public static int getCriticalColor() { return CLIENT.getCriticalColor(); }
    public static int getHeadshotColor() { return CLIENT.getHeadshotColor(); }
    public static int getArmorPiercingColor() { return CLIENT.getArmorPiercingColor(); }
    public static int getTaczColor() { return CLIENT.getTaczColor(); }

    public static void applyColorTheme(ColorTheme theme) {
        if (theme != null && CLIENT != null) {
            CLIENT.colorTheme.set(theme);
            CLIENT.normalDamageColor.set(theme.normalColor);
            CLIENT.criticalDamageColor.set(theme.criticalColor);
            CLIENT.headshotDamageColor.set(theme.headshotColor);
        }
    }

    // [damage_vignette]
    public static boolean isDamageVignetteEnabled() { return CLIENT.isDamageVignetteEnabled(); }
    public static double getDamageVignetteOpacity() { return CLIENT.getDamageVignetteOpacity(); }
    public static int getDamageVignetteDurationTicks() { return CLIENT.getDamageVignetteDurationTicks(); }
    public static int getDamageVignetteColor() { return CLIENT.getDamageVignetteColor(); }
    public static boolean isDamageVignetteScaleWithDamage() { return CLIENT.isDamageVignetteScaleWithDamage(); }

    // [low_hp_vignette]
    public static boolean isLowHpVignetteEnabled() { return CLIENT.isLowHpVignetteEnabled(); }
    public static double getLowHpThreshold() { return CLIENT.getLowHpThreshold(); }
    public static double getLowHpVignetteOpacity() { return CLIENT.getLowHpVignetteOpacity(); }
    public static boolean isLowHpHeartbeatEnabled() { return CLIENT.isLowHpHeartbeatEnabled(); }
    public static double getLowHpHeartbeatSpeed() { return CLIENT.getLowHpHeartbeatSpeed(); }
    public static int getLowHpVignetteColor() { return CLIENT.getLowHpVignetteColor(); }

    // [sounds]
    public static boolean isHitSoundEnabled() { return CLIENT.isHitSoundEnabled(); }
    public static double getHitSoundVolume() { return CLIENT.getHitSoundVolume(); }
    public static boolean isHeadshotSoundEnabled() { return CLIENT.isHeadshotSoundEnabled(); }
    public static boolean isKillSoundEnabled() { return CLIENT.isKillSoundEnabled(); }
    public static double getKillSoundVolume() { return CLIENT.getKillSoundVolume(); }

    // [combat_stats]
    public static CombatStatsDisplayMode getCombatStatsMode() { return CLIENT.getCombatStatsMode(); }
    public static CombatStatsPosition getCombatStatsPosition() { return CLIENT.getCombatStatsPosition(); }
    public static double getCombatStatsScale() { return CLIENT.getCombatStatsScale(); }

    public static class Client {
        // [general] 全般設定
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue onlyPlayerDamage;
        public final ForgeConfigSpec.BooleanValue onlyTaczDamage;
        public final ForgeConfigSpec.BooleanValue debugMode;

        // [display] 表示動作設定
        public final ForgeConfigSpec.EnumValue<RenderMode> renderMode;
        public final ForgeConfigSpec.EnumValue<ConsecutiveMode> consecutiveMode;
        public final ForgeConfigSpec.EnumValue<AnimationStyle> animationStyle;
        public final ForgeConfigSpec.IntValue comboTimeoutTicks;
        public final ForgeConfigSpec.BooleanValue showHitCount;
        public final ForgeConfigSpec.BooleanValue showKillAlert;
        public final ForgeConfigSpec.IntValue decimalPlaces;
        public final ForgeConfigSpec.IntValue maxScrolledIndicators;

        // [hud] HUDレイヤー設定
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.DoubleValue crosshairOffsetX;
        public final ForgeConfigSpec.DoubleValue crosshairOffsetY;
        public final ForgeConfigSpec.DoubleValue scrollSpacing;
        public final ForgeConfigSpec.DoubleValue killAlertOffsetY;
        public final ForgeConfigSpec.DoubleValue killAlertScale;

        // [world3d] 3Dワールド空間設定
        public final ForgeConfigSpec.BooleanValue enableConstantSize;
        public final ForgeConfigSpec.DoubleValue baseScale;
        public final ForgeConfigSpec.DoubleValue distanceScaleFactor;
        public final ForgeConfigSpec.BooleanValue enableXRay;
        public final ForgeConfigSpec.DoubleValue riseSpeed;
        public final ForgeConfigSpec.IntValue lifetimeTicks;

        // [icons_and_colors] アイコン・カラー設定
        public final ForgeConfigSpec.EnumValue<ColorTheme> colorTheme;
        public final ForgeConfigSpec.BooleanValue showHeadshotIcon;
        public final ForgeConfigSpec.BooleanValue showCriticalIcon;
        public final ForgeConfigSpec.BooleanValue showArmorPiercingIcon;
        public final ForgeConfigSpec.BooleanValue showArmorDamageIcon;
        public final ForgeConfigSpec.IntValue normalDamageColor;
        public final ForgeConfigSpec.IntValue criticalDamageColor;
        public final ForgeConfigSpec.IntValue headshotDamageColor;
        public final ForgeConfigSpec.IntValue armorPiercingColor;
        public final ForgeConfigSpec.IntValue taczDamageColor;

        // [damage_vignette] 被ダメージ画面エフェクト設定
        public final ForgeConfigSpec.BooleanValue enableDamageVignette;
        public final ForgeConfigSpec.DoubleValue damageVignetteOpacity;
        public final ForgeConfigSpec.IntValue damageVignetteDurationTicks;
        public final ForgeConfigSpec.IntValue damageVignetteColor;
        public final ForgeConfigSpec.BooleanValue damageVignetteScaleWithDamage;

        // [low_hp_vignette] 瀕死時画面エフェクト・鼓動設定
        public final ForgeConfigSpec.BooleanValue enableLowHpVignette;
        public final ForgeConfigSpec.DoubleValue lowHpThreshold;
        public final ForgeConfigSpec.DoubleValue lowHpVignetteOpacity;
        public final ForgeConfigSpec.BooleanValue enableLowHpHeartbeat;
        public final ForgeConfigSpec.DoubleValue lowHpHeartbeatSpeed;
        public final ForgeConfigSpec.IntValue lowHpVignetteColor;

        // [sounds] サウンド設定
        public final ForgeConfigSpec.BooleanValue enableHitSound;
        public final ForgeConfigSpec.DoubleValue hitSoundVolume;
        public final ForgeConfigSpec.BooleanValue enableHeadshotSound;
        public final ForgeConfigSpec.BooleanValue enableKillSound;
        public final ForgeConfigSpec.DoubleValue killSoundVolume;

        // [combat_stats] 戦闘統計・DPSメーター設定
        public final ForgeConfigSpec.EnumValue<CombatStatsDisplayMode> combatStatsMode;
        public final ForgeConfigSpec.EnumValue<CombatStatsPosition> combatStatsPosition;
        public final ForgeConfigSpec.DoubleValue combatStatsScale;

        public Client(ForgeConfigSpec.Builder builder) {
            // -------------------------------------------------------------
            // 1. [general] 全般設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 1. 全般設定 (General Settings)",
                            "==================================================").push("general");

            enabled = builder
                    .comment("ダメージインジケータ機能を有効化するかどうか")
                    .define("enabled", true);

            onlyPlayerDamage = builder
                    .comment("自分（プレイヤー）が与えたダメージのみを表示するかどうか (true: プレイヤーのみ, false: 全ダメージ表示)")
                    .define("onlyPlayerDamage", true);

            onlyTaczDamage = builder
                    .comment("TaCZの銃器によるダメージのみを表示するかどうか (true: 銃撃のみ, false: 近接・魔法等も表示)")
                    .define("onlyTaczDamage", false);

            debugMode = builder
                    .comment("デバッグモード: ワールド参加時の動作モード案内やログ詳細出力を有効化するかどうか")
                    .define("debugMode", false);

            builder.pop();

            // -------------------------------------------------------------
            // 2. [display] 表示動作設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 2. 表示・連続ダメージ設定 (Display & Combo Settings)",
                            "==================================================").push("display");

            renderMode = builder
                    .comment("描画モード:",
                             "  HUD_CROSSHAIR  : 照準（クロスヘア）横のHUDに表示（推奨）",
                             "  WORLD_3D       : 3Dワールド空間（モブの頭上・画面上同一サイズ）",
                             "  HUD_PROJECTED  : 3D座標を2D画面上に投影")
                    .defineEnum("renderMode", RenderMode.HUD_CROSSHAIR);

            consecutiveMode = builder
                    .comment("連続ダメージ（コンボ）の表示形式:",
                             "  ACCUMULATE : その場で数値を合算しポップアップ（例: 45.0 (x3)）",
                             "  SCROLL_UP  : 古い数値を上へ押し上げて順番に流す",
                             "  OFF        : 毎回個別に新規表示")
                    .defineEnum("consecutiveMode", ConsecutiveMode.ACCUMULATE);

            animationStyle = builder
                    .comment("ダメージ数値のアニメーションスタイル (位置がブレない高視認性スタイル):",
                             "  STATIC_POP  : その場で拡大ポップ（移動なし・推奨デフォルト）",
                             "  STATIC_FADE : 拡大も移動もせずスムーズにフェードアウト（ミニマル）",
                             "  SUBTLE_POP  : わずかに浮き上がって静止する控えめなポップ")
                    .defineEnum("animationStyle", AnimationStyle.STATIC_POP);

            comboTimeoutTicks = builder
                    .comment("連続ヒットと判定する制限時間（Tick単位: 20Ticks = 1秒）")
                    .defineInRange("comboTimeoutTicks", 30, 5, 100);

            showHitCount = builder
                    .comment("ACCUMULATEモード時にヒット数を併記するかどうか (例: 45.0 (x3))")
                    .define("showHitCount", true);

            showKillAlert = builder
                    .comment("敵撃破時にレティクル下にキル確定通知（Killed ゾンビ (x2) [100m]）を表示するかどうか")
                    .define("showKillAlert", true);

            decimalPlaces = builder
                    .comment("ダメージ数値の小数点以下表示桁数 (0: 整数のみ, 1: 小数第1位まで)")
                    .defineInRange("decimalPlaces", 1, 0, 3);

            maxScrolledIndicators = builder
                    .comment("SCROLL_UPモード時に画面上に保持・表示する最大インジケータ数（上限を超えた古い数値は自動消去）")
                    .defineInRange("maxScrolledIndicators", 6, 1, 20);

            builder.pop();

            // -------------------------------------------------------------
            // 3. [hud] HUDレイヤー調整
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 3. HUDレイヤー調整 (HUD Layout Settings)",
                            "==================================================").push("hud");

            hudScale = builder
                    .comment("HUD表示時の文字拡大スケール (1.0 = 標準)")
                    .defineInRange("hudScale", 1.15D, 0.2D, 4.0D);

            crosshairOffsetX = builder
                    .comment("HUD_CROSSHAIRモード時の画面中心（レティクル）からのXオフセット（ピクセル）")
                    .defineInRange("crosshairOffsetX", 18.0D, -300.0D, 300.0D);

            crosshairOffsetY = builder
                    .comment("HUD_CROSSHAIRモード時の画面中心（レティクル）からのYオフセット（ピクセル）")
                    .defineInRange("crosshairOffsetY", -4.0D, -300.0D, 300.0D);

            scrollSpacing = builder
                    .comment("SCROLL_UPモードで古い数値を上に押し上げるピクセル間隔")
                    .defineInRange("scrollSpacing", 14.0D, 2.0D, 50.0D);

            killAlertOffsetY = builder
                    .comment("キル確定通知の画面中心（レティクル）からのYオフセット（ピクセル）")
                    .defineInRange("killAlertOffsetY", 28.0D, -300.0D, 300.0D);

            killAlertScale = builder
                    .comment("キル確定通知の文字拡大スケール")
                    .defineInRange("killAlertScale", 1.10D, 0.2D, 4.0D);

            builder.pop();

            // -------------------------------------------------------------
            // 4. [world3d] 3Dワールド空間設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 4. 3Dワールド空間設定 (3D World Render Settings)",
                            "==================================================").push("world3d");

            enableConstantSize = builder
                    .comment("WORLD_3Dモード時: 距離に関わらず画面上で同じ大きさ（角度サイズ一定）で表示するかどうか")
                    .define("enableConstantSize", true);

            baseScale = builder
                    .comment("WORLD_3Dモード時: 基本描画スケール")
                    .defineInRange("baseScale", 0.025D, 0.001D, 0.5D);

            distanceScaleFactor = builder
                    .comment("WORLD_3Dモード時: 距離に応じた拡大係数（1.0 = 完全等比拡大）")
                    .defineInRange("distanceScaleFactor", 1.0D, 0.01D, 10.0D);

            enableXRay = builder
                    .comment("WORLD_3Dモード時: 壁や遮蔽物の向こう側でもインジケータを透過表示するかどうか")
                    .define("enableXRay", true);

            riseSpeed = builder
                    .comment("インジケータの上昇速度")
                    .defineInRange("riseSpeed", 0.025D, 0.0D, 0.5D);

            lifetimeTicks = builder
                    .comment("インジケータの表示持続時間（Tick単位: 20Ticks = 1秒）")
                    .defineInRange("lifetimeTicks", 35, 5, 200);

            builder.pop();

            // -------------------------------------------------------------
            // 5. [icons_and_colors] アイコン・カラー設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 5. アイコン・カラー設定 (Icons & Colors Settings)",
                            "==================================================").push("icons_and_colors");

            colorTheme = builder
                    .comment("カラーテーマ・プリセット (DEFAULT, APEX, CYBERPUNK, TACTICAL_COD, VALORANT)")
                    .defineEnum("colorTheme", ColorTheme.DEFAULT);

            showHeadshotIcon = builder
                    .comment("ヘッドショット時にドクロアイコン(☠)を表示するかどうか")
                    .define("showHeadshotIcon", true);

            showCriticalIcon = builder
                    .comment("クリティカル時にスターアイコン(★)を表示するかどうか")
                    .define("showCriticalIcon", true);

            showArmorPiercingIcon = builder
                    .comment("防具貫通弾(AP)命中時に貫通アイコン(shield_penetration.png)を表示するかどうか")
                    .define("showArmorPiercingIcon", true);

            showArmorDamageIcon = builder
                    .comment("防具装備モブに命中した際に防具軽減アイコン(shield.png)を表示するかどうか")
                    .define("showArmorDamageIcon", true);

            normalDamageColor = builder
                    .comment("通常ダメージの文字色 (RGB Hex 0xRRGGBB)")
                    .defineInRange("normalDamageColor", 0xFFFFFF, 0, 0xFFFFFF);

            criticalDamageColor = builder
                    .comment("クリティカルダメージの文字色 (RGB Hex 0xRRGGBB)")
                    .defineInRange("criticalDamageColor", 0xFFCC00, 0, 0xFFFFFF);

            headshotDamageColor = builder
                    .comment("ヘッドショットダメージの文字色 (RGB Hex 0xRRGGBB)")
                    .defineInRange("headshotDamageColor", 0xFF3333, 0, 0xFFFFFF);

            armorPiercingColor = builder
                    .comment("防具貫通ダメージの文字色 (RGB Hex 0xRRGGBB)")
                    .defineInRange("armorPiercingColor", 0x33CCFF, 0, 0xFFFFFF);

            taczDamageColor = builder
                    .comment("銃撃ダメージの文字色 (RGB Hex 0xRRGGBB)")
                    .defineInRange("taczDamageColor", 0xFFFFFF, 0, 0xFFFFFF);

            builder.pop();

            // -------------------------------------------------------------
            // 6. [damage_vignette] 被ダメージ画面エフェクト設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 6. 被ダメージ画面効果設定 (Damage Screen Vignette Settings)",
                            "==================================================").push("damage_vignette");

            enableDamageVignette = builder
                    .comment("プレイヤー被ダメージ時の画面赤色効果（ヴィネット/フラッシュ）を有効化するかどうか")
                    .define("enableDamageVignette", true);

            damageVignetteOpacity = builder
                    .comment("被ダメージ画面効果の最大不透明度 (0.0: 完全透明/無効, 1.0: 最大不透明度)")
                    .defineInRange("damageVignetteOpacity", 0.28D, 0.0D, 1.0D);

            damageVignetteDurationTicks = builder
                    .comment("被ダメージ画面効果の表示持続時間（Tick単位: 20Ticks = 1秒）")
                    .defineInRange("damageVignetteDurationTicks", 20, 1, 100);

            damageVignetteColor = builder
                    .comment("被ダメージ画面効果の色 (RGB Hex 0xRRGGBB, デフォルト: 0xFF0000)")
                    .defineInRange("damageVignetteColor", 0xFF0000, 0, 0xFFFFFF);

            damageVignetteScaleWithDamage = builder
                    .comment("受けたダメージ量に応じて画面赤色効果の濃さを自動調整するかどうか")
                    .define("damageVignetteScaleWithDamage", true);

            builder.pop();

            // -------------------------------------------------------------
            // 7. [low_hp_vignette] 瀕死時画面エフェクト・鼓動設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 7. 瀕死時画面エフェクト・鼓動設定 (Low HP Vignette & Heartbeat Settings)",
                            "==================================================").push("low_hp_vignette");

            enableLowHpVignette = builder
                    .comment("プレイヤーのHPが低下した際に画面端に赤いヴィネット効果を表示するかどうか")
                    .define("enableLowHpVignette", true);

            lowHpThreshold = builder
                    .comment("瀕死時ヴィネットを発動する体力割合の閾値 (0.30 = 最大HPの30%以下 / ハート3個以下)")
                    .defineInRange("lowHpThreshold", 0.30D, 0.05D, 0.80D);

            lowHpVignetteOpacity = builder
                    .comment("瀕死時ヴィネット効果の基準不透明度 (0.0 〜 1.0)")
                    .defineInRange("lowHpVignetteOpacity", 0.22D, 0.0D, 1.0D);

            enableLowHpHeartbeat = builder
                    .comment("瀕死時に心臓の鼓動のような脈動アニメーション（パルス）を適用するかどうか")
                    .define("enableLowHpHeartbeat", true);

            lowHpHeartbeatSpeed = builder
                    .comment("瀕死時鼓動アニメーションの脈動速度倍率 (1.0 = 標準)")
                    .defineInRange("lowHpHeartbeatSpeed", 1.0D, 0.2D, 3.0D);

            lowHpVignetteColor = builder
                    .comment("瀕死時ヴィネット効果の色 (RGB Hex 0xRRGGBB, デフォルト: 0xFF0000)")
                    .defineInRange("lowHpVignetteColor", 0xFF0000, 0, 0xFFFFFF);

            builder.pop();

            // -------------------------------------------------------------
            // 8. [sounds] サウンド設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 8. サウンド設定 (Sound Effects Settings)",
                            "==================================================").push("sounds");

            enableHitSound = builder
                    .comment("攻撃命中時のヒット音を再生するかどうか (他MODとの競合防止のためデフォルトOFF)")
                    .define("enableHitSound", false);

            hitSoundVolume = builder
                    .comment("ヒット音の音量 (0.0: 無音, 1.0: 最大音量)")
                    .defineInRange("hitSoundVolume", 0.8D, 0.0D, 1.0D);

            enableHeadshotSound = builder
                    .comment("ヘッドショット命中時の高音キーン音を再生するかどうか")
                    .define("enableHeadshotSound", false);

            enableKillSound = builder
                    .comment("敵撃破時のキル確定音を再生するかどうか")
                    .define("enableKillSound", false);

            killSoundVolume = builder
                    .comment("キル確定音の音量 (0.0: 無音, 1.0: 最大音量)")
                    .defineInRange("killSoundVolume", 0.9D, 0.0D, 1.0D);

            builder.pop();

            // -------------------------------------------------------------
            // 8. [combat_stats] 戦闘統計・DPSメーター設定
            // -------------------------------------------------------------
            builder.comment("==================================================",
                            " 8. 戦闘統計・DPSメーター設定 (Combat Stats & DPS Meter Settings)",
                            "==================================================").push("combat_stats");

            combatStatsMode = builder
                    .comment("戦闘統計（DPS・総ダメージ・命中数・HS率・キル数）のHUD表示モード:",
                             "  OFF         : 非表示 (デフォルト)",
                             "  COMBAT_ONLY : 戦闘中のみ表示（非戦闘時に5秒でフェードアウト）",
                             "  ALWAYS      : 常に画面上に表示")
                    .defineEnum("combatStatsMode", CombatStatsDisplayMode.OFF);

            combatStatsPosition = builder
                    .comment("戦闘統計カードの画面配置位置 (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)")
                    .defineEnum("combatStatsPosition", CombatStatsPosition.TOP_RIGHT);

            combatStatsScale = builder
                    .comment("戦闘統計カードのHUD拡大スケール")
                    .defineInRange("combatStatsScale", 1.0D, 0.5D, 2.5D);

            builder.pop();
        }

        // 安全なゲッター
        public boolean isEnabled() {
            try { return enabled != null && enabled.get(); } catch (Exception e) { return true; }
        }
        public boolean isOnlyPlayerDamage() {
            try { return onlyPlayerDamage != null && onlyPlayerDamage.get(); } catch (Exception e) { return true; }
        }
        public boolean isOnlyTaczDamage() {
            try { return onlyTaczDamage != null && onlyTaczDamage.get(); } catch (Exception e) { return false; }
        }
        public boolean isDebugMode() {
            try { return debugMode != null && debugMode.get(); } catch (Exception e) { return false; }
        }
        public RenderMode getRenderMode() {
            try { return renderMode != null ? renderMode.get() : RenderMode.HUD_CROSSHAIR; } catch (Exception e) { return RenderMode.HUD_CROSSHAIR; }
        }
        public ConsecutiveMode getConsecutiveMode() {
            try { return consecutiveMode != null ? consecutiveMode.get() : ConsecutiveMode.ACCUMULATE; } catch (Exception e) { return ConsecutiveMode.ACCUMULATE; }
        }
        public AnimationStyle getAnimationStyle() {
            try { return animationStyle != null ? animationStyle.get() : AnimationStyle.STATIC_POP; } catch (Exception e) { return AnimationStyle.STATIC_POP; }
        }
        public int getComboTimeoutTicks() {
            try { return comboTimeoutTicks != null ? comboTimeoutTicks.get() : 30; } catch (Exception e) { return 30; }
        }
        public boolean isShowHitCount() {
            try { return showHitCount != null && showHitCount.get(); } catch (Exception e) { return true; }
        }
        public boolean isShowKillAlert() {
            try { return showKillAlert != null && showKillAlert.get(); } catch (Exception e) { return true; }
        }
        public int getDecimalPlaces() {
            try { return decimalPlaces != null ? decimalPlaces.get() : 1; } catch (Exception e) { return 1; }
        }
        public int getMaxScrolledIndicators() {
            try { return maxScrolledIndicators != null ? maxScrolledIndicators.get() : 6; } catch (Exception e) { return 6; }
        }
        public double getHudScale() {
            try { return hudScale != null ? hudScale.get() : 1.15D; } catch (Exception e) { return 1.15D; }
        }
        public double getCrosshairOffsetX() {
            try { return crosshairOffsetX != null ? crosshairOffsetX.get() : 18.0D; } catch (Exception e) { return 18.0D; }
        }
        public double getCrosshairOffsetY() {
            try { return crosshairOffsetY != null ? crosshairOffsetY.get() : -4.0D; } catch (Exception e) { return -4.0D; }
        }
        public double getScrollSpacing() {
            try { return scrollSpacing != null ? scrollSpacing.get() : 14.0D; } catch (Exception e) { return 14.0D; }
        }
        public double getKillAlertOffsetY() {
            try { return killAlertOffsetY != null ? killAlertOffsetY.get() : 28.0D; } catch (Exception e) { return 28.0D; }
        }
        public double getKillAlertScale() {
            try { return killAlertScale != null ? killAlertScale.get() : 1.10D; } catch (Exception e) { return 1.10D; }
        }
        public boolean isConstantSize() {
            try { return enableConstantSize != null && enableConstantSize.get(); } catch (Exception e) { return true; }
        }
        public double getBaseScale() {
            try { return baseScale != null ? baseScale.get() : 0.025D; } catch (Exception e) { return 0.025D; }
        }
        public double getDistanceScaleFactor() {
            try { return distanceScaleFactor != null ? distanceScaleFactor.get() : 1.0D; } catch (Exception e) { return 1.0D; }
        }
        public boolean isXRay() {
            try { return enableXRay != null && enableXRay.get(); } catch (Exception e) { return true; }
        }
        public double getRiseSpeed() {
            try { return riseSpeed != null ? riseSpeed.get() : 0.025D; } catch (Exception e) { return 0.025D; }
        }
        public int getLifetime() {
            try { return lifetimeTicks != null ? lifetimeTicks.get() : 35; } catch (Exception e) { return 35; }
        }
        public ColorTheme getColorTheme() {
            try { return colorTheme != null ? colorTheme.get() : ColorTheme.DEFAULT; } catch (Exception e) { return ColorTheme.DEFAULT; }
        }
        public boolean isShowHeadshotIcon() {
            try { return showHeadshotIcon != null && showHeadshotIcon.get(); } catch (Exception e) { return true; }
        }
        public boolean isShowCriticalIcon() {
            try { return showCriticalIcon != null && showCriticalIcon.get(); } catch (Exception e) { return true; }
        }
        public boolean isShowArmorPiercingIcon() {
            try { return showArmorPiercingIcon != null && showArmorPiercingIcon.get(); } catch (Exception e) { return true; }
        }
        public boolean isShowArmorDamageIcon() {
            try { return showArmorDamageIcon != null && showArmorDamageIcon.get(); } catch (Exception e) { return true; }
        }
        public int getNormalColor() {
            try { return normalDamageColor != null ? normalDamageColor.get() : 0xFFFFFF; } catch (Exception e) { return 0xFFFFFF; }
        }
        public int getCriticalColor() {
            try { return criticalDamageColor != null ? criticalDamageColor.get() : 0xFFCC00; } catch (Exception e) { return 0xFFCC00; }
        }
        public int getHeadshotColor() {
            try { return headshotDamageColor != null ? headshotDamageColor.get() : 0xFF3333; } catch (Exception e) { return 0xFF3333; }
        }
        public int getArmorPiercingColor() {
            try { return armorPiercingColor != null ? armorPiercingColor.get() : 0x33CCFF; } catch (Exception e) { return 0x33CCFF; }
        }
        public int getTaczColor() {
            try { return taczDamageColor != null ? taczDamageColor.get() : 0xFFFFFF; } catch (Exception e) { return 0xFFFFFF; }
        }
        public boolean isDamageVignetteEnabled() {
            try { return enableDamageVignette != null && enableDamageVignette.get(); } catch (Exception e) { return true; }
        }
        public double getDamageVignetteOpacity() {
            try { return damageVignetteOpacity != null ? damageVignetteOpacity.get() : 0.28D; } catch (Exception e) { return 0.28D; }
        }
        public int getDamageVignetteDurationTicks() {
            try { return damageVignetteDurationTicks != null ? damageVignetteDurationTicks.get() : 20; } catch (Exception e) { return 20; }
        }
        public int getDamageVignetteColor() {
            try { return damageVignetteColor != null ? damageVignetteColor.get() : 0xFF0000; } catch (Exception e) { return 0xFF0000; }
        }
        public boolean isDamageVignetteScaleWithDamage() {
            try { return damageVignetteScaleWithDamage != null && damageVignetteScaleWithDamage.get(); } catch (Exception e) { return true; }
        }
        public boolean isLowHpVignetteEnabled() {
            try { return enableLowHpVignette != null && enableLowHpVignette.get(); } catch (Exception e) { return true; }
        }
        public double getLowHpThreshold() {
            try { return lowHpThreshold != null ? lowHpThreshold.get() : 0.30D; } catch (Exception e) { return 0.30D; }
        }
        public double getLowHpVignetteOpacity() {
            try { return lowHpVignetteOpacity != null ? lowHpVignetteOpacity.get() : 0.22D; } catch (Exception e) { return 0.22D; }
        }
        public boolean isLowHpHeartbeatEnabled() {
            try { return enableLowHpHeartbeat != null && enableLowHpHeartbeat.get(); } catch (Exception e) { return true; }
        }
        public double getLowHpHeartbeatSpeed() {
            try { return lowHpHeartbeatSpeed != null ? lowHpHeartbeatSpeed.get() : 1.0D; } catch (Exception e) { return 1.0D; }
        }
        public int getLowHpVignetteColor() {
            try { return lowHpVignetteColor != null ? lowHpVignetteColor.get() : 0xFF0000; } catch (Exception e) { return 0xFF0000; }
        }
        public boolean isHitSoundEnabled() {
            try { return enableHitSound != null && enableHitSound.get(); } catch (Exception e) { return true; }
        }
        public double getHitSoundVolume() {
            try { return hitSoundVolume != null ? hitSoundVolume.get() : 0.8D; } catch (Exception e) { return 0.8D; }
        }
        public boolean isHeadshotSoundEnabled() {
            try { return enableHeadshotSound != null && enableHeadshotSound.get(); } catch (Exception e) { return true; }
        }
        public boolean isKillSoundEnabled() {
            try { return enableKillSound != null && enableKillSound.get(); } catch (Exception e) { return true; }
        }
        public double getKillSoundVolume() {
            try { return killSoundVolume != null ? killSoundVolume.get() : 0.9D; } catch (Exception e) { return 0.9D; }
        }
        public CombatStatsDisplayMode getCombatStatsMode() {
            try { return combatStatsMode != null ? combatStatsMode.get() : CombatStatsDisplayMode.OFF; } catch (Exception e) { return CombatStatsDisplayMode.OFF; }
        }
        public CombatStatsPosition getCombatStatsPosition() {
            try { return combatStatsPosition != null ? combatStatsPosition.get() : CombatStatsPosition.TOP_RIGHT; } catch (Exception e) { return CombatStatsPosition.TOP_RIGHT; }
        }
        public double getCombatStatsScale() {
            try { return combatStatsScale != null ? combatStatsScale.get() : 1.0D; } catch (Exception e) { return 1.0D; }
        }
    }
}
