package com.kyamy.taczindicator.client.model;

import com.kyamy.taczindicator.config.IndicatorConfig;
import net.minecraft.network.chat.Component;

/**
 * キル確定演出（Kill Alert）の個別インスタンス
 * アクションバーと被らないレティクル下部でのポップアップ・フェードアウト・マルチキルカウントを管理
 */
public class KillAlertInstance {
    private final String victimName;
    private int killCount;
    private int ageTicks;
    private final int maxLifetime;

    private float popScale;
    private float prevPopScale;
    private String formattedText;

    public KillAlertInstance(String victimName) {
        this.victimName = victimName;
        this.killCount = 1;
        this.ageTicks = 0;
        this.maxLifetime = 45; // 約2.25秒

        this.popScale = 1.40f;
        this.prevPopScale = 1.40f;

        updateFormattedText();
    }

    public void addMultiKill() {
        this.killCount++;
        this.ageTicks = 0; // 表示時間をリセット
        this.popScale = 1.50f;
        this.prevPopScale = 1.50f;
        updateFormattedText();
    }

    private void updateFormattedText() {
        if (this.killCount > 1) {
            this.formattedText = Component.translatable("taczindicator.kill.multi", this.victimName, this.killCount).getString();
        } else {
            this.formattedText = Component.translatable("taczindicator.kill.single", this.victimName).getString();
        }
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
}
