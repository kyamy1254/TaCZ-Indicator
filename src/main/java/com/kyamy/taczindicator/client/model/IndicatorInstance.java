package com.kyamy.taczindicator.client.model;

import com.kyamy.taczindicator.config.IndicatorConfig;

import java.util.Locale;
import java.util.Random;

/**
 * 個別のダメージインジケータ表示インスタンス
 * 連続ダメージの加算（スタック）、上方スクロール、防具貫通、アニメーションスタイル（浮遊/拡散/重力/静止）、およびポップアニメーションを管理
 */
public class IndicatorInstance {
    private static final Random RANDOM = new Random();

    private final int entityId;
    private double originX;
    private double originY;
    private double originZ;
    private float damage;
    private int hitCount;
    private boolean isHeadshot;
    private boolean isCritical;
    private boolean isTaCZ;
    private boolean isArmorPiercing;
    private boolean hitArmor;
    private String formattedText;
    private int color;

    private int ageTicks;
    private final int maxLifetime;

    // アニメーションスタイルと物理ベクトル
    private final IndicatorConfig.AnimationStyle animationStyle;
    private double motionX;
    private double motionY;
    private double motionZ;

    private double animOffsetX;
    private double animOffsetY;
    private double animOffsetZ;
    private double prevAnimOffsetX;
    private double prevAnimOffsetY;
    private double prevAnimOffsetZ;

    // HUD用スクロールアニメーション状態 (SCROLL_UP用)
    private double currentScrollY;
    private double prevScrollY;
    private double targetScrollY;

    // ポップアップ拡大バウンス状態
    private float popScale;
    private float prevPopScale;

    public IndicatorInstance(int entityId, double x, double y, double z, float damage,
                             boolean isHeadshot, boolean isCritical, boolean isTaCZ,
                             boolean isArmorPiercing, boolean hitArmor) {
        this.entityId = entityId;
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.damage = damage;
        this.hitCount = 1;
        this.isHeadshot = isHeadshot;
        this.isCritical = isCritical;
        this.isTaCZ = isTaCZ;
        this.isArmorPiercing = isArmorPiercing;
        this.hitArmor = hitArmor;

        this.ageTicks = 0;
        this.maxLifetime = IndicatorConfig.getLifetime();

        this.currentScrollY = 0.0;
        this.prevScrollY = 0.0;
        this.targetScrollY = 0.0;

        this.popScale = 1.35f;
        this.prevPopScale = 1.35f;

        this.animationStyle = IndicatorConfig.getAnimationStyle();
        this.animOffsetX = 0.0;
        this.animOffsetY = 0.0;
        this.animOffsetZ = 0.0;
        this.prevAnimOffsetX = 0.0;
        this.prevAnimOffsetY = 0.0;
        this.prevAnimOffsetZ = 0.0;

        initAnimationPhysics();
        updateFormattedTextAndColor();
    }

    public IndicatorInstance(int entityId, double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        this(entityId, x, y, z, damage, isHeadshot, isCritical, isTaCZ, false, false);
    }

    public static final String ICON_SHIELD = "\uE001";
    public static final String ICON_SHIELD_PENETRATION = "\uE002";

    private void initAnimationPhysics() {
        this.motionX = 0.0;
        this.motionZ = 0.0;
        switch (this.animationStyle) {
            case SUBTLE_POP -> {
                // 1〜2ピクセル程度だけわずかに上に浮き上がるマイクロアニメーション
                this.motionY = 0.010;
                this.popScale = 1.20f;
                this.prevPopScale = 1.20f;
            }
            case STATIC_FADE -> {
                // スケール変化も移動もなし（ピタッと静止してフェードアウト）
                this.motionY = 0.0;
                this.popScale = 1.0f;
                this.prevPopScale = 1.0f;
            }
            case STATIC_POP -> {
                // その場でポンッと拡大（移動なし）
                this.motionY = 0.0;
                this.popScale = 1.35f;
                this.prevPopScale = 1.35f;
            }
        }
    }

    /**
     * テキストおよびカラーの再計算
     */
    public void updateFormattedTextAndColor() {
        int decimalPlaces = IndicatorConfig.getDecimalPlaces();
        String formatString = "%." + decimalPlaces + "f";
        String numText = String.format(Locale.ROOT, formatString, this.damage);

        if (this.isHeadshot && IndicatorConfig.isShowHeadshotIcon()) {
            numText = "§c☠ §l" + numText + "§r";
        } else if (this.isCritical && IndicatorConfig.isShowCriticalIcon()) {
            numText = "§6★ §l" + numText + "§r";
        } else if (this.isCritical) {
            numText = "§6§l" + numText + "§r";
        }

        if (IndicatorConfig.isShowHitCount() && this.hitCount > 1) {
            numText += " §7(x" + this.hitCount + ")";
        }

        // 盾貫通（AP: 白）または通常の防具軽減（盾: 水色）アイコンを末尾に表示
        if (this.isArmorPiercing && IndicatorConfig.isShowArmorPiercingIcon()) {
            numText += " §f" + ICON_SHIELD_PENETRATION;
        } else if (this.hitArmor && IndicatorConfig.isShowArmorDamageIcon()) {
            numText += " §b" + ICON_SHIELD;
        }

        this.formattedText = numText;

        // カラーの決定 (色分けは 通常 / クリティカル / ヘッドショット)
        if (this.isHeadshot) {
            this.color = IndicatorConfig.getHeadshotColor();
        } else if (this.isCritical) {
            this.color = IndicatorConfig.getCriticalColor();
        } else {
            this.color = IndicatorConfig.getNormalColor();
        }
    }

