package com.kyamy.taczindicator.client.model;

import com.kyamy.taczindicator.config.IndicatorConfig;

import java.util.Locale;

/**
 * 個別のダメージインジケータ表示インスタンス
 * 連続ダメージの加算（スタック）、上方スクロール、およびポップアニメーションを管理
 */
public class IndicatorInstance {
    private final int entityId;
    private double x;
    private double y;
    private double z;
    private float damage;
    private int hitCount;
    private boolean isHeadshot;
    private boolean isCritical;
    private boolean isTaCZ;
    private String formattedText;
    private int color;

    private int ageTicks;
    private final int maxLifetime;
    private double prevY;

    // HUD用スクロールアニメーション状態
    private double currentScrollY;
    private double prevScrollY;
    private double targetScrollY;

    // ポップアップ拡大バウンス状態
    private float popScale;
    private float prevPopScale;

    public IndicatorInstance(int entityId, double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.prevY = y;
        this.z = z;
        this.damage = damage;
        this.hitCount = 1;
        this.isHeadshot = isHeadshot;
        this.isCritical = isCritical;
        this.isTaCZ = isTaCZ;

        this.ageTicks = 0;
        this.maxLifetime = IndicatorConfig.getLifetime();

        this.currentScrollY = 0.0;
        this.prevScrollY = 0.0;
        this.targetScrollY = 0.0;

        this.popScale = 1.35f;
        this.prevPopScale = 1.35f;

        updateFormattedTextAndColor();
    }

    /**
     * テキストおよびカラーの再計算
     */
    public void updateFormattedTextAndColor() {
        int decimalPlaces = IndicatorConfig.getDecimalPlaces();
        String formatString = "%." + decimalPlaces + "f";
        String numText = String.format(Locale.ROOT, formatString, this.damage);

        if (this.isHeadshot && IndicatorConfig.isShowHeadshotIcon()) {
            numText = "§c[HS] §r" + numText;
        } else if (this.isCritical) {
            numText = "§e★ §r" + numText;
        }

        if (IndicatorConfig.isShowHitCount() && this.hitCount > 1) {
            numText += " §7x" + this.hitCount;
        }

        this.formattedText = numText;

        // カラーの決定 (優先度: Headshot > Critical > TaCZ > Normal)
        if (this.isHeadshot) {
            this.color = IndicatorConfig.getHeadshotColor();
        } else if (this.isCritical) {
            this.color = IndicatorConfig.getCriticalColor();
        } else if (this.isTaCZ) {
            this.color = IndicatorConfig.getTaczColor();
        } else {
            this.color = IndicatorConfig.getNormalColor();
        }
    }

    /**
     * 同一ターゲットへの連続ダメージを加算
     */
    public void accumulateDamage(float additionalDamage, boolean headshot, boolean critical, boolean tacz) {
        this.damage += additionalDamage;
        this.hitCount++;
        if (headshot) this.isHeadshot = true;
        if (critical) this.isCritical = true;
        if (tacz) this.isTaCZ = true;

        // タイマーのリセットとポップアニメーションの再トリガー
        this.ageTicks = 0;
        this.popScale = 1.45f;
        this.prevPopScale = 1.45f;

        updateFormattedTextAndColor();
    }

    /**
     * 上方への押し出しスクロールを設定
     */
    public void pushScrollUp(double amount) {
        this.targetScrollY += amount;
    }

    /**
     * ワールド座標の更新
     */
    public void updatePosition(double newX, double newY, double newZ) {
        this.x = newX;
        this.y = newY;
        this.prevY = newY;
        this.z = newZ;
    }

    /**
     * 毎クライアントTickごとの更新
     */
    public void tick() {
        this.prevY = this.y;
        this.y += IndicatorConfig.CLIENT.getRiseSpeed();

        this.prevScrollY = this.currentScrollY;
        this.currentScrollY += (this.targetScrollY - this.currentScrollY) * 0.4;

        this.prevPopScale = this.popScale;
        this.popScale += (1.0f - this.popScale) * 0.25f;

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
     */
    public float getAlpha(float partialTicks) {
        float progress = ((float) this.ageTicks + partialTicks) / (float) this.maxLifetime;
        if (progress > 0.7f) {
            return Math.max(0.0f, (1.0f - progress) / 0.3f);
        }
        return 1.0f;
    }

    /**
     * 部分Tickで補間したY座標を取得 (3D用)
     */
    public double getInterpolatedY(float partialTicks) {
        return this.prevY + (this.y - this.prevY) * partialTicks;
    }

    /**
     * 部分Tickで補間したスクロールYオフセットを取得 (HUD用)
     */
    public double getInterpolatedScrollY(float partialTicks) {
        return this.prevScrollY + (this.currentScrollY - this.prevScrollY) * partialTicks;
    }

    /**
     * 部分Tickで補間したポップスケールを取得
     */
    public float getInterpolatedPopScale(float partialTicks) {
        return this.prevPopScale + (this.popScale - this.prevPopScale) * partialTicks;
    }

    public int getEntityId() { return entityId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getDamage() { return damage; }
    public int getHitCount() { return hitCount; }
    public boolean isHeadshot() { return isHeadshot; }
    public boolean isCritical() { return isCritical; }
    public boolean isTaCZ() { return isTaCZ; }
    public String getFormattedText() { return formattedText; }
    public int getColor() { return color; }
    public int getAgeTicks() { return ageTicks; }
}
