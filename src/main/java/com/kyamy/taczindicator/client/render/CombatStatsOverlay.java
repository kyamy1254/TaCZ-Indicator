package com.kyamy.taczindicator.client.render;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.client.stats.CombatStatsManager;
import com.kyamy.taczindicator.config.IndicatorConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * 戦闘統計（DPS・総ダメージ・命中数・HS率・キル数）のスタイリッシュなHUDオーバーレイ描画
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class CombatStatsOverlay {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            return;
        }

        if (!IndicatorConfig.isEnabled() || IndicatorConfig.getCombatStatsMode() == IndicatorConfig.CombatStatsDisplayMode.OFF) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        CombatStatsManager stats = CombatStatsManager.getInstance();
        float alpha = stats.getDisplayAlpha();
        if (alpha <= 0.01f) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        renderStatsCard(guiGraphics, font, stats, alpha, screenWidth, screenHeight);
    }

    public static void renderStatsCard(GuiGraphics guiGraphics, Font font, CombatStatsManager stats, float alpha, int screenWidth, int screenHeight) {
        int cardWidth = 142;
        int cardHeight = 32;
        double scale = IndicatorConfig.getCombatStatsScale();

        int posX;
        int posY;

        IndicatorConfig.CombatStatsPosition pos = IndicatorConfig.getCombatStatsPosition();
        switch (pos) {
            case TOP_LEFT -> {
                posX = 8;
                posY = 8;
            }
            case BOTTOM_LEFT -> {
                posX = 8;
                posY = screenHeight - (int) (cardHeight * scale) - 8;
            }
            case BOTTOM_RIGHT -> {
                posX = screenWidth - (int) (cardWidth * scale) - 8;
                posY = screenHeight - (int) (cardHeight * scale) - 8;
            }
            case TOP_RIGHT -> {
                posX = screenWidth - (int) (cardWidth * scale) - 8;
                posY = 8;
            }
            default -> {
                posX = screenWidth - (int) (cardWidth * scale) - 8;
                posY = 8;
            }
        }

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(posX, posY, 0.0);
        poseStack.scale((float) scale, (float) scale, 1.0f);

        int alphaInt = Math.max(10, Math.min(255, (int) (alpha * 255.0f)));
        int bgAlpha = Math.max(8, Math.min(160, (int) (alpha * 160.0f)));

        int bgColor = (bgAlpha << 24) | 0x101520;
        int borderColor = (alphaInt << 24) | 0x00A0E9;

        // 半透明ダークカード背景
        guiGraphics.fill(0, 0, cardWidth, cardHeight, bgColor);
        guiGraphics.renderOutline(0, 0, cardWidth, cardHeight, borderColor);

        // タイトル
        String header = "§b§l[ COMBAT STATS ]";
        guiGraphics.drawString(font, header, 6, 4, (alphaInt << 24) | 0x00F0FF, false);

        // 1行目: DPS & Total Damage
        float dps = stats.getDPS();
        double totalDmg = stats.getTotalDamage();
        String line1 = String.format(Locale.ROOT, "§fDPS: §e%.1f §7| §fTotal: §a%,.0f", dps, totalDmg);
        guiGraphics.drawString(font, line1, 6, 14, (alphaInt << 24) | 0xFFFFFF, false);

        // 2行目: Hits & HS% & Kills
        int hits = stats.getTotalHits();
        float hsRate = stats.getHeadshotRate();
        int kills = stats.getTotalKills();
        String line2 = String.format(Locale.ROOT, "§fHits: §b%d §7(§c☠%.0f%%§7) §7| §fK: §c%d", hits, hsRate, kills);
        guiGraphics.drawString(font, line2, 6, 23, (alphaInt << 24) | 0xEEEEEE, false);

        poseStack.popPose();
    }

    /**
     * 設定画面用のDPSメータープレビューカード描画
     */
    public static void renderPreviewCard(GuiGraphics guiGraphics, IndicatorConfig.CombatStatsPosition pos, double scale, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int cardWidth = 142;
        int cardHeight = 32;

        int posX;
        int posY;
        switch (pos) {
            case TOP_LEFT -> {
                posX = 8;
                posY = 8;
            }
            case BOTTOM_LEFT -> {
                posX = 8;
                posY = screenHeight - (int) (cardHeight * scale) - 8;
            }
            case BOTTOM_RIGHT -> {
                posX = screenWidth - (int) (cardWidth * scale) - 8;
                posY = screenHeight - (int) (cardHeight * scale) - 8;
            }
            case TOP_RIGHT -> {
                posX = screenWidth - (int) (cardWidth * scale) - 8;
                posY = 8;
            }
            default -> {
                posX = screenWidth - (int) (cardWidth * scale) - 8;
                posY = 8;
            }
        }

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(posX, posY, 0.0);
        poseStack.scale((float) scale, (float) scale, 1.0f);

        int bgColor = 0xAA101520;
        int borderColor = 0xFF00A0E9;

        // 半透明ダークカード背景
        guiGraphics.fill(0, 0, cardWidth, cardHeight, bgColor);
        guiGraphics.renderOutline(0, 0, cardWidth, cardHeight, borderColor);

        // タイトル
        String header = "§b§l[ COMBAT STATS ]";
        guiGraphics.drawString(font, header, 6, 4, 0xFF00F0FF, false);

        // 1行目: DPS & Total Damage (サンプル)
        String line1 = "§fDPS: §e145.2 §7| §fTotal: §a3,450";
        guiGraphics.drawString(font, line1, 6, 14, 0xFFFFFFFF, false);

        // 2行目: Hits & HS% & Kills (サンプル)
        String line2 = "§fHits: §b28 §7(§c☠32%§7) §7| §fK: §c6";
        guiGraphics.drawString(font, line2, 6, 23, 0xFFEEEEEE, false);

        poseStack.popPose();
    }
}
