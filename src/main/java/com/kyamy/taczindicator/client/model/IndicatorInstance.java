package com.kyamy.taczindicator.client.model;

import com.kyamy.taczindicator.config.IndicatorConfig;

import java.util.Locale;

/**
 * 個別のダメージインジケータ表示インスタンス
 */
public class IndicatorInstance {
    private final double x;
    private double y;
    private final double z;
    private final float damage;
    private final boolean isHeadshot;
    private final boolean isCritical;
    private final boolean isTaCZ;
    private final String formattedText;
    private final int color;

    private int ageTicks;
    private final int maxLifetime;
    private double prevY;

    public IndicatorInstance(double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        this.x = x;
        this.y = y;
        this.prevY = y;
        this.z = z;
        this.damage = damage;
        this.isHeadshot = isHeadshot;
        this.isCritical = isCritical;
        this.isTaCZ = isTaCZ;

        this.ageTicks = 0;
        this.maxLifetime = IndicatorConfig.CLIENT.lifetimeTicks.get();

        // テキストのフォーマット
        int decimalPlaces = IndicatorConfig.CLIENT.decimalPlaces.get();
        String formatString = "%." + decimalPlaces + "f";
        String numText = String.format(Locale.ROOT, formatString, damage);

        if (isHeadshot && IndicatorConfig.CLIENT.showHeadshotIcon.get()) {
            this.formattedText = "§c[HS] §r" + numText;
        } else if (isCritical) {
            this.formattedText = "§e★ §r" + numText;
        } else {
            this.formattedText = numText;
        }

        // カラーの決定
        if (isHeadshot) {
            this.color = IndicatorConfig.CLIENT.headshotDamageColor.get();
        } else if (isCritical) {
            this.color = IndicatorConfig.CLIENT.criticalDamageColor.get();
        } else if (isTaCZ) {
            this.color = IndicatorConfig.CLIENT.taczDamageColor.get();
        } else {
            this.color = IndicatorConfig.CLIENT.normalDamageColor.get();
        }
    }

    /**
     * 毎クライアントTickごとの更新
     */
    public void tick() {
        this.prevY = this.y;
        this.y += IndicatorConfig.CLIENT.riseSpeed.get();
        this.ageTicks++;
    }

    /**
     * 生存中か判定
     */
    public boolean isExpired() {
        return this.ageTicks >= this.maxLifetime;
    }

    /**
     * アルファ値（透明度 0.0f - 1.0f）の計算
     * 後半にかけてフェードアウト
     */
    public float getAlpha(float partialTicks) {
        float progress = ((float) this.ageTicks + partialTicks) / (float) this.maxLifetime;
        if (progress > 0.7f) {
            // 残り30%でフェードアウト
            return Math.max(0.0f, (1.0f - progress) / 0.3f);
        }
        return 1.0f;
    }

    /**
     * 部分Tickで補間したY座標を取得
     */
    public double getInterpolatedY(float partialTicks) {
        return this.prevY + (this.y - this.prevY) * partialTicks;
    }

    public double getX() { return x; }
    public double getZ() { return z; }
    public float getDamage() { return damage; }
    public boolean isHeadshot() { return isHeadshot; }
    public boolean isCritical() { return isCritical; }
    public boolean isTaCZ() { return isTaCZ; }
    public String getFormattedText() { return formattedText; }
    public int getColor() { return color; }
}
