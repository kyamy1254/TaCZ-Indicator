package com.kyamy.taczindicator.client.gui;

import com.kyamy.taczindicator.client.stats.CombatStatsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * 戦闘統計詳細GUI画面 (CombatStatsScreen)
 * ダメージ、DPS、命中分析、防具貫通、キル距離、およびリアルタイム戦闘ログを一覧表示
 */
public class CombatStatsScreen extends Screen {

    private final Screen parentScreen;
    private int scrollOffset = 0;
    private String feedbackMessage = "";
    private long feedbackTimeMs = 0L;

    public CombatStatsScreen(Screen parentScreen) {
        super(Component.translatable("taczindicator.stats.gui.title"));
        this.parentScreen = parentScreen;
    }

    public CombatStatsScreen() {
        this(null);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int centerX = this.width / 2;
        int bottomY = this.height - 24;
        int btnWidth = 100;

        // 1. クリップボードコピーボタン
        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.stats.gui.copy_btn"),
                btn -> {
                    String report = CombatStatsManager.getInstance().generateStatsReportText();
                    if (this.minecraft != null) {
                        this.minecraft.keyboardHandler.setClipboard(report);
                        this.feedbackMessage = Component.translatable("taczindicator.stats.gui.copied_feedback").getString();
                        this.feedbackTimeMs = System.currentTimeMillis();
                    }
                }
        ).bounds(centerX - btnWidth * 2 - 15, bottomY, btnWidth, 20).build());

        // 2. 統計リセットボタン
        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.gui.reset_stats_btn"),
                btn -> {
                    CombatStatsManager.getInstance().resetStats();
                    this.feedbackMessage = Component.translatable("taczindicator.stats.gui.reset_feedback").getString();
                    this.feedbackTimeMs = System.currentTimeMillis();
                }
        ).bounds(centerX - btnWidth - 5, bottomY, btnWidth, 20).build());

        // 3. 設定画面への遷移ボタン
        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.stats.gui.config_btn"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new IndicatorConfigScreen(this));
                    }
                }
        ).bounds(centerX + 5, bottomY, btnWidth, 20).build());

        // 4. 閉じるボタン
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> this.onClose()
        ).bounds(centerX + btnWidth + 15, bottomY, btnWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        CombatStatsManager stats = CombatStatsManager.getInstance();

        // 1. タイトルヘッダー
        guiGraphics.drawCenteredString(this.font, "§b§l" + this.title.getString(), centerX, 8, 0x00F0FF);

        // フィードバックトースト通知
        if (!this.feedbackMessage.isEmpty() && (System.currentTimeMillis() - this.feedbackTimeMs) < 2500L) {
            guiGraphics.drawCenteredString(this.font, "§a§l✔ " + this.feedbackMessage, centerX, 20, 0x55FF55);
        } else {
            guiGraphics.drawCenteredString(this.font, "§7" + Component.translatable("taczindicator.stats.gui.subtitle").getString(), centerX, 20, 0x888888);
        }

        // 2. 統計カードグリッド (2列 x 2行)
        int marginX = 14;
        int cardGap = 8;
        int cardWidth = (this.width - (marginX * 2) - cardGap) / 2;
        int cardHeight = 52;
        int topRowY = 32;
        int bottomRowY = topRowY + cardHeight + cardGap;

        // Card 1: ダメージ & DPS分析 (左上)
        renderCard(guiGraphics, marginX, topRowY, cardWidth, cardHeight, "§6§l[ ⚔ ダメージ & DPS分析 ]", new String[]{
                String.format(Locale.ROOT, "§f総与ダメージ: §a%,.1f", stats.getTotalDamage()),
                String.format(Locale.ROOT, "§f瞬間DPS (直近3秒): §e%.1f", stats.getDPS()),
                String.format(Locale.ROOT, "§fピークDPS: §6%.1f §7| §f平均: §e%.1f", stats.getPeakDps(), stats.getAverageDPS())
        });

        // Card 2: 命中 & 射撃分析 (右上)
        renderCard(guiGraphics, marginX + cardWidth + cardGap, topRowY, cardWidth, cardHeight, "§b§l[ 🎯 命中 & 射撃分析 ]", new String[]{
                String.format(Locale.ROOT, "§f総命中数: §b%d 発", stats.getTotalHits()),
                String.format(Locale.ROOT, "§fヘッドショット: §c☠ %d 発 §7(§c%.1f%%§7)", stats.getTotalHeadshots(), stats.getHeadshotRate()),
                String.format(Locale.ROOT, "§fクリティカル: §6★ %d 発 §7(§6%.1f%%§7)", stats.getTotalCriticals(), stats.getCriticalRate())
        });

        // Card 3: 弾薬 & 装甲貫通 (左下)
        long combatSec = stats.getTotalCombatDurationMs() / 1000L;
        renderCard(guiGraphics, marginX, bottomRowY, cardWidth, cardHeight, "§d§l[ \uE001 装甲貫通 & 単発火力 ]", new String[]{
                String.format(Locale.ROOT, "§f防具貫通(AP)弾: §f\uE002 %d 発 §7| §f防具軽減: §b\uE001 %d 発", stats.getTotalArmorPiercing(), stats.getTotalArmorDamage()),
                String.format(Locale.ROOT, "§f最大単発ダメージ: §d%.1f", stats.getMaxSingleDamage()),
                String.format(Locale.ROOT, "§f実戦闘時間: §f%02d:%02d", combatSec / 60, combatSec % 60)
        });

        // Card 4: キル記録 & 狙撃 (右下)
        renderCard(guiGraphics, marginX + cardWidth + cardGap, bottomRowY, cardWidth, cardHeight, "§c§l[ ☠ キル記録 & 狙撃距離 ]", new String[]{
                String.format(Locale.ROOT, "§f総キル数: §c%d 体", stats.getTotalKills()),
                String.format(Locale.ROOT, "§f最長キル距離: §e%d m", stats.getMaxKillDistance()),
                String.format(Locale.ROOT, "§f平均キル距離: §7%.1f m", stats.getAverageKillDistance())
        });

        // 3. 戦闘ログ履歴エリア (Combat Log Area)
        int logStartY = bottomRowY + cardHeight + 8;
        int logHeight = (this.height - 30) - logStartY;
        int logWidth = this.width - (marginX * 2);

        // ログ外枠と背景
        guiGraphics.fill(marginX, logStartY, marginX + logWidth, logStartY + logHeight, 0x88101520);
        guiGraphics.renderOutline(marginX, logStartY, logWidth, logHeight, 0xAA00A0E9);

        // ログヘッダー
        guiGraphics.drawString(this.font, "§c§l" + Component.translatable("taczindicator.stats.gui.kill_logs_title").getString(), marginX + 6, logStartY + 4, 0xFF5555, false);

        List<CombatStatsManager.CombatLogEntry> logs = stats.getCombatLogs();
        int visibleLines = (logHeight - 16) / 10;
        int contentY = logStartY + 16;

        if (logs.isEmpty()) {
            guiGraphics.drawString(this.font, "§7" + Component.translatable("taczindicator.stats.gui.no_kill_logs").getString(), marginX + 10, contentY + 6, 0x777777, false);
        } else {
            int startIndex = Math.max(0, Math.min(this.scrollOffset, Math.max(0, logs.size() - visibleLines)));
            int endIndex = Math.min(logs.size(), startIndex + visibleLines);

            for (int i = startIndex; i < endIndex; i++) {
                CombatStatsManager.CombatLogEntry entry = logs.get(i);
                int lineY = contentY + (i - startIndex) * 10;
                String timePrefix = "§8[" + entry.getTimeFormatted() + "] ";
                guiGraphics.drawString(this.font, timePrefix + entry.getMessage(), marginX + 8, lineY, 0xFFFFFF, false);
            }

            // スクロールインジケータ
            if (logs.size() > visibleLines) {
                String scrollInfo = String.format(Locale.ROOT, "§8%d-%d / %d", startIndex + 1, endIndex, logs.size());
                guiGraphics.drawString(this.font, scrollInfo, marginX + logWidth - this.font.width(scrollInfo) - 6, logStartY + 4, 0x888888, false);
            }
        }
    }

    private void renderCard(GuiGraphics guiGraphics, int x, int y, int w, int h, String header, String[] lines) {
        guiGraphics.fill(x, y, x + w, y + h, 0xAA101520);
        guiGraphics.renderOutline(x, y, w, h, 0xAA00A0E9);

        guiGraphics.drawString(this.font, header, x + 6, y + 4, 0xFFFFFF, false);
        for (int i = 0; i < lines.length; i++) {
            guiGraphics.drawString(this.font, lines[i], x + 8, y + 15 + (i * 11), 0xEEEEEE, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            return true;
        } else if (delta < 0) {
            this.scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}
