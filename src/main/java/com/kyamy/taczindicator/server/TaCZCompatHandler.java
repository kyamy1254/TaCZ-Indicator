package com.kyamy.taczindicator.server;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * TaCZ (Timeless and Classics Zero) との動的・安全な連携ハンドラ
 * TaCZのAPIイベント (EntityHurtByGunEvent等) を動的に購読し、
 * 高精度なヘッドショット判定・防具貫通判定・銃器情報をキャッシュして提供
 */
public class TaCZCompatHandler {

    public record TaCZHitRecord(
            int victimId,
            long timestampMs,
            boolean isHeadshot,
            boolean isArmorPiercing,
            float headshotMultiplier,
            String gunId
    ) {}

    private static final Map<Integer, TaCZHitRecord> RECENT_HITS = new ConcurrentHashMap<>();
    private static boolean initialized = false;
    private static boolean taczLoaded = false;

    /**
     * TaCZ連携の動的初期化 (Mod初期化時またはサーバー起動時に呼び出し)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            // 1. EntityHurtByGunEvent.Post の探索と登録
            registerEventListenerIfPresent("com.tacz.guns.api.event.EntityHurtByGunEvent$Post");
            // 2. EntityHurtByGunEvent.Pre の探索と登録
            registerEventListenerIfPresent("com.tacz.guns.api.event.EntityHurtByGunEvent$Pre");
            // 3. 基本イベントクラスの探索
            registerEventListenerIfPresent("com.tacz.guns.api.event.EntityHurtByGunEvent");

            taczLoaded = true;
            TaCZIndicatorMod.LOGGER.info("[TaCZ Indicator] Successfully hooked into TaCZ Gun Events for high-precision hit detection.");
        } catch (Throwable t) {
            TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] TaCZ gun events not detected or could not be hooked: {}", t.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerEventListenerIfPresent(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (net.minecraftforge.eventbus.api.Event.class.isAssignableFrom(clazz)) {
                Class<? extends net.minecraftforge.eventbus.api.Event> eventClass =
                        (Class<? extends net.minecraftforge.eventbus.api.Event>) clazz;

                Consumer consumer = (Consumer<Object>) TaCZCompatHandler::onGunHurtEvent;
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, eventClass, consumer);
                TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Registered dynamic listener for: {}", className);
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Failed to register listener for {}: {}", className, t.getMessage());
        }
    }

    /**
     * TaCZの銃撃イベントを受信した際の処理
     */
    private static void onGunHurtEvent(Object event) {
        if (event == null) return;

        try {
            LivingEntity victim = extractVictim(event);
            if (victim == null) return;

            int victimId = victim.getId();
            boolean isHeadshot = extractBooleanProperty(event, "isHeadshot", "isHeadShot", "headshot", "hasHeadshot", "isHead");
            float multiplier = extractFloatProperty(event, "getHeadshotMultiplier", "headshotMultiplier", "getMultiplier");
            if (multiplier > 1.05f) {
                isHeadshot = true;
            }

            boolean isArmorPiercing = extractBooleanProperty(event, "isArmorPiercing", "isArmorIgnore", "armorPiercing", "armorIgnore");
            String gunId = extractStringProperty(event, "getGunId", "gunId");

            TaCZHitRecord record = new TaCZHitRecord(
                    victimId,
                    System.currentTimeMillis(),
                    isHeadshot,
                    isArmorPiercing,
                    multiplier,
                    gunId
            );

            RECENT_HITS.put(victimId, record);
            TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Captured TaCZ gun hit: victim={}, HS={}, AP={}, mult={}, gun={}",
                    victimId, isHeadshot, isArmorPiercing, multiplier, gunId);

        } catch (Throwable t) {
            TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Error processing TaCZ gun event: {}", t.getMessage());
        }
    }

    /**
     * 指定エンティティの直近（500ms以内）のTaCZ銃撃ヒットレコードを取得
     */
    public static TaCZHitRecord getRecentHit(int entityId) {
        TaCZHitRecord record = RECENT_HITS.get(entityId);
        if (record != null) {
            if (System.currentTimeMillis() - record.timestampMs() <= 500) {
                return record;
            } else {
                RECENT_HITS.remove(entityId);
            }
        }
        return null;
    }

    /**
     * レコードのクリーンアップ
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        RECENT_HITS.entrySet().removeIf(entry -> (now - entry.getValue().timestampMs()) > 2000);
    }

    public static boolean isTaCZLoaded() {
        return taczLoaded;
    }

    // --- リフレクション抽出ヘルパー ---

    private static LivingEntity extractVictim(Object event) {
        for (String method : new String[]{"getHurtEntity", "getEntity", "getTarget", "getLivingEntity", "getVictim"}) {
            try {
                Method m = event.getClass().getMethod(method);
                Object res = m.invoke(event);
                if (res instanceof LivingEntity le) return le;
            } catch (Throwable ignored) {}
        }
        for (String fieldName : new String[]{"hurtEntity", "entity", "target", "victim", "livingEntity"}) {
            try {
                Field f = event.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object res = f.get(event);
                if (res instanceof LivingEntity le) return le;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean extractBooleanProperty(Object obj, String... names) {
        if (obj == null) return false;

        // ゲッターメソッド
        for (String name : names) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object res = m.invoke(obj);
                if (res instanceof Boolean b && b) return true;
            } catch (Throwable ignored) {}
        }

        // フィールド
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res instanceof Boolean b && b) return true;
                } catch (Throwable ignored) {}
            }
            current = current.getSuperclass();
        }

        return false;
    }

    public static float extractFloatProperty(Object obj, String... names) {
        if (obj == null) return 1.0f;

        for (String name : names) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object res = m.invoke(obj);
                if (res instanceof Number n) return n.floatValue();
            } catch (Throwable ignored) {}
        }

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res instanceof Number n) return n.floatValue();
                } catch (Throwable ignored) {}
            }
            current = current.getSuperclass();
        }

        return 1.0f;
    }

    public static String extractStringProperty(Object obj, String... names) {
        if (obj == null) return "";

        for (String name : names) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object res = m.invoke(obj);
                if (res != null) return res.toString();
            } catch (Throwable ignored) {}
        }

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res != null) return res.toString();
                } catch (Throwable ignored) {}
            }
            current = current.getSuperclass();
        }

        return "";
    }
}
