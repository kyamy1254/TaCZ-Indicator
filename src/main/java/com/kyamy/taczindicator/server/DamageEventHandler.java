package com.kyamy.taczindicator.server;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.network.DamageIndicatorPacket;
import com.kyamy.taczindicator.network.ModMessages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側でダメージイベントを検知し、攻撃元プレイヤーへインジケータ情報を送信するハンドラ
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

        // TaCZダメージ判定
        boolean isTaCZ = false;
        boolean isHeadshot = false;
        boolean isCritical = false;

        String msgId = source.getMsgId();
        if (msgId != null) {
            String lowerMsgId = msgId.toLowerCase();
            if (lowerMsgId.contains("tacz") || lowerMsgId.contains("bullet") || lowerMsgId.contains("gun") || lowerMsgId.contains("kinetic")) {
                isTaCZ = true;
            }
            if (lowerMsgId.contains("headshot") || lowerMsgId.contains("head_shot")) {
                isHeadshot = true;
                isTaCZ = true;
            }
        }

        if (directEntity != null) {
            String directClassName = directEntity.getClass().getName().toLowerCase();
            if (directClassName.contains("tacz") || directClassName.contains("bullet") || directClassName.contains("gun") || directClassName.contains("kinetic")) {
                isTaCZ = true;
            }
            if (directClassName.contains("headshot")) {
                isHeadshot = true;
                isTaCZ = true;
            }
        }

        if (!isTaCZ && attackingPlayer.fallDistance > 0.0F && !attackingPlayer.onGround() && !attackingPlayer.onClimbable() && !attackingPlayer.isInWater()) {
            isCritical = true;
        }

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
                isTaCZ
        );

        ModMessages.sendToPlayer(packet, attackingPlayer);
        TaCZIndicatorMod.LOGGER.debug("Sent damage packet: victim={}, dmg={}, player={}", victim.getId(), damage, attackingPlayer.getName().getString());
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

        // Projectile / TraceableEntity からオーナーを取得
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

        // 直近の攻撃者プレイヤー（Fallback）
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
