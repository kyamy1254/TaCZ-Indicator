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
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * サーバー側でダメージイベントおよびキルイベントを高精度に検知し、攻撃元プレイヤーへパケット送信するハンドラ
 * TaCZイベント連携、3D Ray-Box幾何学レイキャスト、多層リフレクション判定を完備
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
            if (TaCZCompatHandler.extractBooleanPropertyDeep(directEntity, "armorpiercing", "armor_piercing", "armorignore", "armor_ignore", "piercing", "bypassesarmor")) {
                return true;
            }
        }
        if (TaCZCompatHandler.extractBooleanPropertyDeep(source, "armorpiercing", "armor_piercing", "armorignore", "armor_ignore", "piercing", "bypassesarmor")) {
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
            String lower = msgId.toLowerCase(Locale.ROOT);
            if (lower.contains("tacz") || lower.contains("bullet") || lower.contains("gun") || lower.contains("kinetic")) {
                return true;
            }
        }

        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            ResourceKey<DamageType> key = source.typeHolder().unwrapKey().get();
            String location = key.location().toString().toLowerCase(Locale.ROOT);
            if (location.contains("tacz") || location.contains("bullet") || location.contains("gun") || location.contains("kinetic")) {
                return true;
            }
        }

        if (directEntity != null) {
            String directClassName = directEntity.getClass().getName().toLowerCase(Locale.ROOT);
            if (directClassName.contains("tacz") || directClassName.contains("bullet") || directClassName.contains("gun") || directClassName.contains("kinetic")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 多層高精度ヘッドショット判定
     * (1. DamageType/MsgId判定 -> 2. 弾丸/DamageSource詳細リフレクション -> 3. 3D Ray-AABB幾何学レイキャスト判定)
     */
    public static boolean isHeadshotDamage(LivingEntity victim, DamageSource source, Entity directEntity, ServerPlayer attackingPlayer) {
        if (victim == null) return false;

        // 1. DamageSourceのMsgIdおよびDamageType判定
        String msgId = source.getMsgId();
        if (msgId != null) {
            String lower = msgId.toLowerCase(Locale.ROOT);
            if (lower.contains("headshot") || lower.contains("head_shot")) {
                return true;
            }
        }

        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            String location = source.typeHolder().unwrapKey().get().location().toString().toLowerCase(Locale.ROOT);
            if (location.contains("headshot") || location.contains("head_shot")) {
                return true;
            }
        }

        // 2. DirectEntity (弾丸など) のヘッドショットプロパティ判定
        if (directEntity != null) {
            if (TaCZCompatHandler.extractHeadshotProperty(directEntity)) {
                return true;
            }
        }

        // 3. DamageSourceのリフレクション判定
        if (TaCZCompatHandler.extractHeadshotProperty(source)) {
            return true;
        }

        // 4. 幾何学的3D Ray-Box交差レイキャスト判定 (確実なフォールバック)
        if (checkGeometricHeadshot(victim, source, directEntity, attackingPlayer)) {
            return true;
        }

        return false;
    }

    /**
     * 攻撃者視線ベクトルおよび弾丸軌道を用いた高精度3D Ray-AABBヘッドショット判定
     */
    public static boolean checkGeometricHeadshot(LivingEntity victim, DamageSource source, Entity directEntity, ServerPlayer attackingPlayer) {
        if (victim == null) return false;

        // 対象エンティティの頭部バウンディングボックス (AABB) の精密構築
        AABB headBox = calculateEntityHeadBox(victim);

        // 1. 攻撃元プレイヤーの3D視線レイキャスト判定
        if (attackingPlayer != null) {
            Vec3 eyePos = attackingPlayer.getEyePosition(1.0f);
            Vec3 lookVec = attackingPlayer.getViewVector(1.0f).normalize();
            Vec3 rayEnd = eyePos.add(lookVec.scale(300.0));

            Optional<Vec3> headHit = headBox.clip(eyePos, rayEnd);
            if (headHit.isPresent()) {
                TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Headshot confirmed by player eye raycast: victim={}", victim.getId());
                return true;
            }
        }

        // 2. 弾丸（DirectEntity）の移動ベクトル軌道交差判定
        if (directEntity != null) {
            Vec3 bulletPos = directEntity.position();
            Vec3 bulletVel = directEntity.getDeltaMovement();
            double speed = bulletVel.length();
            double lookback = Math.max(1.5, speed * 2.5);

            Vec3 rayStart = bulletPos.subtract(bulletVel.normalize().scale(lookback));
            Vec3 rayEnd = bulletPos.add(bulletVel.normalize().scale(lookback));

            Optional<Vec3> bulletHit = headBox.clip(rayStart, rayEnd);
            if (bulletHit.isPresent()) {
                TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Headshot confirmed by bullet trajectory: victim={}", victim.getId());
                return true;
            }

            // 弾丸の現在位置自体が頭部領域内にある場合
            if (headBox.inflate(0.15).contains(bulletPos)) {
                return true;
            }
        }

        // 3. エンダードラゴン等マルチパートエンティティ対応
        if (victim instanceof EnderDragon dragon) {
            if (dragon.head != null) {
                AABB dragonHeadBox = dragon.head.getBoundingBox().inflate(0.2);
                if (attackingPlayer != null) {
                    Vec3 eyePos = attackingPlayer.getEyePosition(1.0f);
                    Vec3 lookVec = attackingPlayer.getViewVector(1.0f).normalize();
                    if (dragonHeadBox.clip(eyePos, eyePos.add(lookVec.scale(300.0))).isPresent()) {
                        return true;
                    }
                }
            }
        }

        // 4. 着弾座標が直接渡されている場合の検証
        if (source != null && source.getSourcePosition() != null) {
            Vec3 srcPos = source.getSourcePosition();
            if (headBox.inflate(0.25).contains(srcPos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * エンティティの頭部当たり判定AABBを算出
     */
    public static AABB calculateEntityHeadBox(LivingEntity victim) {
        double victimY = victim.getY();
        double victimHeight = victim.getBbHeight();
        double eyeY = victim.getEyeY();

        // 頭部の下限Y座標 (全高の上位30%または目線位置-0.3m)
        double headMinY = Math.max(victimY + victimHeight * 0.68, eyeY - 0.35);
        // 頭部の上限Y座標
        double headMaxY = victimY + victimHeight + 0.25;

        // 水平当たり判定幅 (若干の射撃許容マージン +0.15m)
        double halfWidth = Math.max(0.35, (victim.getBbWidth() / 2.0) + 0.15);

        return new AABB(
                victim.getX() - halfWidth,
                headMinY,
                victim.getZ() - halfWidth,
                victim.getX() + halfWidth,
                headMaxY,
                victim.getZ() + halfWidth
        );
    }

    /**
     * クリティカル判定
     */
    private static boolean isCriticalDamage(ServerPlayer player, DamageSource source, Entity directEntity, boolean isTaCZ) {
        if (directEntity != null) {
            if (TaCZCompatHandler.extractBooleanPropertyDeep(directEntity, "crit", "critical")) {
                return true;
            }
        }

        // バニラ近接クリティカル
        if (!isTaCZ && player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()) {
            return true;
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
            ServerPlayer shooter = tryExtractPlayer(directEntity);
            if (shooter != null) {
                return shooter;
            }
        }
        if (attacker != null) {
            ServerPlayer shooter = tryExtractPlayer(attacker);
            if (shooter != null) {
                return shooter;
            }
        }

        if (victim.getLastHurtByMob() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }

        return null;
    }

    private static ServerPlayer tryExtractPlayer(Entity entity) {
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
