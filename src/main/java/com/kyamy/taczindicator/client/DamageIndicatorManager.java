package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.client.model.IndicatorInstance;
import com.kyamy.taczindicator.config.IndicatorConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * クライアント側で表示中のダメージインジケータ群を管理するクラス
 */
public class DamageIndicatorManager {
    private static final DamageIndicatorManager INSTANCE = new DamageIndicatorManager();
    private final List<IndicatorInstance> indicators = new ArrayList<>();
    private final Random random = new Random();

    public static DamageIndicatorManager getInstance() {
        return INSTANCE;
    }

    /**
     * 新しいダメージインジケータを追加
     */
    public synchronized void addIndicator(double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        if (!IndicatorConfig.CLIENT.enabled.get()) {
            return;
        }

        // 高レート射撃時に重なって潰れないよう微小なジッター（散乱）を付与
        double jitterX = (random.nextDouble() - 0.5) * 0.4;
        double jitterY = (random.nextDouble() - 0.5) * 0.2;
        double jitterZ = (random.nextDouble() - 0.5) * 0.4;

        IndicatorInstance instance = new IndicatorInstance(
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
