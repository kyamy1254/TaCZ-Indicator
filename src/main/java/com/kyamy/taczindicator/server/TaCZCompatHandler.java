package com.kyamy.taczindicator.server;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * TaCZ (Timeless and Classics Zero) との動的・高精度連携ハンドラ
 * TaCZのAPIイベント (EntityHurtByGunEvent, BulletHitEvent等) を動的に購読し、
 * クラス階層全体を走査してヘッドショット判定・防具貫通判定・銃器情報をキャッシュして提供
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

    private static final String[] TARGET_EVENT_CLASSES = new String[]{
            "com.tacz.guns.api.event.EntityHurtByGunEvent$Pre",
            "com.tacz.guns.api.event.EntityHurtByGunEvent$Post",
            "com.tacz.guns.api.event.EntityHurtByGunEvent",
            "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre",
            "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post",
            "com.tacz.guns.api.event.common.EntityHurtByGunEvent",
            "com.tacz.guns.api.event.server.EntityHurtByGunEvent$Pre",
            "com.tacz.guns.api.event.server.EntityHurtByGunEvent$Post",
            "com.tacz.guns.api.event.server.EntityHurtByGunEvent",
            "com.tacz.guns.api.event.entity.EntityHurtByGunEvent$Pre",
            "com.tacz.guns.api.event.entity.EntityHurtByGunEvent$Post",
            "com.tacz.guns.api.event.entity.EntityHurtByGunEvent",
            "com.tacz.guns.event.EntityHurtByGunEvent$Pre",
            "com.tacz.guns.event.EntityHurtByGunEvent$Post",
            "com.tacz.guns.event.EntityHurtByGunEvent",
            "com.tacz.guns.api.event.BulletHitEvent$Pre",
            "com.tacz.guns.api.event.BulletHitEvent$Post",
            "com.tacz.guns.api.event.BulletHitEvent",
            "com.tacz.guns.api.event.common.BulletHitEvent",
            "com.tacz.guns.api.event.server.BulletHitEvent"
    };

    /**
     * TaCZ連携の動的初期化 (Mod初期化時またはサーバー起動時に呼び出し)
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        int registeredCount = 0;
        for (String className : TARGET_EVENT_CLASSES) {
            if (registerEventListenerIfPresent(className)) {
                registeredCount++;
                taczLoaded = true;
            }
        }

        if (taczLoaded) {
            TaCZIndicatorMod.LOGGER.info("[TaCZ Indicator] Successfully hooked into {} TaCZ Gun Event listener(s).", registeredCount);
        } else {
            TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] TaCZ gun events not detected or could not be hooked directly (geometric raycast fallback active).");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerEventListenerIfPresent(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (net.minecraftforge.eventbus.api.Event.class.isAssignableFrom(clazz)) {
                Class<? extends net.minecraftforge.eventbus.api.Event> eventClass =
                        (Class<? extends net.minecraftforge.eventbus.api.Event>) clazz;

                Consumer consumer = (Consumer<Object>) TaCZCompatHandler::onGunHurtEvent;
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, eventClass, consumer);
                TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Registered dynamic listener for: {}", className);
                return true;
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Failed to register listener for {}: {}", className, t.getMessage());
        }
        return false;
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
            boolean isHeadshot = extractHeadshotProperty(event);
            float multiplier = extractFloatPropertyDeep(event, "headshotmultiplier", "head_shot_multiplier", "multiplier", "getmultiplier");
            if (multiplier > 1.05f) {
                isHeadshot = true;
            }

            boolean isArmorPiercing = extractBooleanPropertyDeep(event, "armorpiercing", "armor_piercing", "armorignore", "armor_ignore", "piercing", "ignorearmor");
            String gunId = extractGunIdPropertyDeep(event);

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
     * 指定エンティティの直近（800ms以内）のTaCZ銃撃ヒットレコードを取得
     */
    public static TaCZHitRecord getRecentHit(int entityId) {
        TaCZHitRecord record = RECENT_HITS.get(entityId);
        if (record != null) {
            if (System.currentTimeMillis() - record.timestampMs() <= 800) {
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

    // --- 包括的・多層リフレクション抽出ヘルパー ---

    private static LivingEntity extractVictim(Object event) {
        if (event == null) return null;

        // 1. メソッド探索 (全階層)
        Class<?> current = event.getClass();
        while (current != null && current != Object.class) {
            Method[] methods = current.getDeclaredMethods();
            for (Method m : methods) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (name.contains("victim") || name.contains("hurtentity") || name.contains("entity") || name.contains("target") || name.contains("living")) {
                    if (m.getParameterCount() == 0 && LivingEntity.class.isAssignableFrom(m.getReturnType())) {
                        try {
                            m.setAccessible(true);
                            Object res = m.invoke(event);
                            if (res instanceof LivingEntity le) return le;
                        } catch (Throwable ignored) {}
                    }
                }
            }
            current = current.getSuperclass();
        }

        // 2. フィールド探索 (全階層)
        current = event.getClass();
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field f : fields) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.contains("victim") || name.contains("hurtentity") || name.contains("entity") || name.contains("target") || name.contains("living")) {
                    if (LivingEntity.class.isAssignableFrom(f.getType())) {
                        try {
                            f.setAccessible(true);
                            Object res = f.get(event);
                            if (res instanceof LivingEntity le) return le;
                        } catch (Throwable ignored) {}
                    }
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }

    public static boolean extractHeadshotProperty(Object obj) {
        if (obj == null) return false;

        // 直接のboolean判定
        if (extractBooleanPropertyDeep(obj, "headshot", "head_shot", "ishead", "headhit")) {
            return true;
        }

        // 倍率が1.05超か検査
        float mult = extractFloatPropertyDeep(obj, "headshotmultiplier", "head_shot_multiplier", "headmultiplier", "multiplier");
        if (mult > 1.05f) {
            return true;
        }

        // ネストされた Result / Hit オブジェクトの探索
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field f : fields) {
                String fName = f.getName().toLowerCase(Locale.ROOT);
                if (fName.contains("result") || fName.contains("hit") || fName.contains("bullet") || fName.contains("data")) {
                    try {
                        f.setAccessible(true);
                        Object nested = f.get(obj);
                        if (nested != null && nested != obj) {
                            if (extractBooleanPropertyDeep(nested, "headshot", "head_shot", "ishead", "headhit")) {
                                return true;
                            }
                            if (extractFloatPropertyDeep(nested, "headshotmultiplier", "head_shot_multiplier", "headmultiplier", "multiplier") > 1.05f) {
                                return true;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
            current = current.getSuperclass();
        }

        return false;
    }

    public static boolean extractBooleanPropertyDeep(Object obj, String... keywords) {
        if (obj == null) return false;

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            // メソッド探索
            Method[] methods = current.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getParameterCount() == 0 && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    String mName = m.getName().toLowerCase(Locale.ROOT);
                    for (String kw : keywords) {
                        if (mName.contains(kw.toLowerCase(Locale.ROOT))) {
                            try {
                                m.setAccessible(true);
                                Object res = m.invoke(obj);
                                if (res instanceof Boolean b && b) return true;
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }

            // フィールド探索
            Field[] fields = current.getDeclaredFields();
            for (Field f : fields) {
                if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                    String fName = f.getName().toLowerCase(Locale.ROOT);
                    for (String kw : keywords) {
                        if (fName.contains(kw.toLowerCase(Locale.ROOT))) {
                            try {
                                f.setAccessible(true);
                                Object res = f.get(obj);
                                if (res instanceof Boolean b && b) return true;
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            current = current.getSuperclass();
        }

        return false;
    }

    public static float extractFloatPropertyDeep(Object obj, String... keywords) {
        if (obj == null) return 1.0f;

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            Method[] methods = current.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getParameterCount() == 0 && (Number.class.isAssignableFrom(m.getReturnType()) || m.getReturnType() == float.class || m.getReturnType() == double.class)) {
                    String mName = m.getName().toLowerCase(Locale.ROOT);
                    for (String kw : keywords) {
                        if (mName.contains(kw.toLowerCase(Locale.ROOT))) {
                            try {
                                m.setAccessible(true);
                                Object res = m.invoke(obj);
                                if (res instanceof Number n) return n.floatValue();
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }

            Field[] fields = current.getDeclaredFields();
            for (Field f : fields) {
                if (Number.class.isAssignableFrom(f.getType()) || f.getType() == float.class || f.getType() == double.class) {
                    String fName = f.getName().toLowerCase(Locale.ROOT);
                    for (String kw : keywords) {
                        if (fName.contains(kw.toLowerCase(Locale.ROOT))) {
                            try {
                                f.setAccessible(true);
                                Object res = f.get(obj);
                                if (res instanceof Number n) return n.floatValue();
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            current = current.getSuperclass();
        }

        return 1.0f;
    }

    public static String extractStringPropertyDeep(Object obj, String... keywords) {
        if (obj == null) return "";

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            Method[] methods = current.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                    String mName = m.getName().toLowerCase(Locale.ROOT);
                    for (String kw : keywords) {
                        if (mName.contains(kw.toLowerCase(Locale.ROOT))) {
                            try {
                                m.setAccessible(true);
                                Object res = m.invoke(obj);
                                if (res != null) return res.toString();
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }

            Field[] fields = current.getDeclaredFields();
            for (Field f : fields) {
                if (f.getType() == String.class) {
                    String fName = f.getName().toLowerCase(Locale.ROOT);
                    for (String kw : keywords) {
                        if (fName.contains(kw.toLowerCase(Locale.ROOT))) {
                            try {
                                f.setAccessible(true);
                                Object res = f.get(obj);
                                if (res != null) return res.toString();
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            current = current.getSuperclass();
        }

        return "";
    }

    /**
     * オブジェクト（イベント、エンティティ、弾丸等）から銃器ID (ResourceLocation / String 等) を深層抽出
     */
    public static String extractGunIdPropertyDeep(Object obj) {
        if (obj == null) return "";

        // 1. 直接の抽出 (メソッド・フィールド)
        String directId = extractIdentifierDeep(obj, "gunid", "gun_id", "gunname", "weaponid", "gunidentifier", "gun");
        if (!directId.isEmpty()) {
            return directId;
        }

        // 2. ネストされたオブジェクト (Bullet, HitResult, GunData, DirectEntity, etc.) の探索
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            // ネストメソッド探索
            for (Method m : current.getDeclaredMethods()) {
                if (m.getParameterCount() == 0) {
                    String mName = m.getName().toLowerCase(Locale.ROOT);
                    if (mName.contains("bullet") || mName.contains("directentity") || mName.contains("projectile") ||
                            mName.contains("gundata") || mName.contains("gunindex") || mName.contains("hitresult") ||
                            mName.contains("target") || mName.contains("gun")) {
                        try {
                            m.setAccessible(true);
                            Object nested = m.invoke(obj);
                            if (nested != null && nested != obj) {
                                String nestedId = extractIdentifierDeep(nested, "gunid", "gun_id", "gunname", "weaponid", "gunidentifier", "gun", "id");
                                if (!nestedId.isEmpty()) {
                                    return nestedId;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            // ネストフィールド探索
            for (Field f : current.getDeclaredFields()) {
                String fName = f.getName().toLowerCase(Locale.ROOT);
                if (fName.contains("bullet") || fName.contains("directentity") || fName.contains("projectile") ||
                        fName.contains("gundata") || fName.contains("gunindex") || fName.contains("hitresult") ||
                        fName.contains("gun")) {
                    try {
                        f.setAccessible(true);
                        Object nested = f.get(obj);
                        if (nested != null && nested != obj) {
                            String nestedId = extractIdentifierDeep(nested, "gunid", "gun_id", "gunname", "weaponid", "gunidentifier", "gun", "id");
                            if (!nestedId.isEmpty()) {
                                return nestedId;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }

            current = current.getSuperclass();
        }

        return "";
    }

    private static String extractIdentifierDeep(Object obj, String... keywords) {
        if (obj == null) return "";

        // obj自体がResourceLocation等の識別子型の場合
        if (isIdentifierType(obj)) {
            String str = obj.toString();
            if (isValidGunId(str)) return str;
        }

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            // メソッド探索
            for (Method m : current.getDeclaredMethods()) {
                if (m.getParameterCount() == 0) {
                    String mName = m.getName().toLowerCase(Locale.ROOT);
                    for (String kw : keywords) {
                        if (mName.contains(kw.toLowerCase(Locale.ROOT))) {
                            try {
                                m.setAccessible(true);
                                Object res = m.invoke(obj);
                                if (res != null) {
                                    String str = res.toString();
                                    if (isValidGunId(str)) {
                                        return str;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }

            // フィールド探索
            for (Field f : current.getDeclaredFields()) {
                String fName = f.getName().toLowerCase(Locale.ROOT);
                for (String kw : keywords) {
                    if (fName.contains(kw.toLowerCase(Locale.ROOT))) {
                        try {
                            f.setAccessible(true);
                            Object res = f.get(obj);
                            if (res != null) {
                                String str = res.toString();
                                if (isValidGunId(str)) {
                                    return str;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            current = current.getSuperclass();
        }

        return "";
    }

    private static boolean isIdentifierType(Object obj) {
        if (obj == null) return false;
        if (obj instanceof net.minecraft.resources.ResourceLocation) return true;
        String clsName = obj.getClass().getName();
        return clsName.contains("ResourceLocation") || clsName.contains("GunId") || clsName.contains("Identifier");
    }

    private static boolean isValidGunId(String str) {
        if (str == null || str.isBlank()) return false;
        // Object.toString() のデフォルト形式 (例: com.tacz.guns.Gun@1a2b3c) を除外
        if (str.contains("@") && str.indexOf('@') == str.lastIndexOf('@')) {
            String beforeAt = str.substring(0, str.indexOf('@'));
            if (beforeAt.contains(".") && !beforeAt.contains(":")) {
                return false;
            }
        }
        // 単なる true / false や数値単体を除外
        if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false") || str.equalsIgnoreCase("null")) {
            return false;
        }
        return true;
    }
}
