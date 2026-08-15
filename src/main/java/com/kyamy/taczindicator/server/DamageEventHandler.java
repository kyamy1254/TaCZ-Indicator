package com.kyamy.taczindicator.server;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.network.DamageIndicatorPacket;
import com.kyamy.taczindicator.network.ModMessages;
import com.kyamy.taczindicator.network.ServerHandshakePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * サーバー側でダメージイベントおよびキルイベントを高精度に検知し、攻撃元プレイヤーへパケット送信するハンドラ
 * TaCZイベント連携、多層リフレクション判定、および幾何学的フォールバックを完備
 */
public class DamageEventHandler {

    /**
     * プレイヤーがサーバーに参加した際に同期ハンドシェイクパケットを即時送信
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModMessages.sendToPlayer(new ServerHandshakePacket(), player);
            TaCZIndicatorMod.LOGGER.info("[TaCZ Indicator] Sent server handshake packet to player: {}", player.getName().getString());
        }
    }

    /**
     * 最終計算後のダメージイベントのみを購読 (重複・不正確な事前イベントは除外)
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        handleDamage(event.getEntity(), event.getSource(), event.getAmount());
    }

    /**
     * 確実なキル確定イベント (LivingDeathEvent)
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide() || event.getSource() == null) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        Entity directEntity = event.getSource().getDirectEntity();

        ServerPlayer attackingPlayer = resolvePlayerAttacker(victim, attacker, directEntity);
        if (attackingPlayer == null) {
            return;
        }

        Vec3 eyePos = victim.getEyePosition();
        String victimName = victim.getDisplayName().getString();

        DamageIndicatorPacket packet = new DamageIndicatorPacket(
                victim.getId(),
                eyePos.x, eyePos.y, eyePos.z,
                0.0f,
                false,
                false,
                false,
                false,
                false,
                true,
                victimName
        );

        ModMessages.sendToPlayer(packet, attackingPlayer);
        TaCZIndicatorMod.LOGGER.debug("Sent kill packet: victim={}, player={}", victimName, attackingPlayer.getName().getString());
    }

    private static void handleDamage(LivingEntity victim, DamageSource source, float damage) {
        if (victim == null || victim.level().isClientSide() || source == null || damage <= 0.001f) {
            return;
        }

        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        ServerPlayer attackingPlayer = resolvePlayerAttacker(victim, attacker, directEntity);
        if (attackingPlayer == null) {
            return;
        }

        // 1. TaCZキャッシュヒット情報の取得
        TaCZCompatHandler.TaCZHitRecord taczHit = TaCZCompatHandler.getRecentHit(victim.getId());

        // 2. TaCZダメージ判定およびヘッドショット・クリティカル判定
        boolean isTaCZ = (taczHit != null) || isTaCZDamage(source, directEntity);
        boolean isHeadshot = (taczHit != null && isHeadshotFromRecord(taczHit)) || isHeadshotDamage(victim, source, directEntity, attackingPlayer);
        boolean isCritical = isCriticalDamage(attackingPlayer, source, directEntity, isTaCZ);
        boolean isArmorPiercing = (taczHit != null && taczHit.isArmorPiercing()) || isArmorPiercingDamage(source, directEntity);
        boolean hitArmor = victim.getArmorValue() > 0;
        String victimName = victim.getDisplayName().getString();

        Vec3 eyePos = victim.getEyePosition();
        double posX = eyePos.x;
        double posY = eyePos.y;
        double posZ = eyePos.z;

        DamageIndicatorPacket packet = new DamageIndicatorPacket(
                victim.getId(),
                posX, posY, posZ,
                damage,
                isHeadshot,
                isCritical,
                isTaCZ,
                isArmorPiercing,
                hitArmor,
                false,
                victimName
        );

        ModMessages.sendToPlayer(packet, attackingPlayer);
        TaCZIndicatorMod.LOGGER.debug("Sent damage packet: victim={}, dmg={}, HS={}, Crit={}, AP={}, TaCZ={}, player={}",
                victim.getId(), damage, isHeadshot, isCritical, isArmorPiercing, isTaCZ, attackingPlayer.getName().getString());
    }

    private static boolean isHeadshotFromRecord(TaCZCompatHandler.TaCZHitRecord record) {
        return record.isHeadshot() || record.headshotMultiplier() > 1.05f;
    }

    private static boolean isArmorPiercingDamage(DamageSource source, Entity directEntity) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            return true;
        }
        if (directEntity != null) {
            if (checkBooleanFieldDeep(directEntity, "armorIgnore", "isArmorPiercing", "armorPiercing", "piercing", "bypassesArmor")) {
                return true;
            }
            if (checkBooleanGetterDeep(directEntity, "isArmorIgnore", "isArmorPiercing", "getArmorPiercing", "hasArmorIgnore")) {
                return true;
            }
        }
        if (checkBooleanFieldDeep(source, "armorIgnore", "isArmorPiercing", "armorPiercing", "piercing", "bypassesArmor")) {
            return true;
        }
        if (checkBooleanGetterDeep(source, "isArmorIgnore", "isArmorPiercing", "getArmorPiercing", "hasArmorIgnore")) {
            return true;
        }
        return false;
    }

    /**
     * TaCZ銃器ダメージかどうかの判定
     */
    private static boolean isTaCZDamage(DamageSource source, Entity directEntity) {
        String msgId = source.getMsgId();
        if (msgId != null) {
            String lower = msgId.toLowerCase();
            if (lower.contains("tacz") || lower.contains("bullet") || lower.contains("gun") || lower.contains("kinetic")) {
                return true;
            }
        }

        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            ResourceKey<DamageType> key = source.typeHolder().unwrapKey().get();
            String location = key.location().toString().toLowerCase();
            if (location.contains("tacz") || location.contains("bullet") || location.contains("gun") || location.contains("kinetic")) {
                return true;
            }
        }

