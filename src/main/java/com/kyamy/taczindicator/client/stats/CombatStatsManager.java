package com.kyamy.taczindicator.client.stats;

import com.kyamy.taczindicator.config.IndicatorConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * クライアント側戦闘統計・DPSマネージャー
 * 直近3秒間のスライディングウィンドウによる正確な瞬間DPS計算、総与ダメ、命中数、HS率、キル数を集計
 */
public class CombatStatsManager {
    private static final CombatStatsManager INSTANCE = new CombatStatsManager();

    public static CombatStatsManager getInstance() {
        return INSTANCE;
    }

    private static class DamageEntry {
        final long timestampMs;
        final float damage;

        DamageEntry(long timestampMs, float damage) {
            this.timestampMs = timestampMs;
            this.damage = damage;
        }
    }

    private double totalDamage = 0.0;
    private int totalHits = 0;
    private int totalHeadshots = 0;
    private int totalKills = 0;

    private final List<DamageEntry> damageWindow = new ArrayList<>();
    private long lastDamageTimeMs = 0L;

    private static final long WINDOW_MS = 3000L; // 直近3秒間
    private static final long COMBAT_TIMEOUT_MS = 5000L; // 非戦闘移行5秒

    private CombatStatsManager() {}

    /**
     * ダメージヒットを記録
     */
    public synchronized void recordDamage(float damage, boolean isHeadshot, boolean isKill) {
        long now = System.currentTimeMillis();
        this.totalDamage += damage;
        this.totalHits++;
        if (isHeadshot) {
            this.totalHeadshots++;
        }
        if (isKill) {
            this.totalKills++;
        }
        this.lastDamageTimeMs = now;

        cleanOldEntries(now);
        this.damageWindow.add(new DamageEntry(now, damage));
    }

    /**
     * キル確定を記録
     */
    public synchronized void recordKill() {
        this.totalKills++;
        this.lastDamageTimeMs = System.currentTimeMillis();
    }

    private void cleanOldEntries(long now) {
        long cutoff = now - WINDOW_MS;
        this.damageWindow.removeIf(entry -> entry.timestampMs < cutoff);
    }

    /**
     * 直近の瞬間DPSを計算（3秒スライディングウィンドウ）
     */
    public synchronized float getDPS() {
        long now = System.currentTimeMillis();
        cleanOldEntries(now);

        if (this.damageWindow.isEmpty()) {
            return 0.0f;
        }

        float sum = 0.0f;
        for (DamageEntry entry : this.damageWindow) {
            sum += entry.damage;
        }

        long oldest = this.damageWindow.get(0).timestampMs;
        double durationSec = Math.max(1.0, (now - oldest) / 1000.0);
        return (float) (sum / Math.min(3.0, durationSec));
    }

    public synchronized double getTotalDamage() {
        return totalDamage;
    }

    public synchronized int getTotalHits() {
        return totalHits;
    }

    public synchronized int getTotalHeadshots() {
        return totalHeadshots;
    }

    public synchronized int getTotalKills() {
        return totalKills;
    }

    public synchronized float getHeadshotRate() {
        if (this.totalHits <= 0) {
            return 0.0f;
        }
        return ((float) this.totalHeadshots / (float) this.totalHits) * 100.0f;
    }

    public synchronized boolean isInCombat() {
        return (System.currentTimeMillis() - this.lastDamageTimeMs) < COMBAT_TIMEOUT_MS;
    }

    /**
     * 表示用の透明度（フェードイン/フェードアウト）を計算
     */
    public synchronized float getDisplayAlpha() {
        IndicatorConfig.CombatStatsDisplayMode mode = IndicatorConfig.getCombatStatsMode();
        if (mode == IndicatorConfig.CombatStatsDisplayMode.OFF) {
            return 0.0f;
        }
        if (mode == IndicatorConfig.CombatStatsDisplayMode.ALWAYS) {
            return 1.0f;
        }

        // COMBAT_ONLY モード
        long elapsed = System.currentTimeMillis() - this.lastDamageTimeMs;
        if (elapsed < 3500L) {
            return 1.0f;
        }
        if (elapsed < COMBAT_TIMEOUT_MS) {
            return (float) (COMBAT_TIMEOUT_MS - elapsed) / 1500.0f;
        }
        return 0.0f;
    }

    /**
     * 統計情報をリセット (サーバー同期またはクライアント手動)
     */
    public synchronized void resetStats() {
        this.totalDamage = 0.0;
        this.totalHits = 0;
        this.totalHeadshots = 0;
        this.totalKills = 0;
        this.damageWindow.clear();
        this.lastDamageTimeMs = 0L;
    }
}
