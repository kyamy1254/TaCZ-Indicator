package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.client.model.IndicatorInstance;
import com.kyamy.taczindicator.client.model.KillAlertInstance;
import com.kyamy.taczindicator.client.util.ScreenProjectionUtil;
import com.kyamy.taczindicator.config.IndicatorConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2D HUDレイヤー上でのダメージインジケータおよびキル通知描画レンダラー
 * 複数モブ同時被弾時のスマート行分離スタックおよび衝突防止機能を完備
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class DamageIndicatorHudRenderer {

    private static long lastRenderedFrameTime = 0;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        renderHudElements(event.getGuiGraphics(), event.getPartialTick(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        renderHudElements(event.getGuiGraphics(), event.getPartialTick(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    private static void renderHudElements(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!IndicatorConfig.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        // 同一フレームでの多重描画防止
        long now = System.nanoTime() / 1_000_000L;
        if (now - lastRenderedFrameTime < 5) {
            return;
        }
        lastRenderedFrameTime = now;

        Font font = mc.font;

        // 1. ダメージインジケータの描画 (HUDモード時)
        renderDamageIndicators(guiGraphics, partialTick, screenWidth, screenHeight, font);

        // 2. キル確定通知の描画 (レティクル直下・アクションバーと非干渉)
        if (IndicatorConfig.isShowKillAlert()) {
            renderKillAlerts(guiGraphics, partialTick, screenWidth, screenHeight, font);
        }
    }

    private static void renderDamageIndicators(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight, Font font) {
        IndicatorConfig.RenderMode renderMode = IndicatorConfig.getRenderMode();
        if (renderMode == IndicatorConfig.RenderMode.WORLD_3D) {
            return;
        }

        List<IndicatorInstance> indicators = DamageIndicatorManager.getInstance().getActiveIndicators();
        if (indicators.isEmpty()) {
            return;
        }

        PoseStack poseStack = guiGraphics.pose();
        double baseHudScale = IndicatorConfig.getHudScale();

        // 複数ターゲット同時被弾時のスロット割り当て（HUD_CROSSHAIRモード用）
        Map<Integer, Integer> entitySlotMap = new LinkedHashMap<>();
        int currentSlot = 0;
        for (IndicatorInstance ind : indicators) {
            if (!entitySlotMap.containsKey(ind.getEntityId())) {
                entitySlotMap.put(ind.getEntityId(), currentSlot++);
            }
        }

        // 投影モード衝突防止用（HUD_PROJECTEDモード用）
        List<double[]> renderedScreenPositions = new ArrayList<>();

        for (IndicatorInstance indicator : indicators) {
            double posX;
            double posY;
            int drawX;

            if (renderMode == IndicatorConfig.RenderMode.HUD_PROJECTED) {
                ScreenProjectionUtil.ProjectionResult proj = ScreenProjectionUtil.projectToScreen(
                        indicator.getInterpolatedX(partialTick),
                        indicator.getInterpolatedY(partialTick),
                        indicator.getInterpolatedZ(partialTick)
                );

                if (!proj.isVisible()) {
                    continue;
                }

                posX = proj.getScreenX();
                posY = proj.getScreenY() - indicator.getInterpolatedScrollY(partialTick);

                // スクリーン空間での近接衝突回避（モブ同士が重なっている場合の上方ずらし）
                for (double[] existingPos : renderedScreenPositions) {
                    double distSq = Math.pow(posX - existingPos[0], 2) + Math.pow(posY - existingPos[1], 2);
                    if (distSq < 400.0) { // 20px以内
                        posY -= (font.lineHeight + 4) * baseHudScale;
                    }
                }
                renderedScreenPositions.add(new double[]{posX, posY});

                drawX = -font.width(indicator.getFormattedText()) / 2;
            } else {
                // HUD_CROSSHAIRモード (レティクル横・ターゲット別行分離スタック + アニメーションオフセット)
                int slot = entitySlotMap.getOrDefault(indicator.getEntityId(), 0);
                double slotVerticalOffset = slot * ((font.lineHeight + 3) * baseHudScale);

                posX = (screenWidth / 2.0) + IndicatorConfig.getCrosshairOffsetX() + indicator.getInterpolatedAnimOffsetX(partialTick);
                posY = (screenHeight / 2.0) + IndicatorConfig.getCrosshairOffsetY() + slotVerticalOffset - indicator.getInterpolatedScrollY(partialTick) + indicator.getInterpolatedAnimOffsetY(partialTick);
                drawX = 0;
            }

            // スケール計算（ポップバウンス）
            float dynamicScale = (float) (baseHudScale * indicator.getInterpolatedPopScale(partialTick));

            // アルファ値とカラーの計算
            float alpha = indicator.getAlpha(partialTick);
            int alphaInt = Math.max(8, Math.min(255, (int) (alpha * 255.0f)));
            int color = (indicator.getColor() & 0x00FFFFFF) | (alphaInt << 24);

            String text = indicator.getFormattedText();
            int drawY = -font.lineHeight / 2;

            poseStack.pushPose();
            poseStack.translate(posX, posY, 0.0);
            poseStack.scale(dynamicScale, dynamicScale, 1.0f);

            // 影付きでテキストを描画
            guiGraphics.drawString(font, text, drawX, drawY, color, true);

            poseStack.popPose();
        }
    }

    private static void renderKillAlerts(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight, Font font) {
        List<KillAlertInstance> alerts = DamageIndicatorManager.getInstance().getActiveKillAlerts();
        if (alerts.isEmpty()) {
            return;
        }

        PoseStack poseStack = guiGraphics.pose();
        double baseKillScale = IndicatorConfig.getKillAlertScale();
        double centerPosX = screenWidth / 2.0;
        double basePosY = (screenHeight / 2.0) + IndicatorConfig.getKillAlertOffsetY();

        for (int i = 0; i < alerts.size(); i++) {
            KillAlertInstance alert = alerts.get(i);
            String text = alert.getFormattedText();
            int textWidth = font.width(text);

            float alpha = alert.getAlpha(partialTick);
            int alphaInt = Math.max(8, Math.min(255, (int) (alpha * 255.0f)));
            int textColor = 0x00FFFFFF | (alphaInt << 24);

            float dynamicScale = (float) (baseKillScale * alert.getInterpolatedPopScale(partialTick));
            double posY = basePosY + (i * 12.0); // 複数キル通知時の縦並び

            poseStack.pushPose();
            poseStack.translate(centerPosX, posY, 0.0);
            poseStack.scale(dynamicScale, dynamicScale, 1.0f);

            // 画面中央揃えで影付き描画
            guiGraphics.drawString(font, text, -textWidth / 2, -font.lineHeight / 2, textColor, true);

            poseStack.popPose();
        }
    }
}