    /**
     * 同一ターゲットへの連続ダメージを加算（最新ヒットの状態・アイコン・デザインに合わせて更新）
     */
    public void accumulateDamage(float additionalDamage, boolean headshot, boolean critical, boolean tacz, boolean ap, boolean armor) {
        this.damage += additionalDamage;
        this.hitCount++;
        this.isHeadshot = headshot;
        this.isCritical = critical;
        this.isTaCZ = tacz;
        this.isArmorPiercing = ap;
        this.hitArmor = armor;

        // タイマーのリセットとポップアニメーションの再トリガー
        this.ageTicks = 0;
        if (this.animationStyle == IndicatorConfig.AnimationStyle.STATIC_POP) {
            this.popScale = 1.45f;
            this.prevPopScale = 1.45f;
        } else if (this.animationStyle == IndicatorConfig.AnimationStyle.SUBTLE_POP) {
            this.popScale = 1.25f;
            this.prevPopScale = 1.25f;
            this.motionY = 0.008;
        } else {
            this.popScale = 1.0f;
            this.prevPopScale = 1.0f;
        }

        updateFormattedTextAndColor();
    }

    public void accumulateDamage(float additionalDamage, boolean headshot, boolean critical, boolean tacz) {
        accumulateDamage(additionalDamage, headshot, critical, tacz, false, false);
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
        this.originX = newX;
        this.originY = newY;
        this.originZ = newZ;
    }

    /**
     * 毎クライアントTickごとの更新
     */
    public void tick() {
        this.prevAnimOffsetX = this.animOffsetX;
        this.prevAnimOffsetY = this.animOffsetY;
        this.prevAnimOffsetZ = this.animOffsetZ;

        switch (this.animationStyle) {
            case SUBTLE_POP -> {
                this.animOffsetY += this.motionY;
                this.motionY = Math.max(0.0, this.motionY * 0.70 - 0.001); // 素早く減速してピタッと静止
            }
            case STATIC_POP, STATIC_FADE -> {
                // 位置の移動なし
            }
        }

        this.prevScrollY = this.currentScrollY;
        this.currentScrollY += (this.targetScrollY - this.currentScrollY) * 0.4;

        this.prevPopScale = this.popScale;
        if (this.animationStyle != IndicatorConfig.AnimationStyle.STATIC_FADE) {
            this.popScale += (1.0f - this.popScale) * 0.25f;
        } else {
            this.popScale = 1.0f;
        }

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
     * 部分Tickで補間したX座標を取得 (3D用)
     */
    public double getInterpolatedX(float partialTicks) {
        return this.originX + (this.prevAnimOffsetX + (this.animOffsetX - this.prevAnimOffsetX) * partialTicks);
    }

    /**
     * 部分Tickで補間したY座標を取得 (3D用)
     */
    public double getInterpolatedY(float partialTicks) {
        return this.originY + (this.prevAnimOffsetY + (this.animOffsetY - this.prevAnimOffsetY) * partialTicks);
    }

    /**
     * 部分Tickで補間したZ座標を取得 (3D用)
     */
    public double getInterpolatedZ(float partialTicks) {
        return this.originZ + (this.prevAnimOffsetZ + (this.animOffsetZ - this.prevAnimOffsetZ) * partialTicks);
    }

    /**
     * 2D HUD用の補間アニメーションオフセットX (ピクセル単位)
     */
    public double getInterpolatedAnimOffsetX(float partialTicks) {
        return (this.prevAnimOffsetX + (this.animOffsetX - this.prevAnimOffsetX) * partialTicks) * 20.0;
    }

    /**
     * 2D HUD用の補間アニメーションオフセットY (ピクセル単位)
     */
    public double getInterpolatedAnimOffsetY(float partialTicks) {
        return -(this.prevAnimOffsetY + (this.animOffsetY - this.prevAnimOffsetY) * partialTicks) * 20.0;
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
    public double getX() { return originX; }
    public double getY() { return originY; }
    public double getZ() { return originZ; }
    public float getDamage() { return damage; }
    public int getHitCount() { return hitCount; }
    public boolean isHeadshot() { return isHeadshot; }
    public boolean isCritical() { return isCritical; }
    public boolean isTaCZ() { return isTaCZ; }
    public boolean isArmorPiercing() { return isArmorPiercing; }
    public boolean isHitArmor() { return hitArmor; }
    public String getFormattedText() { return formattedText; }
    public int getColor() { return color; }
    public int getAgeTicks() { return ageTicks; }
    public IndicatorConfig.AnimationStyle getAnimationStyle() { return animationStyle; }
}
