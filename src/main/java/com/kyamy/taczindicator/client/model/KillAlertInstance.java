package com.kyamy.taczindicator.client.model;

import net.minecraft.network.chat.Component;

/**
 * キル確定演出（Kill Alert）の個別インスタンス
 * 距離表示 [100m]、同種モブ連続キル時の置換更新、ポップバウンスを管理
 */
public class KillAlertInstance {
    private final String victimName;
    private int killCount;
    private int distanceMeters;
    private String weaponName;
    private int ageTicks;
    private final int maxLifetime;

    private float popScale;
    private float prevPopScale;
    private String formattedText;

    public KillAlertInstance(String victimName, int distanceMeters, String weaponName) {
        this.victimName = victimName;
        this.killCount = 1;
        this.distanceMeters = distanceMeters;
        this.weaponName = weaponName != null ? weaponName : "";
        this.ageTicks = 0;
        this.maxLifetime = 50; // 約2.5秒

        this.popScale = 1.40f;
        this.prevPopScale = 1.40f;

        updateFormattedText();
    }

    public KillAlertInstance(String victimName, int distanceMeters) {
        this(victimName, distanceMeters, "");
    }

    public KillAlertInstance(String victimName) {
        this(victimName, 0, "");
    }

    /**
     * 同種モブの連続キル発生時に新しい距離・武器・カウントで置換更新
     */
    public void updateKill(int newDistanceMeters, String newWeaponName) {
        this.killCount++;
        this.distanceMeters = newDistanceMeters;
        if (newWeaponName != null && !newWeaponName.isBlank()) {
            this.weaponName = newWeaponName;
        }
        this.ageTicks = 0; // 表示時間をリセット
        this.popScale = 1.45f;
        this.prevPopScale = 1.45f;
        updateFormattedText();
    }

    public void updateKill(int newDistanceMeters) {
        updateKill(newDistanceMeters, this.weaponName);
    }

    public void addMultiKill() {
        updateKill(this.distanceMeters, this.weaponName);
    }

    private void updateFormattedText() {
        String base;
        if (this.killCount > 1) {
            base = Component.translatable("taczindicator.kill.multi", this.victimName, this.killCount).getString();
        } else {
            base = Component.translatable("taczindicator.kill.single", this.victimName).getString();
        }

        StringBuilder sb = new StringBuilder(base);
        if (this.weaponName != null && !this.weaponName.isBlank()) {
            sb.append(" §6[").append(this.weaponName).append("]");
        }
        if (this.distanceMeters > 0) {
            sb.append(" §7[").append(this.distanceMeters).append("m]");
        }
        this.formattedText = sb.toString();
    }

    public void tick() {
        this.ageTicks++;
        this.prevPopScale = this.popScale;

        // ポップスケールの減衰 (1.4 -> 1.0)
        if (this.popScale > 1.0f) {
            this.popScale -= (this.popScale - 1.0f) * 0.35f;
            if (this.popScale < 1.005f) {
                this.popScale = 1.0f;
            }
        }
    }

    public float getInterpolatedPopScale(float partialTick) {
        return this.prevPopScale + (this.popScale - this.prevPopScale) * partialTick;
    }

    public float getAlpha(float partialTick) {
        float life = (float) this.ageTicks + partialTick;
        float fadeStart = this.maxLifetime * 0.65f;
        if (life < fadeStart) {
            return 1.0f;
        }
        float remaining = this.maxLifetime - life;
        return Math.max(0.0f, Math.min(1.0f, remaining / (this.maxLifetime - fadeStart)));
    }

    public boolean isExpired() {
        return this.ageTicks >= this.maxLifetime;
    }

    public String getFormattedText() {
        return this.formattedText;
    }

    public String getVictimName() {
        return this.victimName;
    }

    public int getKillCount() {
        return this.killCount;
    }

    public int getDistanceMeters() {
        return this.distanceMeters;
    }
}
