package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.config.IndicatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * プレイヤー被ダメージ時の画面赤色効果（ダメージヴィネット・画面フラッシュ）レンダラー
 * 設定による完全なON/OFF、不透明度、持続時間、カラーカスタマイズを完備
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class DamageVignetteRenderer {

    private static int vignetteTicksRemaining = 0;
    private static int maxVignetteDuration = 15;
    private static float currentDamageIntensity = 1.0f;
    private static long lastRenderTime = 0;

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
     * クライアントTick更新
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (vignetteTicksRemaining > 0) {
                vignetteTicksRemaining--;
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
     * 画面四隅からの美しいグラデーションヴィネットおよび全体フラッシュを描画
     */
    public static void drawVignetteOverlay(GuiGraphics guiGraphics, int width, int height, float alpha, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        int alphaMax = Math.max(1, Math.min(255, (int) (alpha * 255.0f)));
        int alphaMin = 0;

        int edgeColor = (alphaMax << 24) | (r << 16) | (g << 8) | b;
        int transparentColor = (alphaMin << 24) | (r << 16) | (g << 8) | b;

        // 画面全体の極薄フラッシュ
        int flashAlpha = Math.max(1, (int) (alpha * 0.18f * 255.0f));
        int flashColor = (flashAlpha << 24) | (r << 16) | (g << 8) | b;
        guiGraphics.fill(0, 0, width, height, flashColor);

        // 上下左右の境界グラデーション
        int vertBorder = Math.max(20, height / 5);
        int horizBorder = Math.max(20, width / 5);

        // 上端グラデーション (上 -> 中央)
        guiGraphics.fillGradient(0, 0, width, vertBorder, edgeColor, transparentColor);

        // 下端グラデーション (中央 -> 下)
        guiGraphics.fillGradient(0, height - vertBorder, width, height, transparentColor, edgeColor);

        // 左端グラデーション (左 -> 中央)
        guiGraphics.fillGradient(0, 0, horizBorder, height, edgeColor, transparentColor);

        // 右端グラデーション (中央 -> 右)
        guiGraphics.fillGradient(width - horizBorder, 0, width, height, transparentColor, edgeColor);
    }

    /**
     * 設定GUI用のプレビュー描画
     */
    public static void renderPreview(GuiGraphics guiGraphics, int width, int height, double opacity, int rgb) {
        if (opacity <= 0.01) return;
        drawVignetteOverlay(guiGraphics, width, height, (float) opacity, rgb);
    }

    public static void reset() {
        vignetteTicksRemaining = 0;
    }
}
