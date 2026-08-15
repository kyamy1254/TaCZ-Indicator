package com.kyamy.taczindicator.server;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.network.DamageIndicatorPacket;
import com.kyamy.taczindicator.network.ModMessages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * サーバー側でダメージイベントを検知し、攻撃元プレイヤーへインジケータ情報を送信するハンドラ
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID)
public class DamageEventHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) {
            return;
        }

        DamageSource source = event.getSource();
        if (source == null) {
            return;
        }

        float damage = event.getAmount();
        if (damage <= 0.001f) {
            return;
        }

        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        ServerPlayer attackingPlayer = resolvePlayerAttacker(attacker, directEntity);
        if (attackingPlayer == null) {
            return;
        }

        // TaCZダメージ判定
        boolean isTaCZ = false;
        boolean isHeadshot = false;
        boolean isCritical = false;

        String msgId = source.getMsgId();
        if (msgId != null) {
            String lowerMsgId = msgId.toLowerCase();
            if (lowerMsgId.contains("tacz") || lowerMsgId.contains("bullet") || lowerMsgId.contains("gun")) {
                isTaCZ = true;
            }
            if (lowerMsgId.contains("headshot") || lowerMsgId.contains("head_shot")) {
                isHeadshot = true;
                isTaCZ = true;
            }
        }

        // 直接エンティティ（弾丸など）のクラス名判定
        if (directEntity != null) {
            String directClassName = directEntity.getClass().getName().toLowerCase();
            if (directClassName.contains("tacz") || directClassName.contains("bullet") || directClassName.contains("gun")) {
                isTaCZ = true;
            }
            if (directClassName.contains("headshot")) {
                isHeadshot = true;
                isTaCZ = true;
            }
        }

        // バニラクリティカル判定（落下中の近接攻撃等）
        if (!isTaCZ && attackingPlayer.fallDistance > 0.0F && !attackingPlayer.onGround() && !attackingPlayer.onClimbable() && !attackingPlayer.isInWater()) {
            isCritical = true;
        }

        // ダメージ表示位置（モブの頭部〜視線位置）
        Vec3 eyePos = victim.getEyePosition();
        double posX = eyePos.x;
        double posY = eyePos.y;
        double posZ = eyePos.z;

        // パケット送信
        DamageIndicatorPacket packet = new DamageIndicatorPacket(
                victim.getId(),
                posX, posY, posZ,
                damage,
                isHeadshot,
                isCritical,
                isTaCZ
        );

        ModMessages.sendToPlayer(packet, attackingPlayer);
    }

    /**
     * DamageSource / 直接エンティティから攻撃者 ServerPlayer を解決
     */
    private static ServerPlayer resolvePlayerAttacker(Entity attacker, Entity directEntity) {
        if (attacker instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        if (directEntity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        // 弾丸などの Projectile からオーナーを取得
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
        return null;
    }
}
