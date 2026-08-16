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

        String formattedTotalDmg = String.format(Locale.ROOT, "%,.1f", stats.getTotalDamage());

        // Card 1: ダメージ & DPS分析 (左上)
        renderCard(guiGraphics, marginX, topRowY, cardWidth, cardHeight,
                Component.translatable("taczindicator.stats.card.damage_dps").getString(),
                new String[]{
                        Component.translatable("taczindicator.stats.label.total_damage", formattedTotalDmg).getString(),
                        Component.translatable("taczindicator.stats.label.dps_peak", stats.getDPS(), stats.getPeakDps()).getString(),
                        Component.translatable("taczindicator.stats.label.avg_dps", stats.getAverageDPS()).getString()
                });

        // Card 2: 命中 & 射撃分析 (右上)
        renderCard(guiGraphics, marginX + cardWidth + cardGap, topRowY, cardWidth, cardHeight,
                Component.translatable("taczindicator.stats.card.hit_analysis").getString(),
                new String[]{
                        Component.translatable("taczindicator.stats.label.total_hits", stats.getTotalHits()).getString(),
                        Component.translatable("taczindicator.stats.label.headshots", stats.getTotalHeadshots(), stats.getHeadshotRate()).getString(),
                        Component.translatable("taczindicator.stats.label.criticals", stats.getTotalCriticals(), stats.getCriticalRate()).getString()
                });

        // Card 3: 弾薬 & 装甲貫通 (左下)
        long combatSec = stats.getTotalCombatDurationMs() / 1000L;
        renderCard(guiGraphics, marginX, bottomRowY, cardWidth, cardHeight,
                Component.translatable("taczindicator.stats.card.armor_penetration").getString(),
                new String[]{
                        Component.translatable("taczindicator.stats.label.ap_armor", stats.getTotalArmorPiercing(), stats.getTotalArmorDamage()).getString(),
                        Component.translatable("taczindicator.stats.label.max_damage", stats.getMaxSingleDamage()).getString(),
                        Component.translatable("taczindicator.stats.label.combat_time", combatSec / 60, combatSec % 60).getString()
                });

        // Card 4: キル記録 & 狙撃 (右下)
        renderCard(guiGraphics, marginX + cardWidth + cardGap, bottomRowY, cardWidth, cardHeight,
                Component.translatable("taczindicator.stats.card.kills_distance").getString(),
                new String[]{
                        Component.translatable("taczindicator.stats.label.total_kills", stats.getTotalKills()).getString(),
                        Component.translatable("taczindicator.stats.label.max_distance", stats.getMaxKillDistance()).getString(),
                        Component.translatable("taczindicator.stats.label.avg_distance", stats.getAverageKillDistance()).getString()
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

            String formattedDmg = String.format(Locale.ROOT, "%,.1f", w.getTotalDamage());

            // 行1: 武器名 & ダメージ & キル
            String line1 = Component.translatable("taczindicator.stats.weapon.line1", w.getWeaponName(), formattedDmg, w.getTotalKills()).getString();
            guiGraphics.drawString(this.font, line1, marginX + 8, cardY + 3, 0xFFFFFF, false);

            // 行2: 命中数、HS率、AP
            String line2 = Component.translatable("taczindicator.stats.weapon.line2",
                    w.getTotalHits(), w.getTotalHeadshots(), w.getHeadshotRate(), w.getTotalCriticals(), w.getTotalArmorPiercing()).getString();
            guiGraphics.drawString(this.font, line2, marginX + 12, cardY + 14, 0xDDDDDD, false);

            // 行3: 最大単発 & 最長キル
            String line3 = Component.translatable("taczindicator.stats.weapon.line3", w.getMaxSingleDamage(), w.getMaxKillDistance()).getString();
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
