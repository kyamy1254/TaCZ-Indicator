package com.kyamy.taczindicator.client.gui;

import com.kyamy.taczindicator.config.IndicatorConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ゲーム内リアルタイムGUI設定画面
 * クロスヘア周辺のプレビューをドラッグして直感的に位置調整可能
 */
public class IndicatorConfigScreen extends Screen {

    private final Screen parentScreen;

    // 一時編集用設定値
    private boolean tempEnabled;
    private boolean tempOnlyPlayerDamage;
    private boolean tempOnlyTaczDamage;
    private IndicatorConfig.RenderMode tempRenderMode;
    private IndicatorConfig.ConsecutiveMode tempConsecutiveMode;
    private boolean tempShowKillAlert;
    private boolean tempShowHitCount;
    private double tempHudScale;
    private double tempOffsetX;
    private double tempOffsetY;

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
    }

    @Override
    protected void init() {
        super.init();

        int leftCol = 20;
        int rightCol = this.width - 170;
        int btnWidth = 150;
        int btnHeight = 20;
        int startY = 35;

        // --- 左側ボタングループ ---
        // 1. MOD有効/無効
        this.addRenderableWidget(Button.builder(
                getEnabledText(),
                btn -> {
                    this.tempEnabled = !this.tempEnabled;
                    btn.setMessage(getEnabledText());
                }
        ).bounds(leftCol, startY, btnWidth, btnHeight).build());

        // 2. プレイヤー限定
        this.addRenderableWidget(Button.builder(
                getOnlyPlayerDamageText(),
                btn -> {
                    this.tempOnlyPlayerDamage = !this.tempOnlyPlayerDamage;
                    btn.setMessage(getOnlyPlayerDamageText());
                }
        ).bounds(leftCol, startY + 25, btnWidth, btnHeight).build());

        // 3. TaCZ銃撃限定
        this.addRenderableWidget(Button.builder(
                getOnlyTaczDamageText(),
                btn -> {
                    this.tempOnlyTaczDamage = !this.tempOnlyTaczDamage;
                    btn.setMessage(getOnlyTaczDamageText());
                }
        ).bounds(leftCol, startY + 50, btnWidth, btnHeight).build());

        // 4. 描画モード
        this.addRenderableWidget(Button.builder(
                getRenderModeText(),
                btn -> {
                    IndicatorConfig.RenderMode[] modes = IndicatorConfig.RenderMode.values();
                    int nextIdx = (this.tempRenderMode.ordinal() + 1) % modes.length;
                    this.tempRenderMode = modes[nextIdx];
                    btn.setMessage(getRenderModeText());
                }
        ).bounds(leftCol, startY + 75, btnWidth, btnHeight).build());

        // 5. 連続ダメージ形式
        this.addRenderableWidget(Button.builder(
                getConsecutiveModeText(),
                btn -> {
                    IndicatorConfig.ConsecutiveMode[] modes = IndicatorConfig.ConsecutiveMode.values();
                    int nextIdx = (this.tempConsecutiveMode.ordinal() + 1) % modes.length;
                    this.tempConsecutiveMode = modes[nextIdx];
                    btn.setMessage(getConsecutiveModeText());
                }
        ).bounds(leftCol, startY + 100, btnWidth, btnHeight).build());

        // --- 右側ボタングループ ---
        // 6. キル演出トグル
        this.addRenderableWidget(Button.builder(
                getKillAlertText(),
                btn -> {
                    this.tempShowKillAlert = !this.tempShowKillAlert;
                    btn.setMessage(getKillAlertText());
                }
        ).bounds(rightCol, startY, btnWidth, btnHeight).build());

        // 7. ヒット数表示トグル
        this.addRenderableWidget(Button.builder(
                getShowHitCountText(),
                btn -> {
                    this.tempShowHitCount = !this.tempShowHitCount;
                    btn.setMessage(getShowHitCountText());
                }
        ).bounds(rightCol, startY + 25, btnWidth, btnHeight).build());

        // 8. スケール縮小 [-]
        this.addRenderableWidget(Button.builder(
                Component.literal("Scale -"),
                btn -> {
                    this.tempHudScale = Math.max(0.4, Math.round((this.tempHudScale - 0.1) * 10.0) / 10.0);
                }
        ).bounds(rightCol, startY + 50, 72, btnHeight).build());

        // 9. スケール拡大 [+]
        this.addRenderableWidget(Button.builder(
                Component.literal("Scale +"),
                btn -> {
                    this.tempHudScale = Math.min(3.0, Math.round((this.tempHudScale + 0.1) * 10.0) / 10.0);
                }
        ).bounds(rightCol + 78, startY + 50, 72, btnHeight).build());

        // 10. 位置リセット
        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.gui.reset_defaults"),
                btn -> {
                    this.tempOffsetX = 18.0;
                    this.tempOffsetY = -4.0;
                    this.tempHudScale = 1.15;
                }
        ).bounds(rightCol, startY + 75, btnWidth, btnHeight).build());

        // --- 下部アクションボタン ---
        int bottomY = this.height - 28;
        int centerBtnWidth = 140;

        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.gui.save_and_close"),
                btn -> saveAndClose()
        ).bounds(this.width / 2 - centerBtnWidth - 10, bottomY, centerBtnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                btn -> this.onClose()
        ).bounds(this.width / 2 + 10, bottomY, centerBtnWidth, btnHeight).build());
    }

    private Component getEnabledText() {
        return Component.translatable("taczindicator.gui.enabled",
                this.tempEnabled ? "§aON" : "§cOFF");
    }

    private Component getOnlyPlayerDamageText() {
        return Component.translatable("taczindicator.gui.only_player_damage",
                this.tempOnlyPlayerDamage ? "§aON" : "§cOFF");
    }

    private Component getOnlyTaczDamageText() {
        return Component.translatable("taczindicator.gui.only_tacz_damage",
                this.tempOnlyTaczDamage ? "§aON" : "§cOFF");
    }

    private Component getRenderModeText() {
        return Component.translatable("taczindicator.gui.render_mode", "§b" + this.tempRenderMode.name());
    }

    private Component getConsecutiveModeText() {
        return Component.translatable("taczindicator.gui.consecutive_mode", "§b" + this.tempConsecutiveMode.name());
    }

    private Component getKillAlertText() {
        return Component.translatable("taczindicator.gui.show_kill_alert",
                this.tempShowKillAlert ? "§aON" : "§cOFF");
    }

    private Component getShowHitCountText() {
        return Component.translatable("taczindicator.gui.show_hit_count",
                this.tempShowHitCount ? "§aON" : "§cOFF");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 背景の半透明暗転
        this.renderBackground(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // タイトル
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 10, 0xFFFFFF);
        // 操作説明
        guiGraphics.drawCenteredString(this.font, Component.translatable("taczindicator.gui.drag_instruction"), centerX, 24, 0xAAAAAA);

        // 中央クロスヘアの描画
        guiGraphics.drawString(this.font, "+", centerX - this.font.width("+") / 2, centerY - this.font.lineHeight / 2, 0xFFFFFF, false);

        // プレビューサンプル描画（ドラッグ可能な数値）
        double sampleX = centerX + this.tempOffsetX;
        double sampleY = centerY + this.tempOffsetY;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(sampleX, sampleY, 0.0);
        guiGraphics.pose().scale((float) this.tempHudScale, (float) this.tempHudScale, 1.0f);

        String sampleText = "§b🗡 §l45.0 §7(x2)";
        if (this.tempRenderMode == IndicatorConfig.RenderMode.HUD_PROJECTED) {
            guiGraphics.drawString(this.font, sampleText, -this.font.width(sampleText) / 2, -this.font.lineHeight / 2, 0xFFFFFFFF, true);
        } else {
            guiGraphics.drawString(this.font, sampleText, 0, -this.font.lineHeight / 2, 0xFFFFFFFF, true);
        }

        // ドラッグ枠線の強調表示
        int textW = this.font.width(sampleText);
        int textH = this.font.lineHeight;
        int boxX = (this.tempRenderMode == IndicatorConfig.RenderMode.HUD_PROJECTED) ? -textW / 2 - 2 : -2;
        int boxY = -textH / 2 - 2;
        guiGraphics.renderOutline(boxX, boxY, textW + 4, textH + 4, isDragging ? 0xFF55FF55 : 0x88FFFFFF);

        guiGraphics.pose().popPose();

        // キル通知サンプル
        if (this.tempShowKillAlert) {
            String killSample = Component.translatable("taczindicator.kill.single", "Zombie").getString();
            int killW = this.font.width(killSample);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY + 28, 0.0);
            guiGraphics.drawString(this.font, killSample, -killW / 2, -this.font.lineHeight / 2, 0xFFFFFFFF, true);
            guiGraphics.pose().popPose();
        }

        // スケール表示
        guiGraphics.drawString(this.font, String.format(java.util.Locale.ROOT, "Scale: %.1fx  |  Pos: (%.0f, %.0f)", this.tempHudScale, this.tempOffsetX, this.tempOffsetY), centerX - 60, this.height - 45, 0xDDDDDD, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        double sampleX = centerX + this.tempOffsetX;
        double sampleY = centerY + this.tempOffsetY;

        // サンプル数値付近がクリックされたらドラッグ開始
        if (Math.abs(mouseX - sampleX) < 40 && Math.abs(mouseY - sampleY) < 20) {
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
