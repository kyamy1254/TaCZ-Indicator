package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.config.IndicatorConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * プレイヤー被ダメージ時の画面赤色効果（ダメージヴィネット・画面フラッシュ）レンダラー
 * バニラVignetteテクスチャを用いた完全な円形・楕円スムーズフェード、設定プレビュー、およびカラーカスタマイズを完備
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class DamageVignetteRenderer {

    private static final ResourceLocation VIGNETTE_LOCATION = new ResourceLocation("textures/misc/vignette.png");

    private static int vignetteTicksRemaining = 0;
    private static int maxVignetteDuration = 15;
    private static float currentDamageIntensity = 1.0f;
    private static long lastRenderTime = 0;

    // 設定画面用のプレビューアニメーション管理
    private static int previewTicksRemaining = 0;
    private static int previewMaxDuration = 20;
    private static float previewOpacity = 0.5f;
    private static int previewColor = 0xFF0000;

    /**
     * プレイヤーが被ダメージした際にヴィネット効果を開始
     *
     * @param damageAmount 受けたダメージ量
     */
    public static void triggerVignette(float damageAmount) {
        if (!IndicatorConfig.isDamageVignetteEnabled()) {
            return;
        }

        maxVignetteDuration = Math.max(3, IndicatorConfig.getDamageVignetteDurationTicks());
        vignetteTicksRemaining = maxVignetteDuration;

        if (IndicatorConfig.isDamageVignetteScaleWithDamage()) {
            // ダメージ量に応じて強度をスケーリング (2.0ダメージ以上で基準、10.0ダメージで最大)
            currentDamageIntensity = Math.max(0.35f, Math.min(1.0f, damageAmount / 8.0f));
        } else {
            currentDamageIntensity = 1.0f;
        }
    }

    /**
     * 設定画面用: 指定した不透明度・色でプレビューフェードアニメーションをトリガー
     */
    public static void triggerPreview(double opacity, int color, int durationTicks) {
        previewMaxDuration = Math.max(5, durationTicks);
        previewTicksRemaining = previewMaxDuration;
        previewOpacity = (float) opacity;
        previewColor = color;
    }

    public static void triggerPreview(double opacity, int color) {
        triggerPreview(opacity, color, 20);
    }

    /**
     * クライアントTick更新
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (vignetteTicksRemaining > 0) {
                vignetteTicksRemaining--;
            }
            if (previewTicksRemaining > 0) {
                previewTicksRemaining--;
            }
        }
    }

    /**
     * HUD描画イベントでのヴィネット描画
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        renderVignette(event.getGuiGraphics(), event.getPartialTick(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        renderVignette(event.getGuiGraphics(), event.getPartialTick(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    private static void renderVignette(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!IndicatorConfig.isDamageVignetteEnabled() || vignetteTicksRemaining <= 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        // 同一フレームでの多重描画防止
        long now = System.nanoTime() / 1_000_000L;
        if (now - lastRenderTime < 5) {
            return;
        }
        lastRenderTime = now;

        float progress = (vignetteTicksRemaining - partialTick) / (float) maxVignetteDuration;
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        // 滑らかな減衰イージングカーブ (二乗イージング)
        float baseOpacity = (float) IndicatorConfig.getDamageVignetteOpacity();
        float alpha = progress * progress * baseOpacity * currentDamageIntensity;

        if (alpha <= 0.005f) {
            return;
        }

        int rgb = IndicatorConfig.getDamageVignetteColor();
        drawVignetteOverlay(guiGraphics, screenWidth, screenHeight, alpha, rgb);
    }

    /**
     * バニラVignetteテクスチャを用いた美しい円形グラデーションヴィネットおよび画面フラッシュを描画
     */
    public static void drawVignetteOverlay(GuiGraphics guiGraphics, int width, int height, float alpha, int rgb) {
        if (alpha <= 0.005f) {
            return;
        }

        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        // 画面全体の極薄フラッシュ
        int flashAlpha = Math.max(0, Math.min(255, (int) (alpha * 0.15f * 255.0f)));
        if (flashAlpha > 0) {
            int flashColor = (flashAlpha << 24) | (((int) (r * 255)) << 16) | (((int) (g * 255)) << 8) | ((int) (b * 255));
            guiGraphics.fill(0, 0, width, height, flashColor);
        }

        // バニラ準拠の完全な円形・楕円グラデーションヴィネット
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, Math.min(1.0f, alpha));
        guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * 設定GUI用のプレビュー描画（パルスフェードアニメーション対応）
     */
    public static void renderPreview(GuiGraphics guiGraphics, int width, int height, double configuredOpacity, int rgb, float partialTick) {
        if (configuredOpacity <= 0.005) return;

        float alpha;
        if (previewTicksRemaining > 0) {
            // トリガーされた動的フェードアニメーション
            float progress = (previewTicksRemaining - partialTick) / (float) previewMaxDuration;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            alpha = progress * progress * previewOpacity;
        } else {
            // アイドル時の淡い常時プレビュー（設定値の40%の薄さで穏やかに表示）
            alpha = (float) (configuredOpacity * 0.40);
        }

        drawVignetteOverlay(guiGraphics, width, height, alpha, rgb);
    }

    public static void reset() {
        vignetteTicksRemaining = 0;
        previewTicksRemaining = 0;
    }
}
