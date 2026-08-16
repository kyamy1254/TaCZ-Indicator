package com.kyamy.taczindicator.client.gui;

import com.kyamy.taczindicator.client.stats.CombatStatsManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * 戦闘統計詳細GUI画面 (CombatStatsScreen)
 * タブ切り替え（総合概要・武器別統計・キル履歴）、武器別キル/ダメージ分析、およびリアルタイム戦闘ログを一覧表示
 */
public class CombatStatsScreen extends Screen {

    public enum StatsViewTab {
        OVERVIEW("taczindicator.stats.tab.overview"),
        WEAPONS("taczindicator.stats.tab.weapons"),
        KILL_LOGS("taczindicator.stats.tab.kill_logs");

        private final String translationKey;

        StatsViewTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getTitle(boolean isSelected) {
            String prefix = isSelected ? "§a§l[" : "§7";
            String suffix = isSelected ? "§a§l]" : "§7";
            return Component.literal(prefix + " ").append(Component.translatable(this.translationKey)).append(suffix);
        }
    }

    private final Screen parentScreen;
    private StatsViewTab currentTab = StatsViewTab.OVERVIEW;
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

        // 1. ビュー切り替えタブバー
        int tabWidth = Math.min(130, (this.width - 20) / 3);
        int tabTotalWidth = tabWidth * 3;
        int tabStartX = centerX - tabTotalWidth / 2;
        int tabY = 22;

        for (int i = 0; i < StatsViewTab.values().length; i++) {
            StatsViewTab tab = StatsViewTab.values()[i];
            this.addRenderableWidget(Button.builder(tab.getTitle(this.currentTab == tab), btn -> {
                this.currentTab = tab;
                this.scrollOffset = 0;
                this.init();
            }).bounds(tabStartX + i * tabWidth, tabY, tabWidth - 2, 18).build());
        }

        int bottomY = this.height - 24;
        int btnWidth = 100;

        // 2. クリップボードコピーボタン
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

