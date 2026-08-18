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
 * プレイヤー被ダメージ時および瀕死時(Low HP)の画面効果（ヴィネット）レンダラー
 * 専用の高品位白マスクテクスチャ、滑らかな生体呼吸・鼓動パルス、および鮮やかなカラーカスタマイズを完備
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class DamageVignetteRenderer {

    private static final ResourceLocation VIGNETTE_LOCATION = new ResourceLocation(TaCZIndicatorMod.MOD_ID, "textures/gui/vignette.png");

    private static int vignetteTicksRemaining = 0;
    private static int maxVignetteDuration = 15;
    private static float currentDamageIntensity = 1.0f;
    private static long lastRenderTime = 0;

    // 設定画面用のプレビューアニメーション管理
    private static int previewTicksRemaining = 0;
    private static int previewMaxDuration = 20;
    private static float previewOpacity = 0.5f;
    private static int previewColor = 0xFF0000;
    private static boolean isPreviewLowHp = false;
    private static double previewHeartbeatSpeed = 1.0;

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
     * 設定画面用: 被ダメ赤色フェードのプレビューをトリガー
     */
    public static void triggerPreview(double opacity, int color, int durationTicks) {
        previewMaxDuration = Math.max(5, durationTicks);
        previewTicksRemaining = previewMaxDuration;
        previewOpacity = (float) opacity;
        previewColor = color;
        isPreviewLowHp = false;
    }

    public static void triggerPreview(double opacity, int color) {
        triggerPreview(opacity, color, 20);
    }

    /**
     * 設定画面用: 瀕死時(Low HP)鼓動ヴィネットのプレビューをトリガー (約4秒間)
     */
    public static void triggerLowHpPreview(double opacity, int color, double heartbeatSpeed) {
        previewMaxDuration = 80;
        previewTicksRemaining = 80;
        previewOpacity = (float) opacity;
        previewColor = color;
        previewHeartbeatSpeed = heartbeatSpeed;
        isPreviewLowHp = true;
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

        float hitAlpha = 0.0f;
        float hitFlashAlpha = 0.0f;
        int hitColor = IndicatorConfig.getDamageVignetteColor();

        // 1. 被ダメージ時の一時的フェードアウト赤色効果
        if (IndicatorConfig.isDamageVignetteEnabled() && vignetteTicksRemaining > 0) {
            float progress = (vignetteTicksRemaining - partialTick) / (float) maxVignetteDuration;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            float baseOpacity = (float) IndicatorConfig.getDamageVignetteOpacity();
            hitAlpha = progress * progress * baseOpacity * currentDamageIntensity;
            // 瞬間的な極微細フラッシュ（中心視認性を損なわない最大0.06以下の極薄）
            hitFlashAlpha = hitAlpha * 0.08f;
        }

        // 2. 瀕死時 (Low HP) の持続的・滑らかな生体呼吸/鼓動赤色効果
        float lowHpAlpha = 0.0f;
        int lowHpColor = IndicatorConfig.getLowHpVignetteColor();

        if (IndicatorConfig.isLowHpVignetteEnabled() && mc.player.isAlive()) {
            float maxHp = mc.player.getMaxHealth();
            float currentHp = mc.player.getHealth();
            float hpRatio = (maxHp > 0.0f) ? currentHp / maxHp : 1.0f;
            float threshold = (float) IndicatorConfig.getLowHpThreshold();

            if (hpRatio <= threshold && threshold > 0.001f) {
                float danger = Math.max(0.0f, Math.min(1.0f, (threshold - hpRatio) / threshold));
                float baseLowHpOpacity = (float) IndicatorConfig.getLowHpVignetteOpacity();

                float pulse = 1.0f;
                if (IndicatorConfig.isLowHpHeartbeatEnabled()) {
                    pulse = calculateHeartbeatPulse(IndicatorConfig.getLowHpHeartbeatSpeed(), danger, System.currentTimeMillis());
                }

                // 危険度に応じた穏やかなアルファスケーリング (画面端のみ)
                lowHpAlpha = baseLowHpOpacity * (0.70f + 0.30f * danger) * pulse;
            }
        }

        float totalVignetteAlpha = Math.min(1.0f, hitAlpha + lowHpAlpha);
        if (totalVignetteAlpha <= 0.005f && hitFlashAlpha <= 0.005f) {
            return;
        }

        int finalColor = (hitAlpha > lowHpAlpha) ? hitColor : lowHpColor;
        // Low HP時は全画面フラッシュを行わず、被ダメ時のhitFlashAlphaのみを適用
        drawVignetteOverlay(guiGraphics, screenWidth, screenHeight, totalVignetteAlpha, finalColor, hitFlashAlpha);
    }

    /**
     * 自然で滑らかな生体呼吸・心拍パルス（0.35〜1.0）の計算
     * ストロボ点滅を防止し、滑らかなSmoothstepサイン波イージングで上品に脈動
     */
    public static float calculateHeartbeatPulse(double heartbeatSpeed, float danger, long currentTimeMillis) {
        // 自然な心拍速度 (基礎周波数: 1.1Hz ≈ 66bpm, 危険時: 最大1.6Hz ≈ 96bpm)
        double speed = heartbeatSpeed * (1.0 + danger * 0.45);
        double timeSec = (currentTimeMillis % 1000000L) / 1000.0 * speed * 1.1;

        // 滑らかな正弦波 (0.0 〜 1.0)
        double sinVal = Math.sin(timeSec * Math.PI * 2.0);
        double normalized = (sinVal + 1.0) * 0.5;

        // Smoothstep イージング: t^2 * (3 - 2t)
        double smoothEased = normalized * normalized * (3.0 - 2.0 * normalized);

        // 最小下限 0.35（急激な明滅を防ぐ穏やかな下限）から 1.0 へ滑らかに脈動
        return 0.35f + 0.65f * (float) smoothEased;
    }

    /**
     * 高品位白マスクテクスチャを用いた美しい円形グラデーションヴィネットを描画
     */
    public static void drawVignetteOverlay(GuiGraphics guiGraphics, int width, int height, float vignetteAlpha, int rgb) {
        drawVignetteOverlay(guiGraphics, width, height, vignetteAlpha, rgb, 0.0f);
    }

    /**
     * 高品位白マスクテクスチャを用いた美しい円形グラデーションヴィネットおよび瞬間被ダメフラッシュを描画
     */
    public static void drawVignetteOverlay(GuiGraphics guiGraphics, int width, int height, float vignetteAlpha, int rgb, float flashAlpha) {
        if (vignetteAlpha <= 0.005f && flashAlpha <= 0.005f) {
            return;
        }

        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        // 1. 被ダメージ瞬間の一時的極薄フラッシュ（Low HP鼓動時は常に0%・視界クリア）
        if (flashAlpha > 0.005f) {
            int fAlpha = Math.max(0, Math.min(255, (int) (flashAlpha * 255.0f)));
            if (fAlpha > 0) {
                int flashColor = (fAlpha << 24) | (((int) (r * 255)) << 16) | (((int) (g * 255)) << 8) | ((int) (b * 255));
                guiGraphics.fill(0, 0, width, height, flashColor);
            }
        }

        // 2. 高品位白マスクテクスチャによる滑らかな赤色/カスタムカラーヴィネット描画 (画面端のみ)
        if (vignetteAlpha > 0.005f) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(r, g, b, Math.min(1.0f, vignetteAlpha));
            guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /**
     * 設定GUI用のプレビュー描画（パルスフェードアニメーション & 鼓動シミュレーション対応）
     */
    public static void renderPreview(GuiGraphics guiGraphics, int width, int height, double configuredOpacity, int rgb, float partialTick) {
        if (configuredOpacity <= 0.005 && previewTicksRemaining <= 0) return;

        float alpha;
        float flashAlpha = 0.0f;
        if (previewTicksRemaining > 0) {
            if (isPreviewLowHp) {
                // 瀕死時鼓動シミュレーション
                float pulse = calculateHeartbeatPulse(previewHeartbeatSpeed, 0.5f, System.currentTimeMillis());
                alpha = previewOpacity * pulse;
            } else {
                // 被ダメ単発フェードアウト
                float progress = (previewTicksRemaining - partialTick) / (float) previewMaxDuration;
                progress = Math.max(0.0f, Math.min(1.0f, progress));
                alpha = progress * progress * previewOpacity;
                flashAlpha = alpha * 0.08f;
            }
        } else {
            // アイドル時の淡い常時プレビュー
            alpha = (float) (configuredOpacity * 0.35);
        }

        drawVignetteOverlay(guiGraphics, width, height, alpha, rgb, flashAlpha);
    }

    public static void reset() {
        vignetteTicksRemaining = 0;
        previewTicksRemaining = 0;
    }
}
