package com.kyamy.taczindicator.client.gui;

import com.kyamy.taczindicator.client.DamageVignetteRenderer;
import com.kyamy.taczindicator.config.IndicatorConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ゲーム内リアルタイムGUI設定画面
 * タブ（カテゴリ）分割による快適なUIナビゲーション、各種機能トグル、サウンド設定、およびドラッグプレビューを完備
 */
public class IndicatorConfigScreen extends Screen {

    public enum ConfigTab {
        GENERAL("taczindicator.gui.tab.general"),
        HUD_LAYOUT("taczindicator.gui.tab.hud"),
        VISUALS_3D("taczindicator.gui.tab.visuals"),
        SOUNDS("taczindicator.gui.tab.sounds");

        private final String translationKey;

        ConfigTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getTitle(boolean isSelected) {
            String prefix = isSelected ? "§a§l[" : "§7";
            String suffix = isSelected ? "§a§l]" : "§7";
            return Component.literal(prefix + " ").append(Component.translatable(this.translationKey)).append(suffix);
        }
    }

    private final Screen parentScreen;
    private ConfigTab currentTab = ConfigTab.GENERAL;

    // 一時編集用設定値 (全般・表示)
    private boolean tempEnabled;
    private boolean tempOnlyPlayerDamage;
    private boolean tempOnlyTaczDamage;
    private IndicatorConfig.RenderMode tempRenderMode;
    private IndicatorConfig.ConsecutiveMode tempConsecutiveMode;
    private boolean tempShowKillAlert;
    private boolean tempShowHitCount;

    // HUD・配置
    private double tempHudScale;
    private double tempOffsetX;
    private double tempOffsetY;
    private int tempMaxScrolledIndicators;

    // アイコン・3D・画面エフェクト
    private boolean tempShowHeadshotIcon;
    private boolean tempShowCriticalIcon;
    private boolean tempShowArmorPiercingIcon;
    private boolean tempShowArmorDamageIcon;
    private boolean tempEnableConstantSize;
    private boolean tempEnableXRay;
    private boolean tempDamageVignetteEnabled;
    private double tempDamageVignetteOpacity;

    // サウンド設定
    private boolean tempHitSoundEnabled;
    private double tempHitSoundVolume;
    private boolean tempHeadshotSoundEnabled;
    private boolean tempKillSoundEnabled;
    private double tempKillSoundVolume;

    private boolean isDragging = false;

