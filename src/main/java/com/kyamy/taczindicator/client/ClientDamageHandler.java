package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.config.IndicatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * クライアント側でのダメージ検知・パケットディスパッチャ
 * サーバー側MOD未導入のマルチサーバーやクライアント単体環境でも確実に動作するクライアント監視を内蔵
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class ClientDamageHandler {

    // サーバーパケットで処理済みフラグ (重複表示防止用)
    private static final Map<Integer, Long> packetProcessedTicks = new HashMap<>();
    // 各エンティティの前回HP記録
    private static final Map<Integer, Float> lastHealthMap = new HashMap<>();
    // 直近でプレイヤーが攻撃したターゲット
    private static final Map<Integer, Long> playerAttackTargets = new HashMap<>();
    private static long clientTickCount = 0;

    /**
     * サーバーからのパケット受信ハンドラ
     */
    public static void handlePacket(int entityId, double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        packetProcessedTicks.put(entityId, clientTickCount);
        DamageIndicatorManager.getInstance().addIndicator(entityId, x, y, z, damage, isHeadshot, isCritical, isTaCZ);
    }

    /**
     * プレイヤーの直接攻撃検知
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() != null && event.getEntity().level().isClientSide()) {
            Minecraft mc = Minecraft.getInstance();
            if (event.getEntity() == mc.player && event.getTarget() != null) {
                playerAttackTargets.put(event.getTarget().getId(), clientTickCount);
            }
        }
    }

    /**
     * クリティカル攻撃検知
     */
    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity() != null && event.getEntity().level().isClientSide()) {
            Minecraft mc = Minecraft.getInstance();
            if (event.getEntity() == mc.player && event.getTarget() != null) {
                playerAttackTargets.put(event.getTarget().getId(), clientTickCount);
            }
        }
    }

    /**
     * クライアント側でのLivingEntity Tick（HP変動検知によるクライアント単体ダメージ表示）
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || !entity.level().isClientSide()) {
            return;
        }

        if (!IndicatorConfig.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;
        if (localPlayer == null || entity == localPlayer) {
            return;
        }

        int entityId = entity.getId();
        float currentHealth = entity.getHealth();
        Float prevHealth = lastHealthMap.get(entityId);

        if (prevHealth != null) {
            float delta = prevHealth - currentHealth;
            // ダメージ発生（HP減少）
            if (delta > 0.05f) {
                Long lastPacketTick = packetProcessedTicks.get(entityId);
                boolean alreadyHandledByPacket = (lastPacketTick != null && (clientTickCount - lastPacketTick) <= 2);

                if (!alreadyHandledByPacket) {
                    // プレイヤーが攻撃したか、プレイヤーが照準を合わせているか、近傍でのダメージか判定
                    Long attackTick = playerAttackTargets.get(entityId);
                    boolean isPlayerAttack = (attackTick != null && (clientTickCount - attackTick) <= 15);
                    Entity crosshairTarget = mc.crosshairPickEntity;
                    boolean isCrosshairTarget = (crosshairTarget != null && crosshairTarget.getId() == entityId);
                    double distSq = localPlayer.distanceToSqr(entity);

                    if (isPlayerAttack || isCrosshairTarget || distSq < 400.0) {
                        double posX = entity.getX();
                        double posY = entity.getEyeY();
                        double posZ = entity.getZ();

                        // クライアント側フォールバックインジケータ生成
                        DamageIndicatorManager.getInstance().addIndicator(
                                entityId,
                                posX, posY, posZ,
                                delta,
                                false,
                                false,
                                false
                        );
                    }
                }
            }
        }

        lastHealthMap.put(entityId, currentHealth);

        // クリーンアップ（マップ肥大化防止）
        if (entity.isDeadOrDying() || entity.isRemoved()) {
            lastHealthMap.remove(entityId);
            packetProcessedTicks.remove(entityId);
            playerAttackTargets.remove(entityId);
        }
    }

    public static void incrementTick() {
        clientTickCount++;
    }
}
