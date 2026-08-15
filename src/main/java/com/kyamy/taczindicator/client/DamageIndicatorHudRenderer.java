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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 2D HUDレイヤー上でのダメージインジケータ描画レンダラー
 * 照準（クロスヘア）横のHUD表示および投影HUDモードをサポート
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class DamageIndicatorHudRenderer {

    private static long lastRenderedFrameTime = 0;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        renderIndicators(event.getGuiGraphics(), event.getPartialTick(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        renderIndicators(event.getGuiGraphics(), event.getPartialTick(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    private static void renderIndicators(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
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

        // 同一フレームでの多重描画防止
        long now = System.nanoTime() / 1_000_000L;
        if (now - lastRenderedFrameTime < 5) {
            return;
        }
        lastRenderedFrameTime = now;

        PoseStack poseStack = guiGraphics.pose();
        Font font = mc.font;

        double baseHudScale = IndicatorConfig.getHudScale();

        for (IndicatorInstance indicator : indicators) {
            double posX;
            double posY;
            int drawX;

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
                drawX = -font.width(indicator.getFormattedText()) / 2;
            } else {
                // HUD_CROSSHAIRモード (レティクルの横にダメージ数値を表示)
                posX = (screenWidth / 2.0) + IndicatorConfig.getCrosshairOffsetX();
                posY = (screenHeight / 2.0) + IndicatorConfig.getCrosshairOffsetY() - indicator.getInterpolatedScrollY(partialTick);
                drawX = 0; // クロスヘア右側から自然に配置
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
            int drawY = -font.lineHeight / 2;

            poseStack.pushPose();
            poseStack.translate(posX, posY, 0.0);
            poseStack.scale(dynamicScale, dynamicScale, 1.0f);

            // 影付きでテキストを描画
            guiGraphics.drawString(font, text, drawX, drawY, color, true);

            poseStack.popPose();
        }
    }
}
