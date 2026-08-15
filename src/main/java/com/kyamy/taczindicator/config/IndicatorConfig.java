package com.kyamy.taczindicator.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * ダメージインジケータの設定管理クラス
 * わかりやすく整理されたカテゴリ構造（全般・表示・HUD・3D空間・アイコン/カラー）
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
    public static int getComboTimeoutTicks() { return CLIENT.getComboTimeoutTicks(); }
    public static boolean isShowHitCount() { return CLIENT.isShowHitCount(); }
    public static boolean isShowKillAlert() { return CLIENT.isShowKillAlert(); }
    public static int getDecimalPlaces() { return CLIENT.getDecimalPlaces(); }

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
    public static boolean isShowHeadshotIcon() { return CLIENT.isShowHeadshotIcon(); }
    public static boolean isShowCriticalIcon() { return CLIENT.isShowCriticalIcon(); }
    public static boolean isShowArmorPiercingIcon() { return CLIENT.isShowArmorPiercingIcon(); }
    public static boolean isShowArmorDamageIcon() { return CLIENT.isShowArmorDamageIcon(); }
    public static int getNormalColor() { return CLIENT.getNormalColor(); }
    public static int getCriticalColor() { return CLIENT.getCriticalColor(); }
    public static int getHeadshotColor() { return CLIENT.getHeadshotColor(); }
    public static int getArmorPiercingColor() { return CLIENT.getArmorPiercingColor(); }
    public static int getTaczColor() { return CLIENT.getTaczColor(); }

    public static class Client {
        // [general] 全般設定
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue onlyPlayerDamage;
        public final ForgeConfigSpec.BooleanValue onlyTaczDamage;
        public final ForgeConfigSpec.BooleanValue debugMode;

        // [display] 表示動作設定
        public final ForgeConfigSpec.EnumValue<RenderMode> renderMode;
        public final ForgeConfigSpec.EnumValue<ConsecutiveMode> consecutiveMode;
        public final ForgeConfigSpec.IntValue comboTimeoutTicks;
        public final ForgeConfigSpec.BooleanValue showHitCount;
        public final ForgeConfigSpec.BooleanValue showKillAlert;
        public final ForgeConfigSpec.IntValue decimalPlaces;

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
        public final ForgeConfigSpec.BooleanValue showHeadshotIcon;
        public final ForgeConfigSpec.BooleanValue showCriticalIcon;
        public final ForgeConfigSpec.BooleanValue showArmorPiercingIcon;
        public final ForgeConfigSpec.BooleanValue showArmorDamageIcon;
        public final ForgeConfigSpec.IntValue normalDamageColor;
        public final ForgeConfigSpec.IntValue criticalDamageColor;
        public final ForgeConfigSpec.IntValue headshotDamageColor;
        public final ForgeConfigSpec.IntValue armorPiercingColor;
        public final ForgeConfigSpec.IntValue taczDamageColor;

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

            comboTimeoutTicks = builder
                    .comment("連続ヒットと判定する制限時間（Tick単位: 20Ticks = 1秒）")
                    .defineInRange("comboTimeoutTicks", 30, 5, 100);

            showHitCount = builder
                    .comment("ACCUMULATEモード時にヒット数を併記するかどうか (例: 45.0 (x3))")
                    .define("showHitCount", true);

            showKillAlert = builder
                    .comment("敵撃破時にレティクル下にキル確定通知（Killed ゾンビ x2）を表示するかどうか")
                    .define("showKillAlert", true);

            decimalPlaces = builder
                    .comment("ダメージ数値の小数点以下表示桁数 (0: 整数のみ, 1: 小数第1位まで)")
                    .defineInRange("decimalPlaces", 1, 0, 3);

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

            showHeadshotIcon = builder
                    .comment("ヘッドショット時にドクロアイコン(☠)を表示するかどうか")
                    .define("showHeadshotIcon", true);

            showCriticalIcon = builder
                    .comment("クリティカル時にスターアイコン(★)を表示するかどうか")
                    .define("showCriticalIcon", true);

            showArmorPiercingIcon = builder
                    .comment("防具貫通弾(AP)命中時に貫通アイコン(🗡)を表示するかどうか")
                    .define("showArmorPiercingIcon", true);

            showArmorDamageIcon = builder
                    .comment("防具装備モブに命中した際に防具軽減アイコン(🛡)を表示するかどうか")
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
    }
}
