package com.kyamy.taczindicator.server;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.network.DamageIndicatorPacket;
import com.kyamy.taczindicator.network.ModMessages;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側でダメージイベントを検知し、攻撃元プレイヤーへインジケータ情報を送信するハンドラ
 * TaCZ銃器のヘッドショットおよびクリティカルを高精度に判定
 */
public class DamageEventHandler {

    // 同一Tick内の重複送信防止
    private static final Map<Integer, Long> lastHandledTick = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        handleDamage(event.getEntity(), event.getSource(), event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        handleDamage(event.getEntity(), event.getSource(), event.getAmount());
    }

    private static void handleDamage(LivingEntity victim, DamageSource source, float damage) {
        if (victim == null || victim.level().isClientSide() || source == null || damage <= 0.001f) {
            return;
        }

        long currentTick = victim.level().getGameTime();
        Long lastTick = lastHandledTick.get(victim.getId());
        if (lastTick != null && lastTick == currentTick) {
            return;
        }

        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        ServerPlayer attackingPlayer = resolvePlayerAttacker(victim, attacker, directEntity);
        if (attackingPlayer == null) {
            return;
        }

        lastHandledTick.put(victim.getId(), currentTick);

        // TaCZダメージ判定およびヘッドショット・クリティカル判定
        boolean isTaCZ = isTaCZDamage(source, directEntity);
        boolean isHeadshot = isHeadshotDamage(victim, source, directEntity);
        boolean isCritical = isCriticalDamage(attackingPlayer, source, directEntity, isTaCZ);
        boolean isArmorPiercing = isArmorPiercingDamage(source, directEntity);
        boolean hitArmor = victim.getArmorValue() > 0;
        boolean isKill = (victim.getHealth() - damage <= 0.001f) || victim.isDeadOrDying();
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
                isKill,
                victimName
        );

        ModMessages.sendToPlayer(packet, attackingPlayer);
        TaCZIndicatorMod.LOGGER.debug("Sent damage packet: victim={}, dmg={}, HS={}, Crit={}, AP={}, Kill={}, player={}",
                victim.getId(), damage, isHeadshot, isCritical, isArmorPiercing, isKill, attackingPlayer.getName().getString());
    }

    private static boolean isArmorPiercingDamage(DamageSource source, Entity directEntity) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            return true;
        }
        if (directEntity != null) {
            if (checkBooleanField(directEntity, "armorIgnore", "isArmorPiercing", "armorPiercing", "piercing")) {
                return true;
            }
            if (checkBooleanGetter(directEntity, "isArmorIgnore", "isArmorPiercing", "getArmorPiercing", "hasArmorIgnore")) {
                return true;
            }
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
     * ヘッドショット判定 (DamageType、リフレクション、弾丸エンティティ、着弾幾何位置の総合判定)
     */
    private static boolean isHeadshotDamage(LivingEntity victim, DamageSource source, Entity directEntity) {
        // 1. DamageSourceのMsgIdおよびDamageType判定
        String msgId = source.getMsgId();
        if (msgId != null) {
            String lower = msgId.toLowerCase();
            if (lower.contains("headshot") || lower.contains("head_shot") || lower.contains("head")) {
                return true;
            }
        }

        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            String location = source.typeHolder().unwrapKey().get().location().toString().toLowerCase();
            if (location.contains("headshot") || location.contains("head_shot") || location.contains("head")) {
                return true;
            }
        }

        // 2. DirectEntity (弾丸など) のリフレクション判定
        if (directEntity != null) {
            if (checkBooleanGetter(directEntity, "isHeadshot", "isHeadShot", "getHeadshot", "hasHeadshot", "isHead")) {
                return true;
            }
            if (checkBooleanField(directEntity, "isHeadshot", "isHeadShot", "headshot", "isHead")) {
                return true;
            }
            String directName = directEntity.getClass().getName().toLowerCase();
            if (directName.contains("headshot")) {
                return true;
            }
        }

        // 3. DamageSourceのリフレクション判定
        if (checkBooleanGetter(source, "isHeadshot", "isHeadShot", "getHeadshot", "isHead")) {
            return true;
        }

        // 4. 弾丸着弾位置と被弾モブの頭部（EyeHeight）の幾何学的フォールバック判定
        double eyeY = victim.getEyeY();
        if (directEntity != null) {
            double bulletY = directEntity.getY();
            // 弾丸のY座標がモブの目線付近（目線-0.3ブロック以上）であればヘッドショット
            if (bulletY >= eyeY - 0.35) {
                return true;
            }
        }

        Vec3 srcPos = source.getSourcePosition();
        if (srcPos != null && srcPos.y >= eyeY - 0.35) {
            return true;
        }

        return false;
    }

    /**
     * クリティカル判定
     */
    private static boolean isCriticalDamage(ServerPlayer player, DamageSource source, Entity directEntity, boolean isTaCZ) {
        if (directEntity != null) {
            if (checkBooleanGetter(directEntity, "isCrit", "isCritical", "getCritical", "hasCrit")) {
                return true;
            }
            if (checkBooleanField(directEntity, "isCrit", "isCritical", "crit", "critical")) {
                return true;
            }
        }

        // バニラ近接クリティカル
        if (!isTaCZ && player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()) {
            return true;
        }

        return false;
    }

    private static boolean checkBooleanGetter(Object obj, String... methodNames) {
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

    private static boolean checkBooleanField(Object obj, String... fieldNames) {
        for (String name : fieldNames) {
            try {
                Field field = obj.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object res = field.get(obj);
                if (res instanceof Boolean b && b) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
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

        // TaCZ Bullet Entity (getShooter / getOwner メソッドのリフレクション解決)
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
