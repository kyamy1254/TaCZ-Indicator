package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.client.model.IndicatorInstance;
import com.kyamy.taczindicator.client.model.KillAlertInstance;
import com.kyamy.taczindicator.config.IndicatorConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * クライアント側で表示中のダメージインジケータおよびキル通知群を管理するクラス
 * スクロール上限数管理、同種モブ連続キル時の置換更新を完備
 */
public class DamageIndicatorManager {
    private static final DamageIndicatorManager INSTANCE = new DamageIndicatorManager();
    private final List<IndicatorInstance> indicators = new ArrayList<>();
    private final List<KillAlertInstance> killAlerts = new ArrayList<>();
    private final Random random = new Random();

    public static DamageIndicatorManager getInstance() {
        return INSTANCE;
    }

    /**
     * 新しいダメージインジケータを追加または連続ダメージ処理
     */
    public synchronized void addIndicator(int entityId, double x, double y, double z, float damage,
                                          boolean isHeadshot, boolean isCritical, boolean isTaCZ,
                                          boolean isArmorPiercing, boolean hitArmor) {
        if (!IndicatorConfig.isEnabled()) {
            return;
        }

        if (IndicatorConfig.isOnlyTaczDamage() && !isTaCZ) {
            return;
        }

        IndicatorConfig.ConsecutiveMode consecutiveMode = IndicatorConfig.getConsecutiveMode();
        int comboTimeout = IndicatorConfig.getComboTimeoutTicks();

        if (consecutiveMode == IndicatorConfig.ConsecutiveMode.ACCUMULATE) {
            IndicatorInstance existing = findRecentIndicatorForEntity(entityId, comboTimeout);
            if (existing != null) {
                existing.accumulateDamage(damage, isHeadshot, isCritical, isTaCZ, isArmorPiercing, hitArmor);
                existing.updatePosition(x, y, z);
                return;
            }
        } else if (consecutiveMode == IndicatorConfig.ConsecutiveMode.SCROLL_UP) {
            double spacing = IndicatorConfig.getScrollSpacing();
            for (IndicatorInstance ind : indicators) {
                if (ind.getEntityId() == entityId || isNearby(ind, x, y, z, 2.5)) {
                    ind.pushScrollUp(spacing);
                }
            }

            // スクロール上限数 (maxScrolledIndicators) を超える古いインジケータを自動消去
            int maxScrolled = IndicatorConfig.getMaxScrolledIndicators();
            while (indicators.size() >= maxScrolled && !indicators.isEmpty()) {
                indicators.remove(0);
            }
        }

        double jitterX = 0.0;
        double jitterY = 0.0;
        double jitterZ = 0.0;
        if (consecutiveMode == IndicatorConfig.ConsecutiveMode.OFF) {
            jitterX = (random.nextDouble() - 0.5) * 0.3;
            jitterY = (random.nextDouble() - 0.5) * 0.15;
            jitterZ = (random.nextDouble() - 0.5) * 0.3;
        }

        IndicatorInstance instance = new IndicatorInstance(
                entityId,
                x + jitterX,
                y + jitterY,
                z + jitterZ,
                damage,
                isHeadshot,
                isCritical,
                isTaCZ,
                isArmorPiercing,
                hitArmor
        );

        indicators.add(instance);
    }

    public synchronized void addIndicator(int entityId, double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        addIndicator(entityId, x, y, z, damage, isHeadshot, isCritical, isTaCZ, false, false);
    }

    /**
     * キル確定演出（Kill Alert）の追加・更新
     * 同種モブの連続キル時は最新の距離とカウントでその場置換更新
     */
    public synchronized void addKillAlert(String victimName, int distanceMeters, String weaponName) {
        if (!IndicatorConfig.isEnabled() || !IndicatorConfig.isShowKillAlert()) {
            return;
        }
        if (victimName == null || victimName.isBlank()) {
            victimName = "Enemy";
        }

        // 同種モブの直近キル通知がある場合は最新距離・カウント・武器で置換更新
        for (KillAlertInstance alert : killAlerts) {
            if (!alert.isExpired() && alert.getVictimName().equals(victimName)) {
                alert.updateKill(distanceMeters, weaponName);
                return;
            }
        }

        killAlerts.add(new KillAlertInstance(victimName, distanceMeters, weaponName));
    }

    public synchronized void addKillAlert(String victimName, int distanceMeters) {
        addKillAlert(victimName, distanceMeters, "");
    }

    public synchronized void addKillAlert(String victimName) {
        addKillAlert(victimName, 0, "");
    }

    private IndicatorInstance findRecentIndicatorForEntity(int entityId, int maxAge) {
        for (int i = indicators.size() - 1; i >= 0; i--) {
            IndicatorInstance ind = indicators.get(i);
            if (ind.getEntityId() == entityId && !ind.isExpired() && ind.getAgeTicks() <= maxAge) {
                return ind;
            }
        }
        return null;
    }

    private boolean isNearby(IndicatorInstance ind, double x, double y, double z, double maxDist) {
        double dx = ind.getX() - x;
        double dy = ind.getY() - y;
        double dz = ind.getZ() - z;
        return (dx * dx + dy * dy + dz * dz) <= (maxDist * maxDist);
    }

    /**
     * クライアントTick更新
     */
    public synchronized void tick() {
        Iterator<IndicatorInstance> indIter = indicators.iterator();
        while (indIter.hasNext()) {
            IndicatorInstance indicator = indIter.next();
            indicator.tick();
            if (indicator.isExpired()) {
                indIter.remove();
            }
        }

        Iterator<KillAlertInstance> alertIter = killAlerts.iterator();
        while (alertIter.hasNext()) {
            KillAlertInstance alert = alertIter.next();
            alert.tick();
            if (alert.isExpired()) {
                alertIter.remove();
            }
        }
    }

    public synchronized List<IndicatorInstance> getActiveIndicators() {
        return new ArrayList<>(indicators);
    }

    public synchronized List<KillAlertInstance> getActiveKillAlerts() {
        return new ArrayList<>(killAlerts);
    }

    public synchronized void clear() {
        indicators.clear();
        killAlerts.clear();
    }
}
