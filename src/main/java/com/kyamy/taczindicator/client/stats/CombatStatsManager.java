package com.kyamy.taczindicator.client.stats;

import com.kyamy.taczindicator.config.IndicatorConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * クライアント側詳細戦闘統計・DPSマネージャー
 * 瞬間DPS（3秒スライディングウィンドウ）、ピークDPS、平均DPS、最大単発ダメージ、命中分析、キル距離、武器別統計、および戦闘ログ履歴を総合管理
 */
public class CombatStatsManager {
    private static final CombatStatsManager INSTANCE = new CombatStatsManager();

    public static CombatStatsManager getInstance() {
        return INSTANCE;
    }

    public static class DamageEntry {
        final long timestampMs;
        final float damage;

        DamageEntry(long timestampMs, float damage) {
            this.timestampMs = timestampMs;
            this.damage = damage;
        }
    }

    public static class CombatLogEntry {
        private final long timestampMs;
        private final String timeFormatted;
        private final String message;
        private final float damage;
        private final boolean isKill;
        private final boolean isHeadshot;
        private final boolean isCritical;
        private final boolean isArmorPiercing;
        private final String weaponName;

        public CombatLogEntry(long timestampMs, String message, float damage, boolean isKill, boolean isHeadshot, boolean isCritical, boolean isArmorPiercing, String weaponName) {
            this.timestampMs = timestampMs;
            this.timeFormatted = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(timestampMs));
            this.message = message;
            this.damage = damage;
            this.isKill = isKill;
            this.isHeadshot = isHeadshot;
            this.isCritical = isCritical;
            this.isArmorPiercing = isArmorPiercing;
            this.weaponName = weaponName != null ? weaponName : "";
        }