    public IndicatorConfigScreen(Screen parentScreen) {
        super(Component.translatable("taczindicator.gui.title"));
        this.parentScreen = parentScreen;

        // 現在の設定値を読み込み
        this.tempEnabled = IndicatorConfig.isEnabled();
        this.tempOnlyPlayerDamage = IndicatorConfig.isOnlyPlayerDamage();
        this.tempOnlyTaczDamage = IndicatorConfig.isOnlyTaczDamage();
        this.tempRenderMode = IndicatorConfig.getRenderMode();
        this.tempConsecutiveMode = IndicatorConfig.getConsecutiveMode();
        this.tempShowKillAlert = IndicatorConfig.isShowKillAlert();
        this.tempShowHitCount = IndicatorConfig.isShowHitCount();

        this.tempHudScale = IndicatorConfig.getHudScale();
        this.tempOffsetX = IndicatorConfig.getCrosshairOffsetX();
        this.tempOffsetY = IndicatorConfig.getCrosshairOffsetY();
        this.tempMaxScrolledIndicators = IndicatorConfig.getMaxScrolledIndicators();

        this.tempShowHeadshotIcon = IndicatorConfig.isShowHeadshotIcon();
        this.tempShowCriticalIcon = IndicatorConfig.isShowCriticalIcon();
        this.tempShowArmorPiercingIcon = IndicatorConfig.isShowArmorPiercingIcon();
        this.tempShowArmorDamageIcon = IndicatorConfig.isShowArmorDamageIcon();
        this.tempEnableConstantSize = IndicatorConfig.isConstantSize();
        this.tempEnableXRay = IndicatorConfig.isXRay();
        this.tempDamageVignetteEnabled = IndicatorConfig.isDamageVignetteEnabled();
        this.tempDamageVignetteOpacity = IndicatorConfig.getDamageVignetteOpacity();

        this.tempHitSoundEnabled = IndicatorConfig.isHitSoundEnabled();
        this.tempHitSoundVolume = IndicatorConfig.getHitSoundVolume();
        this.tempHeadshotSoundEnabled = IndicatorConfig.isHeadshotSoundEnabled();
        this.tempKillSoundEnabled = IndicatorConfig.isKillSoundEnabled();
        this.tempKillSoundVolume = IndicatorConfig.getKillSoundVolume();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int centerX = this.width / 2;

        // 1. トップカテゴリタブバー
        int tabWidth = Math.min(115, (this.width - 20) / 4);
        int tabTotalWidth = tabWidth * 4;
        int tabStartX = centerX - tabTotalWidth / 2;
        int tabY = 22;

        for (int i = 0; i < ConfigTab.values().length; i++) {
            ConfigTab tab = ConfigTab.values()[i];
            this.addRenderableWidget(Button.builder(tab.getTitle(this.currentTab == tab), btn -> {
                this.currentTab = tab;
                this.init();
            }).bounds(tabStartX + i * tabWidth, tabY, tabWidth - 2, 18).build());
        }

        // 2. 現在のタブに応じたウィジェットの構築
        initCurrentTabWidgets();

        // 3. 最下部アクションボタン
        int bottomY = this.height - 24;
        int centerBtnWidth = 140;

        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.gui.save_and_close"),
                btn -> saveAndClose()
        ).bounds(centerX - centerBtnWidth - 10, bottomY, centerBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                btn -> this.onClose()
        ).bounds(centerX + 10, bottomY, centerBtnWidth, 20).build());
    }

    private void initCurrentTabWidgets() {
        int leftCol = 15;
        int rightCol = this.width - 165;
        int btnWidth = 150;
        int btnHeight = 18;
        int startY = 46;
        int gap = 21;

        switch (this.currentTab) {
            case GENERAL -> {
                // [全般・基本設定]
                // 1. MOD有効/無効
                this.addRenderableWidget(Button.builder(getEnabledText(), btn -> {
                    this.tempEnabled = !this.tempEnabled;
                    btn.setMessage(getEnabledText());
                }).bounds(leftCol, startY, btnWidth, btnHeight).build());

                // 2. プレイヤー与ダメ限定
                this.addRenderableWidget(Button.builder(getOnlyPlayerDamageText(), btn -> {
                    this.tempOnlyPlayerDamage = !this.tempOnlyPlayerDamage;
                    btn.setMessage(getOnlyPlayerDamageText());
                }).bounds(leftCol, startY + gap, btnWidth, btnHeight).build());

                // 3. TaCZ銃撃限定
                this.addRenderableWidget(Button.builder(getOnlyTaczDamageText(), btn -> {
                    this.tempOnlyTaczDamage = !this.tempOnlyTaczDamage;
                    btn.setMessage(getOnlyTaczDamageText());
                }).bounds(leftCol, startY + gap * 2, btnWidth, btnHeight).build());

                // 4. 描画モード
                this.addRenderableWidget(Button.builder(getRenderModeText(), btn -> {
                    IndicatorConfig.RenderMode[] modes = IndicatorConfig.RenderMode.values();
                    int nextIdx = (this.tempRenderMode.ordinal() + 1) % modes.length;
                    this.tempRenderMode = modes[nextIdx];
                    btn.setMessage(getRenderModeText());
                }).bounds(rightCol, startY, btnWidth, btnHeight).build());

                // 5. 連続ダメージ形式
                this.addRenderableWidget(Button.builder(getConsecutiveModeText(), btn -> {
                    IndicatorConfig.ConsecutiveMode[] modes = IndicatorConfig.ConsecutiveMode.values();
                    int nextIdx = (this.tempConsecutiveMode.ordinal() + 1) % modes.length;
                    this.tempConsecutiveMode = modes[nextIdx];
                    btn.setMessage(getConsecutiveModeText());
                }).bounds(rightCol, startY + gap, btnWidth, btnHeight).build());

                // 6. キル演出トグル
                this.addRenderableWidget(Button.builder(getKillAlertText(), btn -> {
                    this.tempShowKillAlert = !this.tempShowKillAlert;
                    btn.setMessage(getKillAlertText());
                }).bounds(rightCol, startY + gap * 2, btnWidth, btnHeight).build());
            }
            case HUD_LAYOUT -> {
                // [HUD・配置調整]
                // 1. ヒット数併記 (x3)
                this.addRenderableWidget(Button.builder(getShowHitCountText(), btn -> {
                    this.tempShowHitCount = !this.tempShowHitCount;
                    btn.setMessage(getShowHitCountText());
                }).bounds(leftCol, startY, btnWidth, btnHeight).build());

                // 2. 文字スケール縮小 / 拡大 [-] [+]
                this.addRenderableWidget(Button.builder(Component.literal("Scale -"), btn -> {
                    this.tempHudScale = Math.max(0.4, Math.round((this.tempHudScale - 0.1) * 10.0) / 10.0);
                }).bounds(leftCol, startY + gap, 72, btnHeight).build());

                this.addRenderableWidget(Button.builder(Component.literal("Scale +"), btn -> {
                    this.tempHudScale = Math.min(3.0, Math.round((this.tempHudScale + 0.1) * 10.0) / 10.0);
                }).bounds(leftCol + 78, startY + gap, 72, btnHeight).build());

                // 3. スクロール上限数 [-] [+]
                this.addRenderableWidget(Button.builder(Component.literal("Scroll -"), btn -> {
                    this.tempMaxScrolledIndicators = Math.max(1, this.tempMaxScrolledIndicators - 1);
                }).bounds(leftCol, startY + gap * 2, 72, btnHeight).build());

                this.addRenderableWidget(Button.builder(Component.literal("Scroll +"), btn -> {
                    this.tempMaxScrolledIndicators = Math.min(20, this.tempMaxScrolledIndicators + 1);
                }).bounds(leftCol + 78, startY + gap * 2, 72, btnHeight).build());

                // 4. 位置リセット
                this.addRenderableWidget(Button.builder(
                        Component.translatable("taczindicator.gui.reset_defaults"),
                        btn -> {
                            this.tempOffsetX = 18.0;
                            this.tempOffsetY = -4.0;
                            this.tempHudScale = 1.15;
                        }
                ).bounds(rightCol, startY, btnWidth, btnHeight).build());
            }
            case VISUALS_3D -> {
                // [アイコン・3D・画面エフェクト]
                // 1. ☠ ヘッドショットアイコン
                this.addRenderableWidget(Button.builder(getHeadshotIconText(), btn -> {
                    this.tempShowHeadshotIcon = !this.tempShowHeadshotIcon;
                    btn.setMessage(getHeadshotIconText());
                }).bounds(leftCol, startY, btnWidth, btnHeight).build());

                // 2. ★ クリティカルアイコン
                this.addRenderableWidget(Button.builder(getCriticalIconText(), btn -> {
                    this.tempShowCriticalIcon = !this.tempShowCriticalIcon;
                    btn.setMessage(getCriticalIconText());
                }).bounds(leftCol, startY + gap, btnWidth, btnHeight).build());

                // 3. 🗡 防具貫通(AP)アイコン
                this.addRenderableWidget(Button.builder(getArmorPiercingIconText(), btn -> {
                    this.tempShowArmorPiercingIcon = !this.tempShowArmorPiercingIcon;
                    btn.setMessage(getArmorPiercingIconText());
                }).bounds(leftCol, startY + gap * 2, btnWidth, btnHeight).build());

                // 4. 🛡️ 防具軽減アイコン
                this.addRenderableWidget(Button.builder(getArmorDamageIconText(), btn -> {
                    this.tempShowArmorDamageIcon = !this.tempShowArmorDamageIcon;
                    btn.setMessage(getArmorDamageIconText());
                }).bounds(leftCol, startY + gap * 3, btnWidth, btnHeight).build());

                // 5. 3D画面上同一サイズ
                this.addRenderableWidget(Button.builder(getConstantSizeText(), btn -> {
                    this.tempEnableConstantSize = !this.tempEnableConstantSize;
                    btn.setMessage(getConstantSizeText());
                }).bounds(rightCol, startY, btnWidth, btnHeight).build());

                // 6. 3D壁越し透過X-Ray
                this.addRenderableWidget(Button.builder(getXRayText(), btn -> {
                    this.tempEnableXRay = !this.tempEnableXRay;
                    btn.setMessage(getXRayText());
                }).bounds(rightCol, startY + gap, btnWidth, btnHeight).build());

                // 7. 被ダメ画面赤色効果トグル
                this.addRenderableWidget(Button.builder(getDamageVignetteText(), btn -> {
                    this.tempDamageVignetteEnabled = !this.tempDamageVignetteEnabled;
                    btn.setMessage(getDamageVignetteText());
                }).bounds(rightCol, startY + gap * 2, btnWidth, btnHeight).build());

                // 8. 赤色効果 濃さ [-] [+]
                this.addRenderableWidget(Button.builder(Component.literal("Red -"), btn -> {
                    this.tempDamageVignetteOpacity = Math.max(0.0, Math.round((this.tempDamageVignetteOpacity - 0.05) * 100.0) / 100.0);
                }).bounds(rightCol, startY + gap * 3, 72, btnHeight).build());

                this.addRenderableWidget(Button.builder(Component.literal("Red +"), btn -> {
                    this.tempDamageVignetteOpacity = Math.min(1.0, Math.round((this.tempDamageVignetteOpacity + 0.05) * 100.0) / 100.0);
                }).bounds(rightCol + 78, startY + gap * 3, 72, btnHeight).build());
            }
            case SOUNDS -> {
                // [サウンド設定]
                // 1. ヒット効果音
                this.addRenderableWidget(Button.builder(getHitSoundText(), btn -> {
                    this.tempHitSoundEnabled = !this.tempHitSoundEnabled;
                    btn.setMessage(getHitSoundText());
                }).bounds(leftCol, startY, btnWidth, btnHeight).build());

                // 2. ヒット音量 [-] [+]
                this.addRenderableWidget(Button.builder(Component.literal("HitVol -"), btn -> {
                    this.tempHitSoundVolume = Math.max(0.0, Math.round((this.tempHitSoundVolume - 0.1) * 10.0) / 10.0);
                }).bounds(leftCol, startY + gap, 72, btnHeight).build());

                this.addRenderableWidget(Button.builder(Component.literal("HitVol +"), btn -> {
                    this.tempHitSoundVolume = Math.min(1.0, Math.round((this.tempHitSoundVolume + 0.1) * 10.0) / 10.0);
                }).bounds(leftCol + 78, startY + gap, 72, btnHeight).build());

                // 3. ヘッドショット高音チャイム
                this.addRenderableWidget(Button.builder(getHeadshotSoundText(), btn -> {
                    this.tempHeadshotSoundEnabled = !this.tempHeadshotSoundEnabled;
                    btn.setMessage(getHeadshotSoundText());
                }).bounds(leftCol, startY + gap * 2, btnWidth, btnHeight).build());

                // 4. キル確定音
                this.addRenderableWidget(Button.builder(getKillSoundText(), btn -> {
                    this.tempKillSoundEnabled = !this.tempKillSoundEnabled;
                    btn.setMessage(getKillSoundText());
                }).bounds(rightCol, startY, btnWidth, btnHeight).build());

                // 5. キル音量 [-] [+]
                this.addRenderableWidget(Button.builder(Component.literal("KillVol -"), btn -> {
                    this.tempKillSoundVolume = Math.max(0.0, Math.round((this.tempKillSoundVolume - 0.1) * 10.0) / 10.0);
                }).bounds(rightCol, startY + gap, 72, btnHeight).build());

                this.addRenderableWidget(Button.builder(Component.literal("KillVol +"), btn -> {
                    this.tempKillSoundVolume = Math.min(1.0, Math.round((this.tempKillSoundVolume + 0.1) * 10.0) / 10.0);
                }).bounds(rightCol + 78, startY + gap, 72, btnHeight).build());
            }
        }
    }

    private Component getEnabledText() {
        return Component.translatable("taczindicator.gui.enabled", this.tempEnabled ? "§aON" : "§cOFF");
    }
    private Component getOnlyPlayerDamageText() {
        return Component.translatable("taczindicator.gui.only_player_damage", this.tempOnlyPlayerDamage ? "§aON" : "§cOFF");
    }
    private Component getOnlyTaczDamageText() {
        return Component.translatable("taczindicator.gui.only_tacz_damage", this.tempOnlyTaczDamage ? "§aON" : "§cOFF");
    }
    private Component getRenderModeText() {
        return Component.translatable("taczindicator.gui.render_mode", "§b" + this.tempRenderMode.name());
    }
    private Component getConsecutiveModeText() {
        return Component.translatable("taczindicator.gui.consecutive_mode", "§b" + this.tempConsecutiveMode.name());
    }
    private Component getKillAlertText() {
        return Component.translatable("taczindicator.gui.show_kill_alert", this.tempShowKillAlert ? "§aON" : "§cOFF");
    }
    private Component getShowHitCountText() {
        return Component.translatable("taczindicator.gui.show_hit_count", this.tempShowHitCount ? "§aON" : "§cOFF");
    }
    private Component getDamageVignetteText() {
        return Component.translatable("taczindicator.gui.damage_vignette", this.tempDamageVignetteEnabled ? "§aON" : "§cOFF");
    }
    private Component getHitSoundText() {
        return Component.translatable("taczindicator.gui.hit_sound", this.tempHitSoundEnabled ? "§aON" : "§cOFF");
    }
    private Component getHeadshotSoundText() {
        return Component.translatable("taczindicator.gui.headshot_sound", this.tempHeadshotSoundEnabled ? "§aON" : "§cOFF");
    }
    private Component getKillSoundText() {
        return Component.translatable("taczindicator.gui.kill_sound", this.tempKillSoundEnabled ? "§aON" : "§cOFF");
    }
    private Component getHeadshotIconText() {
        return Component.translatable("taczindicator.gui.show_headshot_icon", this.tempShowHeadshotIcon ? "§aON" : "§cOFF");
    }
    private Component getCriticalIconText() {
        return Component.translatable("taczindicator.gui.show_critical_icon", this.tempShowCriticalIcon ? "§aON" : "§cOFF");
    }
    private Component getArmorPiercingIconText() {
        return Component.translatable("taczindicator.gui.show_armor_piercing_icon", this.tempShowArmorPiercingIcon ? "§aON" : "§cOFF");
    }
    private Component getArmorDamageIconText() {
        return Component.translatable("taczindicator.gui.show_armor_damage_icon", this.tempShowArmorDamageIcon ? "§aON" : "§cOFF");
    }
    private Component getConstantSizeText() {
        return Component.translatable("taczindicator.gui.constant_size", this.tempEnableConstantSize ? "§aON" : "§cOFF");
    }
    private Component getXRayText() {
        return Component.translatable("taczindicator.gui.xray", this.tempEnableXRay ? "§aON" : "§cOFF");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // 被ダメージ赤色効果のプレビュー描画 (有効時)
        if (this.tempDamageVignetteEnabled && this.tempDamageVignetteOpacity > 0.01) {
            DamageVignetteRenderer.renderPreview(guiGraphics, this.width, this.height, this.tempDamageVignetteOpacity * 0.7, 0xFF0000);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // タイトル
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 6, 0xFFFFFF);

        // 中央クロスヘア
        guiGraphics.drawString(this.font, "+", centerX - this.font.width("+") / 2, centerY - this.font.lineHeight / 2, 0xFFFFFF, false);

        // プレビューサンプル描画（リアルタイム設定反映）
        double sampleX = centerX + this.tempOffsetX;
        double sampleY = centerY + this.tempOffsetY;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(sampleX, sampleY, 0.0);
        guiGraphics.pose().scale((float) this.tempHudScale, (float) this.tempHudScale, 1.0f);

        // サンプル1（AP弾）
        String sample1 = "45.0";
        if (this.tempShowHitCount) sample1 += " §7(x2)";
        if (this.tempShowArmorPiercingIcon) {
            sample1 += " §b\uE002";
        } else if (this.tempShowArmorDamageIcon) {
            sample1 += " §f\uE001";
        }

        // サンプル2（複数モブ被弾時の2行目スタック）
        String sample2 = "120.0";
        if (this.tempShowHeadshotIcon) sample2 = "§c☠ §l" + sample2 + "§r";

        int drawX = (this.tempRenderMode == IndicatorConfig.RenderMode.HUD_PROJECTED) ? -this.font.width(sample1) / 2 : 0;
        int drawX2 = (this.tempRenderMode == IndicatorConfig.RenderMode.HUD_PROJECTED) ? -this.font.width(sample2) / 2 : 0;

        guiGraphics.drawString(this.font, sample1, drawX, -this.font.lineHeight / 2, 0xFF33CCFF, true);
        guiGraphics.drawString(this.font, sample2, drawX2, -this.font.lineHeight / 2 + 12, 0xFFFF3333, true);

        // ドラッグ枠線
        int maxW = Math.max(this.font.width(sample1), this.font.width(sample2));
        int boxX = (this.tempRenderMode == IndicatorConfig.RenderMode.HUD_PROJECTED) ? -maxW / 2 - 2 : -2;
        guiGraphics.renderOutline(boxX, -this.font.lineHeight / 2 - 2, maxW + 4, 24, isDragging ? 0xFF55FF55 : 0x88FFFFFF);

        guiGraphics.pose().popPose();

        // キル通知サンプル (距離表示 [100m] 付き)
        if (this.tempShowKillAlert) {
            String killSample = Component.translatable("taczindicator.kill.single", "Zombie").getString() + " §7[100m]";
            int killW = this.font.width(killSample);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY + 30, 0.0);
            guiGraphics.drawString(this.font, killSample, -killW / 2, -this.font.lineHeight / 2, 0xFFFFFFFF, true);
            guiGraphics.pose().popPose();
        }

        // 下部情報ステータス行
        String infoStr;
        if (this.currentTab == ConfigTab.SOUNDS) {
            infoStr = String.format(java.util.Locale.ROOT, "Hit Sound: %s (%.1f) | Kill Sound: %s (%.1f)",
                    this.tempHitSoundEnabled ? "ON" : "OFF", this.tempHitSoundVolume,
                    this.tempKillSoundEnabled ? "ON" : "OFF", this.tempKillSoundVolume);
        } else if (this.currentTab == ConfigTab.HUD_LAYOUT) {
            infoStr = String.format(java.util.Locale.ROOT, "Scale: %.1fx | Pos: (%.0f, %.0f) | Max Scrolled: %d",
                    this.tempHudScale, this.tempOffsetX, this.tempOffsetY, this.tempMaxScrolledIndicators);
        } else if (this.currentTab == ConfigTab.VISUALS_3D) {
            infoStr = String.format(java.util.Locale.ROOT, "Red Vignette: %s (%.2f) | 3D Equal: %s",
                    this.tempDamageVignetteEnabled ? "ON" : "OFF", this.tempDamageVignetteOpacity,
                    this.tempEnableConstantSize ? "ON" : "OFF");
        } else {
            infoStr = String.format(java.util.Locale.ROOT, "Mode: %s | Combo: %s | Only Player: %s",
                    this.tempRenderMode.name(), this.tempConsecutiveMode.name(), this.tempOnlyPlayerDamage ? "ON" : "OFF");
        }
        guiGraphics.drawCenteredString(this.font, infoStr, centerX, this.height - 38, 0xDDDDDD);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        double sampleX = centerX + this.tempOffsetX;
        double sampleY = centerY + this.tempOffsetY;

        if (Math.abs(mouseX - sampleX) < 45 && Math.abs(mouseY - sampleY) < 25) {
            this.isDragging = true;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            this.tempOffsetX = Math.max(-200.0, Math.min(200.0, mouseX - centerX));
            this.tempOffsetY = Math.max(-150.0, Math.min(150.0, mouseY - centerY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void saveAndClose() {
        IndicatorConfig.CLIENT.enabled.set(this.tempEnabled);
        IndicatorConfig.CLIENT.onlyPlayerDamage.set(this.tempOnlyPlayerDamage);
        IndicatorConfig.CLIENT.onlyTaczDamage.set(this.tempOnlyTaczDamage);
        IndicatorConfig.CLIENT.renderMode.set(this.tempRenderMode);
        IndicatorConfig.CLIENT.consecutiveMode.set(this.tempConsecutiveMode);
        IndicatorConfig.CLIENT.showKillAlert.set(this.tempShowKillAlert);
        IndicatorConfig.CLIENT.showHitCount.set(this.tempShowHitCount);

        IndicatorConfig.CLIENT.hudScale.set(this.tempHudScale);
        IndicatorConfig.CLIENT.crosshairOffsetX.set(this.tempOffsetX);
        IndicatorConfig.CLIENT.crosshairOffsetY.set(this.tempOffsetY);
        IndicatorConfig.CLIENT.maxScrolledIndicators.set(this.tempMaxScrolledIndicators);

        IndicatorConfig.CLIENT.showHeadshotIcon.set(this.tempShowHeadshotIcon);
        IndicatorConfig.CLIENT.showCriticalIcon.set(this.tempShowCriticalIcon);
        IndicatorConfig.CLIENT.showArmorPiercingIcon.set(this.tempShowArmorPiercingIcon);
        IndicatorConfig.CLIENT.showArmorDamageIcon.set(this.tempShowArmorDamageIcon);
        IndicatorConfig.CLIENT.enableConstantSize.set(this.tempEnableConstantSize);
        IndicatorConfig.CLIENT.enableXRay.set(this.tempEnableXRay);
        IndicatorConfig.CLIENT.enableDamageVignette.set(this.tempDamageVignetteEnabled);
        IndicatorConfig.CLIENT.damageVignetteOpacity.set(this.tempDamageVignetteOpacity);

        IndicatorConfig.CLIENT.enableHitSound.set(this.tempHitSoundEnabled);
        IndicatorConfig.CLIENT.hitSoundVolume.set(this.tempHitSoundVolume);
        IndicatorConfig.CLIENT.enableHeadshotSound.set(this.tempHeadshotSoundEnabled);
        IndicatorConfig.CLIENT.enableKillSound.set(this.tempKillSoundEnabled);
        IndicatorConfig.CLIENT.killSoundVolume.set(this.tempKillSoundVolume);

        IndicatorConfig.saveConfig();

        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}
