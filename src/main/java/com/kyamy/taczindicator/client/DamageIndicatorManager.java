package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.client.model.IndicatorInstance;
import com.kyamy.taczindicator.config.IndicatorConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * クライアント側で表示中のダメージインジケータ群を管理するクラス
 * 連続ダメージの加算・スクロール押し出しのディスパッチを担当
 */
public class DamageIndicatorManager {
    private static final DamageIndicatorManager INSTANCE = new DamageIndicatorManager();
    private final List<IndicatorInstance> indicators = new ArrayList<>();
    private final Random random = new Random();

    public static DamageIndicatorManager getInstance() {
        return INSTANCE;
    }

    /**
     * 新しいダメージインジケータを追加または連続ダメージ処理
     */
    public synchronized void addIndicator(int entityId, double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        if (!IndicatorConfig.CLIENT.isEnabled()) {
            return;
        }

        IndicatorConfig.ConsecutiveMode consecutiveMode = IndicatorConfig.CLIENT.getConsecutiveMode();
        int comboTimeout = IndicatorConfig.CLIENT.getComboTimeoutTicks();

        if (consecutiveMode == IndicatorConfig.ConsecutiveMode.ACCUMULATE) {
            // 同一エンティティへの直近ヒットを検索
            IndicatorInstance existing = findRecentIndicatorForEntity(entityId, comboTimeout);
            if (existing != null) {
                existing.accumulateDamage(damage, isHeadshot, isCritical, isTaCZ);
                existing.updatePosition(x, y, z);
                return;
            }
        } else if (consecutiveMode == IndicatorConfig.ConsecutiveMode.SCROLL_UP) {
            // 同一エンティティ（または近接）の既存インジケータを上へ押し上げ
            double spacing = IndicatorConfig.CLIENT.getScrollSpacing();
            for (IndicatorInstance ind : indicators) {
                if (ind.getEntityId() == entityId || isNearby(ind, x, y, z, 2.5)) {
                    ind.pushScrollUp(spacing);
                }
            }
        }

        // 微小なジッター（個別表示時の重なり緩和）
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
                isTaCZ
        );

        indicators.add(instance);
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
        Iterator<IndicatorInstance> iterator = indicators.iterator();
        while (iterator.hasNext()) {
            IndicatorInstance indicator = iterator.next();
            indicator.tick();
            if (indicator.isExpired()) {
                iterator.remove();
            }
        }
    }

    /**
     * 現在アクティブなインジケータ一覧を取得（描画用スレッドセーフコピー）
     */
    public synchronized List<IndicatorInstance> getActiveIndicators() {
        return new ArrayList<>(indicators);
    }

    /**
     * 全消去（ワールド離脱時など）
     */
    public synchronized void clear() {
        indicators.clear();
    }
}
