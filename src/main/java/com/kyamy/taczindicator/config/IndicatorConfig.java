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

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "taczindicator-client.toml");
    }

    public static class Client {
        // 全般設定
        public final ForgeConfigSpec.BooleanValue enabled;
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

            enableConstantSize = builder
                    .comment("距離に関わらず画面上で同じ大きさ（角度サイズ一定）で表示するかどうか")
                    .define("enableConstantSize", true);

            baseScale = builder
                    .comment("インジケータの基本スケール")
                    .defineInRange("baseScale", 0.025D, 0.001D, 0.5D);

            distanceScaleFactor = builder
                    .comment("距離に応じた拡大係数（一定サイズモードで使用）")
                    .defineInRange("distanceScaleFactor", 0.05D, 0.001D, 1.0D);

            lifetimeTicks = builder
                    .comment("インジケータが表示されてから消えるまでの時間（Tick単位: 20Ticks = 1秒）")
                    .defineInRange("lifetimeTicks", 30, 5, 200);

            riseSpeed = builder
                    .comment("インジケータの上昇速度")
                    .defineInRange("riseSpeed", 0.03D, 0.0D, 0.5D);

            enableXRay = builder
                    .comment("壁や遮蔽物の向こう側でもインジケータを透過表示するかどうか")
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
                    .defineInRange("criticalDamageColor", 0xFFFF55, 0, 0xFFFFFF);

            headshotDamageColor = builder
                    .comment("ヘッドショットダメージの色 (0xRRGGBB)")
                    .defineInRange("headshotDamageColor", 0xFF2222, 0, 0xFFFFFF);

            taczDamageColor = builder
                    .comment("TaCZ銃撃ダメージの色 (0xRRGGBB)")
                    .defineInRange("taczDamageColor", 0xFFA500, 0, 0xFFFFFF);

            builder.pop();
        }
    }
}
