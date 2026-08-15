package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.client.model.IndicatorInstance;
import com.kyamy.taczindicator.config.IndicatorConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 3Dワールド空間における距離非依存ダメージインジケータ描画レンダラー
 */
@Mod.EventBusSubscriber(modid = "taczindicator", value = Dist.CLIENT)
public class DamageIndicatorRenderer {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && !mc.isPaused()) {
                ClientDamageHandler.incrementTick();
                DamageIndicatorManager.getInstance().tick();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // パーティクル描画直後のステージで描画
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        if (!IndicatorConfig.isEnabled() || IndicatorConfig.getRenderMode() != IndicatorConfig.RenderMode.WORLD_3D) {
            return;
        }

        List<IndicatorInstance> indicators = DamageIndicatorManager.getInstance().getActiveIndicators();
        if (indicators.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float partialTick = event.getPartialTick();

        boolean enableConstantSize = IndicatorConfig.CLIENT.isConstantSize();
        double baseScale = IndicatorConfig.CLIENT.getBaseScale();
        double distanceScaleFactor = IndicatorConfig.CLIENT.getDistanceScaleFactor();
        boolean enableXRay = IndicatorConfig.CLIENT.isXRay();

        for (IndicatorInstance indicator : indicators) {
            double posX = indicator.getX();
            double posY = indicator.getInterpolatedY(partialTick);
            double posZ = indicator.getZ();

            double relX = posX - cameraPos.x;
            double relY = posY - cameraPos.y;
            double relZ = posZ - cameraPos.z;

            // 距離計算
            double distance = Math.sqrt(relX * relX + relY * relY + relZ * relZ);
            if (distance < 0.1) {
                continue;
            }

            // 距離非依存スケール計算 (透視投影の距離減衰を相殺)
            double scale = baseScale;
            if (enableConstantSize) {
                // 距離に正比例させて拡大することで画面上の見かけのサイズを完全に一定に維持
                scale = baseScale * Math.max(1.0D, distance * distanceScaleFactor);
            }

            // ヘッドショット・クリティカル時は少し大きめに強調
            if (indicator.isHeadshot()) {
                scale *= 1.35D;
            } else if (indicator.isCritical()) {
                scale *= 1.15D;
            }

            poseStack.pushPose();
            poseStack.translate(relX, relY, relZ);

            // カメラの向きにビルボード回転
            poseStack.mulPose(camera.rotation());

            // MinecraftのUIレンダリングに合わせY軸反転
            poseStack.scale((float) -scale, (float) -scale, (float) scale);

            Matrix4f matrix4f = poseStack.last().pose();

            String text = indicator.getFormattedText();
            float textWidth = font.width(text);
            float xOffset = -textWidth / 2.0f;
            float yOffset = -font.lineHeight / 2.0f;

            // アルファ値（フェードアウト効果）
            int alpha = (int) (indicator.getAlpha(partialTick) * 255.0f);
            alpha = Math.max(8, Math.min(255, alpha));
            int color = indicator.getColor() & 0x00FFFFFF;
            int argb = (alpha << 24) | color;

            // X-Ray（壁越し透過表示）または通常表示モード
            Font.DisplayMode displayMode = enableXRay ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

            // テキスト描画 (影付き dropShadow = true で視認性を向上)
            font.drawInBatch(
                    text,
                    xOffset,
                    yOffset,
                    argb,
                    true,
                    matrix4f,
                    bufferSource,
                    displayMode,
                    0,
                    15728880
            );

            poseStack.popPose();
        }

        // バッファのフラッシュ描画
        bufferSource.endBatch();
    }
}
