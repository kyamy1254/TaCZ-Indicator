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

        // TaCZダメージ判定およびヘッドショット・クリティカル判定
        boolean isTaCZ = isTaCZDamage(source, directEntity);
        boolean isHeadshot = isHeadshotDamage(source, directEntity);
        boolean isCritical = isCriticalDamage(attackingPlayer, source, directEntity, isTaCZ);
        boolean isArmorPiercing = isArmorPiercingDamage(source, directEntity);
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
        TaCZIndicatorMod.LOGGER.debug("Sent damage packet: victim={}, dmg={}, HS={}, Crit={}, AP={}, player={}",
                victim.getId(), damage, isHeadshot, isCritical, isArmorPiercing, attackingPlayer.getName().getString());
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
     * 正確なヘッドショット判定 (DamageType、TaCZ専用フラグ、リフレクションによる厳密判定)
     * 高低差による誤判定を招く幾何学的推定は完全に撤廃
     */
    private static boolean isHeadshotDamage(DamageSource source, Entity directEntity) {
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

        // 2. DirectEntity (弾丸など) のヘッドショットフラグ判定
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