        if (directEntity != null) {
            String directClassName = directEntity.getClass().getName().toLowerCase();
            if (directClassName.contains("tacz") || directClassName.contains("bullet") || directClassName.contains("gun") || directClassName.contains("kinetic")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 多層ヘッドショット判定
     * (1. DamageType/MsgId判定 -> 2. DirectEntity/Source詳細リフレクション -> 3. 着弾座標幾何フォールバック)
     */
    public static boolean isHeadshotDamage(LivingEntity victim, DamageSource source, Entity directEntity, ServerPlayer attackingPlayer) {
        // 1. DamageSourceのMsgIdおよびDamageType判定 (TaCZのtacz:bullet_headshot等)
        String msgId = source.getMsgId();
        if (msgId != null) {
            String lower = msgId.toLowerCase();
            if (lower.contains("headshot") || lower.contains("head_shot")) {
                return true;
            }
        }

        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            String location = source.typeHolder().unwrapKey().get().location().toString().toLowerCase();
            if (location.contains("headshot") || location.contains("head_shot")) {
                return true;
            }
        }

        // 2. DirectEntity (弾丸など) のヘッドショットフラグ・倍率判定
        if (directEntity != null) {
            if (checkBooleanGetterDeep(directEntity, "isHeadshot", "isHeadShot", "getHeadshot", "hasHeadshot", "isHead")) {
                return true;
            }
            if (checkBooleanFieldDeep(directEntity, "isHeadshot", "isHeadShot", "headshot", "isHead", "head_shot")) {
                return true;
            }
            if (checkMultiplierDeep(directEntity, "getHeadshotMultiplier", "headshotMultiplier", "headShotMultiplier")) {
                return true;
            }
            String directName = directEntity.getClass().getName().toLowerCase();
            if (directName.contains("headshot")) {
                return true;
            }

            // DirectEntity内部の HitResult / EntityResult / RayTraceResult フィールドを探索
            if (checkNestedResultObjects(directEntity)) {
                return true;
            }
        }

        // 3. DamageSourceのリフレクション判定
        if (checkBooleanGetterDeep(source, "isHeadshot", "isHeadShot", "getHeadshot", "isHead")) {
            return true;
        }
        if (checkBooleanFieldDeep(source, "isHeadshot", "isHeadShot", "headshot", "isHead", "head_shot")) {
            return true;
        }
        if (checkMultiplierDeep(source, "getHeadshotMultiplier", "headshotMultiplier", "headShotMultiplier")) {
            return true;
        }

        // 4. 幾何学的ヘッドショットフォールバック判定
        if (checkGeometricHeadshot(victim, source, directEntity, attackingPlayer)) {
            return true;
        }

        return false;
    }

    /**
     * 着弾座標または射線を用いた安全な幾何学的ヘッドショット判定
     */
    public static boolean checkGeometricHeadshot(LivingEntity victim, DamageSource source, Entity directEntity, ServerPlayer attackingPlayer) {
        if (victim == null) return false;

        Vec3 hitPos = null;
        if (source.getSourcePosition() != null) {
            hitPos = source.getSourcePosition();
        } else if (directEntity != null) {
            hitPos = directEntity.position();
        }

        // 着弾位置が取得できた場合
        if (hitPos != null) {
            double eyeY = victim.getEyeY();
            double victimBaseY = victim.getY();
            double victimHeight = victim.getBbHeight();
            double headThresholdY = Math.max(victimBaseY + victimHeight * 0.70, eyeY - 0.25);

            // 着弾位置のY座標が頭部領域以上かつ、水平距離がモブの当たり判定以内
            if (hitPos.y >= headThresholdY) {
                double dx = hitPos.x - victim.getX();
                double dz = hitPos.z - victim.getZ();
                double horizDistSq = dx * dx + dz * dz;
                double maxRadius = Math.max(0.5, victim.getBbWidth() * 1.5);
                if (horizDistSq <= (maxRadius * maxRadius)) {
                    TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Geometric headshot confirmed: hitY={}, thresholdY={}", hitPos.y, headThresholdY);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * クリティカル判定
     */
    private static boolean isCriticalDamage(ServerPlayer player, DamageSource source, Entity directEntity, boolean isTaCZ) {
        if (directEntity != null) {
            if (checkBooleanGetterDeep(directEntity, "isCrit", "isCritical", "getCritical", "hasCrit")) {
                return true;
            }
            if (checkBooleanFieldDeep(directEntity, "isCrit", "isCritical", "crit", "critical")) {
                return true;
            }
        }

        // バニラ近接クリティカル
        if (!isTaCZ && player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()) {
            return true;
        }

        return false;
    }

    // --- 深層リフレクション探索ユーティリティ ---

    private static boolean checkBooleanGetterDeep(Object obj, String... methodNames) {
        if (obj == null) return false;
        for (String name : methodNames) {
            try {
                Method method = obj.getClass().getMethod(name);
                Object res = method.invoke(obj);
                if (res instanceof Boolean b && b) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean checkBooleanFieldDeep(Object obj, String... fieldNames) {
        if (obj == null) return false;
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (String name : fieldNames) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    Object res = field.get(obj);
                    if (res instanceof Boolean b && b) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean checkMultiplierDeep(Object obj, String... names) {
        if (obj == null) return false;
        for (String name : names) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object res = m.invoke(obj);
                if (res instanceof Number n && n.floatValue() > 1.05f) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res instanceof Number n && n.floatValue() > 1.05f) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean checkNestedResultObjects(Object obj) {
        if (obj == null) return false;
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName().toLowerCase();
                if (fieldName.contains("result") || fieldName.contains("hit") || fieldName.contains("target")) {
                    try {
                        field.setAccessible(true);
                        Object nested = field.get(obj);
                        if (nested != null && nested != obj) {
                            if (checkBooleanFieldDeep(nested, "isHeadshot", "isHeadShot", "headshot", "isHead", "head_shot")) {
                                return true;
                            }
                            if (checkBooleanGetterDeep(nested, "isHeadshot", "isHeadShot", "getHeadshot", "hasHeadshot", "isHead")) {
                                return true;
                            }
                            if (checkMultiplierDeep(nested, "getHeadshotMultiplier", "headshotMultiplier", "headShotMultiplier")) {
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

    /**
     * DamageSource / 直接エンティティから攻撃者 ServerPlayer を高精度に解決
     */
    private static ServerPlayer resolvePlayerAttacker(LivingEntity victim, Entity attacker, Entity directEntity) {
        if (attacker instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        if (directEntity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }

        if (directEntity instanceof TraceableEntity traceable) {
            if (traceable.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }
        if (attacker instanceof TraceableEntity traceable) {
            if (traceable.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }

        if (directEntity instanceof Projectile projectile) {
            if (projectile.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }
        if (attacker instanceof Projectile projectile) {
            if (projectile.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }

        if (directEntity != null) {
            ServerPlayer shooter = tryExtractPlayer(victim, directEntity);
            if (shooter != null) {
                return shooter;
            }
        }
        if (attacker != null) {
            ServerPlayer shooter = tryExtractPlayer(victim, attacker);
            if (shooter != null) {
                return shooter;
            }
        }

        if (victim.getLastHurtByMob() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }

        return null;
    }

    private static ServerPlayer tryExtractPlayer(LivingEntity victim, Entity entity) {
        for (String methodName : new String[]{"getShooter", "getOwner", "getShootingEntity", "getThrower"}) {
            try {
                Method method = entity.getClass().getMethod(methodName);
                Object result = method.invoke(entity);
                if (result instanceof ServerPlayer sp) {
                    return sp;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