        // 3. 統計リセットボタン
        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.gui.reset_stats_btn"),
                btn -> {
                    CombatStatsManager.getInstance().resetStats();
                    this.feedbackMessage = Component.translatable("taczindicator.stats.gui.reset_feedback").getString();
                    this.feedbackTimeMs = System.currentTimeMillis();
                }
        ).bounds(centerX - btnWidth - 5, bottomY, btnWidth, 20).build());

        // 4. 設定画面への遷移ボタン
        this.addRenderableWidget(Button.builder(
                Component.translatable("taczindicator.stats.gui.config_btn"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new IndicatorConfigScreen(this));
                    }
                }
        ).bounds(centerX + 5, bottomY, btnWidth, 20).build());

        // 5. 閉じるボタン
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
        guiGraphics.drawCenteredString(this.font, "§b§l" + this.title.getString(), centerX, 6, 0x00F0FF);

        // フィードバックトースト通知
        if (!this.feedbackMessage.isEmpty() && (System.currentTimeMillis() - this.feedbackTimeMs) < 2500L) {
            guiGraphics.drawCenteredString(this.font, "§a§l✔ " + this.feedbackMessage, centerX, 18, 0x55FF55);
        }

        int marginX = 14;
        int logWidth = this.width - (marginX * 2);

        switch (this.currentTab) {
            case OVERVIEW -> renderOverviewTab(guiGraphics, stats, marginX, logWidth);
            case WEAPONS -> renderWeaponsTab(guiGraphics, stats, marginX, logWidth);
            case KILL_LOGS -> renderKillLogsTab(guiGraphics, stats, marginX, logWidth);
        }
    }

    private void renderOverviewTab(GuiGraphics guiGraphics, CombatStatsManager stats, int marginX, int logWidth) {
        int cardGap = 8;
        int cardWidth = (this.width - (marginX * 2) - cardGap) / 2;
        int cardHeight = 48;
        int topRowY = 44;
        int bottomRowY = topRowY + cardHeight + 6;

        // Card 1: ダメージ & DPS分析 (左上)
        renderCard(guiGraphics, marginX, topRowY, cardWidth, cardHeight, "§6§l[ ⚔ ダメージ & DPS分析 ]", new String[]{
                String.format(Locale.ROOT, "§f総与ダメージ: §a%,.1f", stats.getTotalDamage()),
                String.format(Locale.ROOT, "§f瞬間DPS (3秒): §e%.1f §7| §fピーク: §6%.1f", stats.getDPS(), stats.getPeakDps()),
                String.format(Locale.ROOT, "§f平均DPS: §e%.1f", stats.getAverageDPS())
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

        // 下部: キルログ抜粋プレビュー
        int logStartY = bottomRowY + cardHeight + 6;
        int logHeight = (this.height - 28) - logStartY;

        guiGraphics.fill(marginX, logStartY, marginX + logWidth, logStartY + logHeight, 0x88101520);
        guiGraphics.renderOutline(marginX, logStartY, logWidth, logHeight, 0xAA00A0E9);
        guiGraphics.drawString(this.font, "§c§l" + Component.translatable("taczindicator.stats.gui.kill_logs_title").getString(), marginX + 6, logStartY + 4, 0xFF5555, false);

        List<CombatStatsManager.CombatLogEntry> logs = stats.getCombatLogs();
        int visibleLines = Math.max(1, (logHeight - 16) / 10);
        int contentY = logStartY + 16;

        if (logs.isEmpty()) {
            guiGraphics.drawString(this.font, "§7" + Component.translatable("taczindicator.stats.gui.no_kill_logs").getString(), marginX + 10, contentY + 4, 0x777777, false);
        } else {
            int endIndex = Math.min(logs.size(), visibleLines);
            for (int i = 0; i < endIndex; i++) {
                CombatStatsManager.CombatLogEntry entry = logs.get(i);
                int lineY = contentY + i * 10;
                String timePrefix = "§8[" + entry.getTimeFormatted() + "] ";
                guiGraphics.drawString(this.font, timePrefix + entry.getMessage(), marginX + 8, lineY, 0xFFFFFF, false);
            }
        }
    }

    private void renderWeaponsTab(GuiGraphics guiGraphics, CombatStatsManager stats, int marginX, int logWidth) {
        int listStartY = 44;
        int listHeight = (this.height - 28) - listStartY;

        guiGraphics.fill(marginX, listStartY, marginX + logWidth, listStartY + listHeight, 0x88101520);
        guiGraphics.renderOutline(marginX, listStartY, logWidth, listHeight, 0xAA00A0E9);

        guiGraphics.drawString(this.font, "§6§l" + Component.translatable("taczindicator.stats.gui.weapon_breakdown_title").getString(), marginX + 6, listStartY + 4, 0xFFAA00, false);

        List<CombatStatsManager.WeaponStatEntry> weapons = stats.getWeaponBreakdownList();
        int contentY = listStartY + 16;

        if (weapons.isEmpty()) {
            guiGraphics.drawString(this.font, "§7" + Component.translatable("taczindicator.stats.gui.no_weapon_stats").getString(), marginX + 10, contentY + 8, 0x777777, false);
            return;
        }

        int cardItemHeight = 36;
        int visibleCards = Math.max(1, (listHeight - 18) / cardItemHeight);
        int startIndex = Math.max(0, Math.min(this.scrollOffset, Math.max(0, weapons.size() - visibleCards)));
        int endIndex = Math.min(weapons.size(), startIndex + visibleCards);

        for (int i = startIndex; i < endIndex; i++) {
            CombatStatsManager.WeaponStatEntry w = weapons.get(i);
            int cardY = contentY + (i - startIndex) * cardItemHeight;

            // 各武器の個別カード背景
            guiGraphics.fill(marginX + 4, cardY, marginX + logWidth - 4, cardY + cardItemHeight - 2, 0x55182030);
            guiGraphics.renderOutline(marginX + 4, cardY, logWidth - 8, cardItemHeight - 2, 0x6600A0E9);

            // 行1: 武器名 & ダメージ & キル
            String line1 = String.format(Locale.ROOT, "§e§l[ 🔫 %s ]  §f総ダメージ: §a%,.1f §7| §fキル数: §c%d 体",
                    w.getWeaponName(), w.getTotalDamage(), w.getTotalKills());
            guiGraphics.drawString(this.font, line1, marginX + 8, cardY + 3, 0xFFFFFF, false);

            // 行2: 命中数、HS率、AP
            String line2 = String.format(Locale.ROOT, "§f命中数: §b%d 発 §7(§c☠ HS: %d発 / %.1f%%§7) §7| §6★ Crit: %d §7| §f\uE002 AP: %d",
                    w.getTotalHits(), w.getTotalHeadshots(), w.getHeadshotRate(), w.getTotalCriticals(), w.getTotalArmorPiercing());
            guiGraphics.drawString(this.font, line2, marginX + 12, cardY + 14, 0xDDDDDD, false);

            // 行3: 最大単発 & 最長キル
            String line3 = String.format(Locale.ROOT, "§7最大単発: §d%.1f dmg §7| 最長キル距離: §e%d m",
                    w.getMaxSingleDamage(), w.getMaxKillDistance());
            guiGraphics.drawString(this.font, line3, marginX + 12, cardY + 24, 0xAAAAAA, false);
        }

        // スクロールインジケータ
        if (weapons.size() > visibleCards) {
            String scrollInfo = String.format(Locale.ROOT, "§8%d-%d / %d", startIndex + 1, endIndex, weapons.size());
            guiGraphics.drawString(this.font, scrollInfo, marginX + logWidth - this.font.width(scrollInfo) - 6, listStartY + 4, 0x888888, false);
        }
    }

    private void renderKillLogsTab(GuiGraphics guiGraphics, CombatStatsManager stats, int marginX, int logWidth) {
        int listStartY = 44;
        int listHeight = (this.height - 28) - listStartY;

        guiGraphics.fill(marginX, listStartY, marginX + logWidth, listStartY + listHeight, 0x88101520);
        guiGraphics.renderOutline(marginX, listStartY, logWidth, listHeight, 0xAA00A0E9);

        guiGraphics.drawString(this.font, "§c§l" + Component.translatable("taczindicator.stats.gui.kill_logs_title").getString(), marginX + 6, listStartY + 4, 0xFF5555, false);

        List<CombatStatsManager.CombatLogEntry> logs = stats.getCombatLogs();
        int visibleLines = Math.max(1, (listHeight - 16) / 10);
        int contentY = listStartY + 16;

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

            if (logs.size() > visibleLines) {
                String scrollInfo = String.format(Locale.ROOT, "§8%d-%d / %d", startIndex + 1, endIndex, logs.size());
                guiGraphics.drawString(this.font, scrollInfo, marginX + logWidth - this.font.width(scrollInfo) - 6, listStartY + 4, 0x888888, false);
            }
        }
    }

    private void renderCard(GuiGraphics guiGraphics, int x, int y, int w, int h, String header, String[] lines) {
        guiGraphics.fill(x, y, x + w, y + h, 0xAA101520);
        guiGraphics.renderOutline(x, y, w, h, 0xAA00A0E9);

        guiGraphics.drawString(this.font, header, x + 6, y + 4, 0xFFFFFF, false);
        for (int i = 0; i < lines.length; i++) {
            guiGraphics.drawString(this.font, lines[i], x + 8, y + 15 + (i * 10), 0xEEEEEE, false);
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
