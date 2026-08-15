package com.kyamy.taczindicator.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * ダメージインジケータの設定管理クラス
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

    // --- 静的ヘルパーメソッド ---
    public static boolean isEnabled() { return CLIENT.isEnabled(); }
    public static RenderMode getRenderMode() { return CLIENT.getRenderMode(); }
    public static ConsecutiveMode getConsecutiveMode() { return CLIENT.getConsecutiveMode(); }
    public static int getComboTimeoutTicks() { return CLIENT.getComboTimeoutTicks(); }
    public static double getHudScale() { return CLIENT.getHudScale(); }
    public static double getScrollSpacing() { return CLIENT.getScrollSpacing(); }
    public static double getCrosshairOffsetX() { return CLIENT.getCrosshairOffsetX(); }
    public static double getCrosshairOffsetY() { return CLIENT.getCrosshairOffsetY(); }
    public static boolean isShowHitCount() { return CLIENT.isShowHitCount(); }
    public static boolean isConstantSize() { return CLIENT.isConstantSize(); }
    public static double getBaseScale() { return CLIENT.getBaseScale(); }
    public static double getDistanceScaleFactor() { return CLIENT.getDistanceScaleFactor(); }
    public static int getLifetime() { return CLIENT.getLifetime(); }
    public static double getRiseSpeed() { return CLIENT.getRiseSpeed(); }
    public static boolean isXRay() { return CLIENT.isXRay(); }
    public static boolean isShowHeadshotIcon() { return CLIENT.isShowHeadshotIcon(); }
    public static int getDecimalPlaces() { return CLIENT.getDecimalPlaces(); }
    public static int getNormalColor() { return CLIENT.getNormalColor(); }
    public static int getCriticalColor() { return CLIENT.getCriticalColor(); }
    public static int getHeadshotColor() { return CLIENT.getHeadshotColor(); }
    public static int getTaczColor() { return CLIENT.getTaczColor(); }

    public static class Client {
        // 全般設定
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.EnumValue<RenderMode> renderMode;
        public final ForgeConfigSpec.EnumValue<ConsecutiveMode> consecutiveMode;
        public final ForgeConfigSpec.IntValue comboTimeoutTicks;
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.DoubleValue scrollSpacing;
        public final ForgeConfigSpec.DoubleValue crosshairOffsetX;
        public final ForgeConfigSpec.DoubleValue crosshairOffsetY;
        public final ForgeConfigSpec.BooleanValue showHitCount;

        // 3D/ワールド設定（WORLD_3Dモード時）
        public final ForgeConfigSpec.BooleanValue enableConstantSize;
        public final ForgeConfigSpec.DoubleValue baseScale;
        public final ForgeConfigSpec.DoubleValue distanceScaleFactor;
        public final ForgeConfigSpec.IntValue lifetimeTicks;
        public final ForgeConfigSpec.DoubleValue riseSpeed;
        public final ForgeConfigSpec.BooleanValue enableXRay;
        public final ForgeConfigSpec.BooleanValue showHeadshotIcon;

        // カラー設定 (0xRRGGBB)
        public final ForgeConfigSpec.IntValue normalDamageColor;
        public final ForgeConfigSpec.IntValue criticalDamageColor;
        public final ForgeConfigSpec.IntValue headshotDamageColor;
        public final ForgeConfigSpec.IntValue taczDamageColor;

        // 表示フォーマット設定
        public final ForgeConfigSpec.IntValue decimalPlaces;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.comment("TaCZ Damage Indicator Client Settings").push("general");

            enabled = builder
                    .comment("インジケータ表示を有効化するかどうか")
                    .define("enabled", true);

            renderMode = builder
                    .comment("描画モード: HUD_CROSSHAIR (照準横のHUD・推奨), WORLD_3D (3Dワールド空間・画面上同一サイズ), HUD_PROJECTED (画面HUD上に3D投影)")
                    .defineEnum("renderMode", RenderMode.HUD_CROSSHAIR);

            consecutiveMode = builder
                    .comment("連続ダメージ処理モード: ACCUMULATE (加算・累積表示), SCROLL_UP (古い数値を上へはけさせる/スクロール), OFF (個別表示)")
                    .defineEnum("consecutiveMode", ConsecutiveMode.ACCUMULATE);

            comboTimeoutTicks = builder
                    .comment("連続ヒットと判定する持続時間（Tick単位: 20Ticks = 1秒）")
                    .defineInRange("comboTimeoutTicks", 30, 5, 100);

            hudScale = builder
                    .comment("HUD表示時の文字拡大スケール (1.0 = 標準)")
                    .defineInRange("hudScale", 1.15D, 0.2D, 4.0D);

            scrollSpacing = builder
                    .comment("SCROLL_UPモードで古いインジケータを上に押し上げる間隔（ピクセル）")
                    .defineInRange("scrollSpacing", 14.0D, 2.0D, 50.0D);

            crosshairOffsetX = builder
                    .comment("HUD_CROSSHAIRモード時の画面中心（レティクル）からのXオフセット（ピクセル）")
                    .defineInRange("crosshairOffsetX", 18.0D, -300.0D, 300.0D);

            crosshairOffsetY = builder
                    .comment("HUD_CROSSHAIRモード時の画面中心（レティクル）からのYオフセット（ピクセル）")
                    .defineInRange("crosshairOffsetY", -4.0D, -300.0D, 300.0D);

            showHitCount = builder
                    .comment("加算モード時にヒット数を表示するかどうか (例: 45.0 x3)")
                    .define("showHitCount", true);

            enableConstantSize = builder
                    .comment("WORLD_3Dモード時: 距離に関わらず画面上で同じ大きさ（角度サイズ一定）で表示するかどうか")
                    .define("enableConstantSize", true);

            baseScale = builder
                    .comment("WORLD_3Dモード時: 基本描画スケール（1ブロックを基準としたフォント比率）")
                    .defineInRange("baseScale", 0.025D, 0.001D, 0.5D);

            distanceScaleFactor = builder
                    .comment("WORLD_3Dモード時: 距離に応じた拡大係数（一定サイズモードで 1.0 = 完全等比拡大）")
                    .defineInRange("distanceScaleFactor", 1.0D, 0.01D, 10.0D);

            lifetimeTicks = builder
                    .comment("インジケータが表示されてから消えるまでの時間（Tick単位: 20Ticks = 1秒）")
                    .defineInRange("lifetimeTicks", 35, 5, 200);

            riseSpeed = builder
                    .comment("インジケータの上昇速度")
                    .defineInRange("riseSpeed", 0.025D, 0.0D, 0.5D);

            enableXRay = builder
                    .comment("WORLD_3Dモード時: 壁や遮蔽物の向こう側でもインジケータを透過表示するかどうか")
                    .define("enableXRay", true);

            showHeadshotIcon = builder
                    .comment("ヘッドショット時にヘッドショットアイコンを表示するかどうか")
                    .define("showHeadshotIcon", true);

            decimalPlaces = builder
                    .comment("ダメージ値の小数点以下表示桁数")
                    .defineInRange("decimalPlaces", 1, 0, 3);

            builder.pop();

            builder.comment("Damage Indicator Colors (RGB Hex)").push("colors");

            normalDamageColor = builder
                    .comment("通常ダメージの色 (0xRRGGBB)")
                    .defineInRange("normalDamageColor", 0xFFFFFF, 0, 0xFFFFFF);

            criticalDamageColor = builder
                    .comment("クリティカルダメージの色 (0xRRGGBB)")
                    .defineInRange("criticalDamageColor", 0xFFCC00, 0, 0xFFFFFF);

            headshotDamageColor = builder
                    .comment("ヘッドショットダメージの色 (0xRRGGBB)")
                    .defineInRange("headshotDamageColor", 0xFF3333, 0, 0xFFFFFF);

            taczDamageColor = builder
                    .comment("TaCZ銃撃ダメージの色 (0xRRGGBB)")
                    .defineInRange("taczDamageColor", 0xFFFFFF, 0, 0xFFFFFF);

            builder.pop();
        }

        // 安全なゲッター（Config未ロード時でも安全にデフォルト値を返す）
        public boolean isEnabled() {
            try { return enabled != null && enabled.get(); } catch (Exception e) { return true; }
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

        public double getHudScale() {
            try { return hudScale != null ? hudScale.get() : 1.15D; } catch (Exception e) { return 1.15D; }
        }

        public double getScrollSpacing() {
            try { return scrollSpacing != null ? scrollSpacing.get() : 14.0D; } catch (Exception e) { return 14.0D; }
        }

        public double getCrosshairOffsetX() {
            try { return crosshairOffsetX != null ? crosshairOffsetX.get() : 18.0D; } catch (Exception e) { return 18.0D; }
        }

        public double getCrosshairOffsetY() {
            try { return crosshairOffsetY != null ? crosshairOffsetY.get() : -4.0D; } catch (Exception e) { return -4.0D; }
        }

        public boolean isShowHitCount() {
            try { return showHitCount != null && showHitCount.get(); } catch (Exception e) { return true; }
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

        public int getLifetime() {
            try { return lifetimeTicks != null ? lifetimeTicks.get() : 35; } catch (Exception e) { return 35; }
        }

        public double getRiseSpeed() {
            try { return riseSpeed != null ? riseSpeed.get() : 0.025D; } catch (Exception e) { return 0.025D; }
        }

        public boolean isXRay() {
            try { return enableXRay != null && enableXRay.get(); } catch (Exception e) { return true; }
        }

        public boolean isShowHeadshotIcon() {
            try { return showHeadshotIcon != null && showHeadshotIcon.get(); } catch (Exception e) { return true; }
        }

        public int getDecimalPlaces() {
            try { return decimalPlaces != null ? decimalPlaces.get() : 1; } catch (Exception e) { return 1; }
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

        public int getTaczColor() {
            try { return taczDamageColor != null ? taczDamageColor.get() : 0xFFFFFF; } catch (Exception e) { return 0xFFFFFF; }
        }
    }
}
