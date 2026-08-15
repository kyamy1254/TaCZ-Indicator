package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.client.model.IndicatorInstance;
import com.kyamy.taczindicator.client.util.ScreenProjectionUtil;
import com.kyamy.taczindicator.config.IndicatorConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 2D HUDレイヤー上でのダメージインジケータ描画レンダラー
 * 投影HUDモードおよび照準HUDモード、ポップ・スクロールアニメーションをサポート
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class DamageIndicatorHudRenderer {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!IndicatorConfig.isEnabled()) {
            return;
        }

        IndicatorConfig.RenderMode renderMode = IndicatorConfig.getRenderMode();
        if (renderMode == IndicatorConfig.RenderMode.WORLD_3D) {
            // WORLD_3Dモードの場合はDamageIndicatorRendererで描画
            return;
        }

        List<IndicatorInstance> indicators = DamageIndicatorManager.getInstance().getActiveIndicators();
        if (indicators.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        PoseStack poseStack = guiGraphics.pose();
        Font font = mc.font;
        float partialTick = event.getPartialTick();

        double baseHudScale = IndicatorConfig.getHudScale();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        for (IndicatorInstance indicator : indicators) {
            double posX;
            double posY;

            if (renderMode == IndicatorConfig.RenderMode.HUD_PROJECTED) {
                // 3Dワールド座標から2D画面座標へ投影
                ScreenProjectionUtil.ProjectionResult proj = ScreenProjectionUtil.projectToScreen(
                        indicator.getX(),
                        indicator.getInterpolatedY(partialTick),
                        indicator.getZ()
                );

                if (!proj.isVisible()) {
                    continue;
                }

                posX = proj.getScreenX();
                posY = proj.getScreenY() - indicator.getInterpolatedScrollY(partialTick);
            } else {
                // HUD_CROSSHAIRモード (レティクル周辺)
                posX = (screenWidth / 2.0) + IndicatorConfig.getCrosshairOffsetX();
                posY = (screenHeight / 2.0) + IndicatorConfig.getCrosshairOffsetY() - indicator.getInterpolatedScrollY(partialTick);
            }

            // スケール計算（ポップバウンス + ヘッドショット/クリティカル強調）
            float dynamicScale = (float) (baseHudScale * indicator.getInterpolatedPopScale(partialTick));
            if (indicator.isHeadshot()) {
                dynamicScale *= 1.25f;
            } else if (indicator.isCritical()) {
                dynamicScale *= 1.15f;
            }

            // アルファ値とカラーの計算
            float alpha = indicator.getAlpha(partialTick);
            int alphaInt = Math.max(8, Math.min(255, (int) (alpha * 255.0f)));
            int color = (indicator.getColor() & 0x00FFFFFF) | (alphaInt << 24);

            String text = indicator.getFormattedText();
            int textWidth = font.width(text);

            poseStack.pushPose();
            poseStack.translate(posX, posY, 0.0);
            poseStack.scale(dynamicScale, dynamicScale, 1.0f);

            int drawX = -textWidth / 2;
            int drawY = -font.lineHeight / 2;

            // 影付きでテキストを描画
            guiGraphics.drawString(font, text, drawX, drawY, color, true);

            poseStack.popPose();
        }
    }
}