        public long getTimestampMs() { return timestampMs; }
        public String getTimeFormatted() { return timeFormatted; }
        public String getMessage() { return message; }
        public float getDamage() { return damage; }
        public boolean isKill() { return isKill; }
        public boolean isHeadshot() { return isHeadshot; }
        public boolean isCritical() { return isCritical; }
        public boolean isArmorPiercing() { return isArmorPiercing; }
        public String getWeaponName() { return weaponName; }
    }

    public static class WeaponStatEntry {
        private final String weaponName;
        private double totalDamage = 0.0;
        private int totalHits = 0;
        private int totalHeadshots = 0;
        private int totalCriticals = 0;
        private int totalArmorPiercing = 0;
        private int totalArmorDamage = 0;
        private int totalKills = 0;
        private float maxSingleDamage = 0.0f;
        private int maxKillDistance = 0;

        public WeaponStatEntry(String weaponName) {
            this.weaponName = weaponName;
        }

        public synchronized void recordHit(float damage, boolean isHeadshot, boolean isCritical, boolean isArmorPiercing, boolean hitArmor, boolean isKill, int distanceMeters) {
            this.totalDamage += damage;
            this.totalHits++;
            if (isHeadshot) this.totalHeadshots++;
            if (isCritical) this.totalCriticals++;
            if (isArmorPiercing) this.totalArmorPiercing++;
            if (hitArmor) this.totalArmorDamage++;
            if (damage > this.maxSingleDamage) {
                this.maxSingleDamage = damage;
            }
            if (isKill) {
                this.totalKills++;
                if (distanceMeters > this.maxKillDistance) {
                    this.maxKillDistance = distanceMeters;
                }
            }
        }

        public synchronized void recordKillOnly(int distanceMeters, boolean isHeadshot, boolean isCritical, boolean isArmorPiercing) {
            this.totalKills++;
            if (isHeadshot) this.totalHeadshots++;
            if (isCritical) this.totalCriticals++;
            if (isArmorPiercing) this.totalArmorPiercing++;
            if (distanceMeters > this.maxKillDistance) {
                this.maxKillDistance = distanceMeters;
            }
        }

        public String getWeaponName() { return weaponName; }
        public double getTotalDamage() { return totalDamage; }
        public int getTotalHits() { return totalHits; }
        public int getTotalHeadshots() { return totalHeadshots; }
        public int getTotalCriticals() { return totalCriticals; }
        public int getTotalArmorPiercing() { return totalArmorPiercing; }
        public int getTotalArmorDamage() { return totalArmorDamage; }
        public int getTotalKills() { return totalKills; }
        public float getMaxSingleDamage() { return maxSingleDamage; }
        public int getMaxKillDistance() { return maxKillDistance; }

        public float getHeadshotRate() {
            if (this.totalHits <= 0) return 0.0f;
            return ((float) this.totalHeadshots / (float) this.totalHits) * 100.0f;
        }

        public float getCriticalRate() {
            if (this.totalHits <= 0) return 0.0f;
            return ((float) this.totalCriticals / (float) this.totalHits) * 100.0f;
        }
    }

    private double totalDamage = 0.0;
    private int totalHits = 0;
    private int totalHeadshots = 0;
    private int totalCriticals = 0;
    private int totalArmorPiercing = 0;
    private int totalArmorDamage = 0;
    private int totalKills = 0;

    private float maxSingleDamage = 0.0f;
    private float peakDps = 0.0f;
    private int maxKillDistance = 0;
    private int totalKillDistance = 0;

    private long firstCombatTimeMs = 0L;
    private long totalCombatActiveMs = 0L;
    private long lastDamageTimeMs = 0L;

    private final List<DamageEntry> damageWindow = new ArrayList<>();
    private final List<CombatLogEntry> combatLogs = new ArrayList<>();
    private final Map<String, WeaponStatEntry> weaponStats = new LinkedHashMap<>();

    private static final long WINDOW_MS = 3000L; // 直近3秒間
    private static final long COMBAT_TIMEOUT_MS = 5000L; // 非戦闘移行5秒
    private static final int MAX_LOGS = 50;

    private CombatStatsManager() {}

    /**
     * 詳細ダメージヒットの記録
     */
    public synchronized void recordDamage(float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ,
                                         boolean isArmorPiercing, boolean hitArmor, boolean isKill,
                                         String victimName, int distanceMeters, String weaponName) {
        long now = System.currentTimeMillis();

        if (this.firstCombatTimeMs == 0L) {
            this.firstCombatTimeMs = now;
        }

        // 戦闘継続時間の加算
        if (this.lastDamageTimeMs > 0L) {
            long delta = now - this.lastDamageTimeMs;
            if (delta < COMBAT_TIMEOUT_MS) {
                this.totalCombatActiveMs += delta;
            }
        }

        this.totalDamage += damage;
        this.totalHits++;

        if (isHeadshot) this.totalHeadshots++;
        if (isCritical) this.totalCriticals++;
        if (isArmorPiercing) this.totalArmorPiercing++;
        if (hitArmor) this.totalArmorDamage++;

        if (damage > this.maxSingleDamage) {
            this.maxSingleDamage = damage;
        }

        if (isKill) {
            this.totalKills++;
            if (distanceMeters > this.maxKillDistance) {
                this.maxKillDistance = distanceMeters;
            }
            if (distanceMeters > 0) {
                this.totalKillDistance += distanceMeters;
            }
        }

        // 武器別統計の記録
        String validWeapon = (weaponName != null && !weaponName.isBlank()) ? weaponName : (isTaCZ ? "TaCZ Gun" : "Melee");
        this.weaponStats.computeIfAbsent(validWeapon, WeaponStatEntry::new)
                .recordHit(damage, isHeadshot, isCritical, isArmorPiercing, hitArmor, isKill, distanceMeters);

        this.lastDamageTimeMs = now;
        cleanOldEntries(now);
        this.damageWindow.add(new DamageEntry(now, damage));

        // 現在DPSとピークDPSの更新
        float currentDps = getDPS();
        if (currentDps > this.peakDps) {
            this.peakDps = currentDps;
        }

        // ログエントリの作成（キル確定時のみ履歴リストに記録）
        if (isKill) {
            String targetStr = (victimName != null && !victimName.isEmpty()) ? victimName : "Target";
            StringBuilder logMsg = new StringBuilder();
            logMsg.append("§c☠ Killed ").append(targetStr);
            if (!validWeapon.isBlank() && !validWeapon.equalsIgnoreCase("Melee")) {
                logMsg.append(" §6[").append(validWeapon).append("]");
            }
            if (distanceMeters > 0) {
                logMsg.append(" §7[").append(distanceMeters).append("m]");
            }
            if (damage > 0.001f) {
                logMsg.append(" §f(").append(String.format(Locale.ROOT, "%.1f dmg", damage)).append(")");
            }
            if (isHeadshot) logMsg.append(" §c[HS ☠]");
            else if (isCritical) logMsg.append(" §6[Crit ★]");
            if (isArmorPiercing) logMsg.append(" §f[AP \uE002]");

            this.combatLogs.add(0, new CombatLogEntry(now, logMsg.toString(), damage, true, isHeadshot, isCritical, isArmorPiercing, validWeapon));
            if (this.combatLogs.size() > MAX_LOGS) {
                this.combatLogs.remove(this.combatLogs.size() - 1);
            }
        }
    }

    public synchronized void recordDamage(float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ,
                                         boolean isArmorPiercing, boolean hitArmor, boolean isKill,
                                         String victimName, int distanceMeters) {
        recordDamage(damage, isHeadshot, isCritical, isTaCZ, isArmorPiercing, hitArmor, isKill, victimName, distanceMeters, "");
    }

    public synchronized void recordDamage(float damage, boolean isHeadshot, boolean isKill) {
        recordDamage(damage, isHeadshot, false, false, false, false, isKill, "", 0, "");
    }

    public synchronized void recordKill(String victimName, int distanceMeters, boolean isHeadshot, boolean isCritical, boolean isArmorPiercing, String weaponName) {
        this.totalKills++;
        long now = System.currentTimeMillis();
        this.lastDamageTimeMs = now;
        if (distanceMeters > this.maxKillDistance) {
            this.maxKillDistance = distanceMeters;
        }
        if (distanceMeters > 0) {
            this.totalKillDistance += distanceMeters;
        }
        String validWeapon = (weaponName != null && !weaponName.isBlank()) ? weaponName : "Melee";
        this.weaponStats.computeIfAbsent(validWeapon, WeaponStatEntry::new)
                .recordKillOnly(distanceMeters, isHeadshot, isCritical, isArmorPiercing);

        String targetStr = (victimName != null && !victimName.isEmpty()) ? victimName : "Target";
        StringBuilder logMsg = new StringBuilder();
        logMsg.append("§c☠ Killed ").append(targetStr);
        if (!validWeapon.isBlank() && !validWeapon.equalsIgnoreCase("Melee")) {
            logMsg.append(" §6[").append(validWeapon).append("]");
        }
        if (distanceMeters > 0) {
            logMsg.append(" §7[").append(distanceMeters).append("m]");
        }
        if (isHeadshot) logMsg.append(" §c[HS ☠]");
        else if (isCritical) logMsg.append(" §6[Crit ★]");
        if (isArmorPiercing) logMsg.append(" §f[AP \uE002]");

        this.combatLogs.add(0, new CombatLogEntry(now, logMsg.toString(), 0.0f, true, isHeadshot, isCritical, isArmorPiercing, validWeapon));
        if (this.combatLogs.size() > MAX_LOGS) {
            this.combatLogs.remove(this.combatLogs.size() - 1);
        }
    }

    public synchronized void recordKill(String victimName, int distanceMeters, boolean isHeadshot, boolean isCritical, boolean isArmorPiercing) {
        recordKill(victimName, distanceMeters, isHeadshot, isCritical, isArmorPiercing, "");
    }

    public synchronized void recordKill(String victimName, int distanceMeters) {
        recordKill(victimName, distanceMeters, false, false, false, "");
    }

    public synchronized void recordKill() {
        recordKill("", 0, false, false, false, "");
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

    public synchronized float getAverageDPS() {
        double combatSec = Math.max(1.0, (double) this.totalCombatActiveMs / 1000.0);
        return (float) (this.totalDamage / combatSec);
    }

    public synchronized double getTotalDamage() { return totalDamage; }
    public synchronized int getTotalHits() { return totalHits; }
    public synchronized int getTotalHeadshots() { return totalHeadshots; }
    public synchronized int getTotalCriticals() { return totalCriticals; }
    public synchronized int getTotalArmorPiercing() { return totalArmorPiercing; }
    public synchronized int getTotalArmorDamage() { return totalArmorDamage; }
    public synchronized int getTotalKills() { return totalKills; }
    public synchronized float getMaxSingleDamage() { return maxSingleDamage; }
    public synchronized float getPeakDps() { return peakDps; }
    public synchronized int getMaxKillDistance() { return maxKillDistance; }

    public synchronized float getAverageKillDistance() {
        if (this.totalKills <= 0) return 0.0f;
        return (float) this.totalKillDistance / (float) this.totalKills;
    }

    public synchronized float getHeadshotRate() {
        if (this.totalHits <= 0) return 0.0f;
        return ((float) this.totalHeadshots / (float) this.totalHits) * 100.0f;
    }

    public synchronized float getCriticalRate() {
        if (this.totalHits <= 0) return 0.0f;
        return ((float) this.totalCriticals / (float) this.totalHits) * 100.0f;
    }

    public synchronized long getTotalCombatDurationMs() {
        return this.totalCombatActiveMs;
    }

    public synchronized List<CombatLogEntry> getCombatLogs() {
        return Collections.unmodifiableList(new ArrayList<>(this.combatLogs));
    }

    /**
     * 武器別統計リストを取得（総ダメージ降順）
     */
    public synchronized List<WeaponStatEntry> getWeaponBreakdownList() {
        List<WeaponStatEntry> list = new ArrayList<>(this.weaponStats.values());
        list.sort((a, b) -> Double.compare(b.getTotalDamage(), a.getTotalDamage()));
        return Collections.unmodifiableList(list);
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
     * クリップボードコピー用の整形テキストレポートを生成
     */
    public synchronized String generateStatsReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("      TaCZ Indicator - 戦闘統計レポート   \n");
        sb.append("========================================\n");
        sb.append(String.format(Locale.ROOT, "総与ダメージ: %,.1f\n", totalDamage));
        sb.append(String.format(Locale.ROOT, "瞬間DPS (直近3秒): %.1f / ピークDPS: %.1f / 平均DPS: %.1f\n", getDPS(), peakDps, getAverageDPS()));
        sb.append(String.format(Locale.ROOT, "最大単発ダメージ: %.1f\n", maxSingleDamage));
        sb.append(String.format(Locale.ROOT, "総命中数: %d 発\n", totalHits));
        sb.append(String.format(Locale.ROOT, "  - ヘッドショット: %d 発 (%.1f%%)\n", totalHeadshots, getHeadshotRate()));
        sb.append(String.format(Locale.ROOT, "  - クリティカル: %d 発 (%.1f%%)\n", totalCriticals, getCriticalRate()));
        sb.append(String.format(Locale.ROOT, "  - 防具貫通(AP): %d 発 / 防具軽減: %d 発\n", totalArmorPiercing, totalArmorDamage));
        sb.append(String.format(Locale.ROOT, "総キル数: %d 体\n", totalKills));
        sb.append(String.format(Locale.ROOT, "最長キル距離: %d m / 平均キル距離: %.1f m\n", maxKillDistance, getAverageKillDistance()));
        long sec = totalCombatActiveMs / 1000L;
        sb.append(String.format(Locale.ROOT, "実戦闘時間: %02d:%02d\n", sec / 60, sec % 60));

        List<WeaponStatEntry> weapons = getWeaponBreakdownList();
        if (!weapons.isEmpty()) {
            sb.append("----------------------------------------\n");
            sb.append("       武器別統計 (Weapon Breakdown)     \n");
            sb.append("----------------------------------------\n");
            for (WeaponStatEntry w : weapons) {
                sb.append(String.format(Locale.ROOT, "[%s] ダメージ: %,.1f | 命中: %d発 (HS: %.1f%%) | Kills: %d体 | 最長: %dm\n",
                        w.getWeaponName(), w.getTotalDamage(), w.getTotalHits(), w.getHeadshotRate(), w.getTotalKills(), w.getMaxKillDistance()));
            }
        }

        sb.append("========================================\n");
        return sb.toString();
    }

    /**
     * 統計情報をリセット (サーバー同期またはクライアント手動)
     */
    public synchronized void resetStats() {
        this.totalDamage = 0.0;
        this.totalHits = 0;
        this.totalHeadshots = 0;
        this.totalCriticals = 0;
        this.totalArmorPiercing = 0;
        this.totalArmorDamage = 0;
        this.totalKills = 0;
        this.maxSingleDamage = 0.0f;
        this.peakDps = 0.0f;
        this.maxKillDistance = 0;
        this.totalKillDistance = 0;
        this.firstCombatTimeMs = 0L;
        this.totalCombatActiveMs = 0L;
        this.lastDamageTimeMs = 0L;
        this.damageWindow.clear();
        this.combatLogs.clear();
        this.weaponStats.clear();
    }
}
