package com.kyamy.taczindicator.client.sound;

import com.kyamy.taczindicator.config.IndicatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * ダメージヒット音およびキル確定音の再生ヘルパー
 * 設定による音量調整・ON/OFFトグル完備
 */
public class SoundHelper {

    /**
     * ダメージヒット時の効果音再生
     */
    public static void playHitSound(boolean isHeadshot, boolean isArmorPiercing, boolean hitArmor) {
        if (!IndicatorConfig.isHitSoundEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float volume = (float) IndicatorConfig.getHitSoundVolume();
        if (volume <= 0.001f) return;

        if (isHeadshot && IndicatorConfig.isHeadshotSoundEnabled()) {
            // ヘッドショット音 (高音キーン音)
            playSoundUI(SoundEvents.EXPERIENCE_ORB_PICKUP, volume, 2.0f);
            playSoundUI(SoundEvents.ARROW_HIT_PLAYER, volume * 0.8f, 1.8f);
        } else if (isArmorPiercing) {
            // 防具貫通音 (金属貫通音)
            playSoundUI(SoundEvents.SHIELD_BLOCK, volume * 0.7f, 1.6f);
            playSoundUI(SoundEvents.ARROW_HIT_PLAYER, volume, 1.2f);
        } else if (hitArmor) {
            // 通常防具被弾音
            playSoundUI(SoundEvents.ARMOR_EQUIP_GENERIC, volume * 0.8f, 1.5f);
        } else {
            // 通常ヒット音
            playSoundUI(SoundEvents.ARROW_HIT_PLAYER, volume, 1.2f);
        }
    }

    /**
     * 敵撃破時のキル確定音再生
     */
    public static void playKillSound() {
        if (!IndicatorConfig.isKillSoundEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float volume = (float) IndicatorConfig.getKillSoundVolume();
        if (volume <= 0.001f) return;

        // キル確定音 (爽快なチャイム音)
        playSoundUI(SoundEvents.EXPERIENCE_ORB_PICKUP, volume, 1.4f);
        playSoundUI(SoundEvents.PLAYER_LEVELUP, volume * 0.6f, 1.8f);
    }

    private static void playSoundUI(SoundEvent sound, float volume, float pitch) {
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        } catch (Throwable ignored) {}
    }
}
