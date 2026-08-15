package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.config.IndicatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * クライアント側でのダメージ検知・パケットディスパッチャ
 * 動作モード（Server同期 vs Client単体）の自動判定とプレイヤー与ダメージ限定フィルタを完備
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class ClientDamageHandler {

    public enum OperatingMode {
        SERVER_SYNCED("§aServer同期 (SERVER_SYNCED)", "サーバーMOD稼働中・高精度ヘッドショット/クリティカルパケット受信"),
        CLIENT_STANDALONE("§eClient単体 (CLIENT_STANDALONE)", "サーバー未導入・クライアント側ローカル攻撃検知中");

        private final String displayName;
        private final String description;

        OperatingMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    private static OperatingMode currentOperatingMode = OperatingMode.CLIENT_STANDALONE;
    private static boolean serverPacketReceived = false;
    private static boolean joinMessageShown = false;

    // サーバーパケットで処理済みフラグ (重複表示防止用)
    private static final Map<Integer, Long> packetProcessedTicks = new HashMap<>();
    // 各エンティティの前回HP記録
    private static final Map<Integer, Float> lastHealthMap = new HashMap<>();
    // 直近でプレイヤーが攻撃したターゲット (entityId -> clientTick)
    private static final Map<Integer, Long> playerAttackTargets = new HashMap<>();
    // プレイヤーの直近アクション時刻 (攻撃や射撃動作)
    private static long lastPlayerActionTick = -100;
    private static long clientTickCount = 0;

    /**
     * サーバーからのパケット受信ハンドラ (Server同期モード確定)
     */
    public static void handlePacket(int entityId, double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        if (!serverPacketReceived) {
            serverPacketReceived = true;
            currentOperatingMode = OperatingMode.SERVER_SYNCED;
            TaCZIndicatorMod.LOGGER.info("TaCZ Indicator: Server packet received. Operating mode switched to SERVER_SYNCED.");
        }

        packetProcessedTicks.put(entityId, clientTickCount);
        DamageIndicatorManager.getInstance().addIndicator(entityId, x, y, z, damage, isHeadshot, isCritical, isTaCZ);
    }

    /**
     * ワールド参加・サーバー接続イベント
     */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        serverPacketReceived = false;
        joinMessageShown = false;
        currentOperatingMode = OperatingMode.CLIENT_STANDALONE;
        lastHealthMap.clear();
        packetProcessedTicks.clear();
        playerAttackTargets.clear();
    }

    /**
     * プレイヤーの直接攻撃検知
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() != null && event.getEntity().level().isClientSide()) {
            Minecraft mc = Minecraft.getInstance();
            if (event.getEntity() == mc.player && event.getTarget() != null) {
                lastPlayerActionTick = clientTickCount;
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
                lastPlayerActionTick = clientTickCount;
                playerAttackTargets.put(event.getTarget().getId(), clientTickCount);
            }
        }
    }

    /**
     * クライアント側でのLivingEntity Tick
     * サーバーMOD導入時はサーバーパケットが優先されるため、余計なクライアント重複検知は自動スキップ
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

        // ワールド参加時の案内通知（1回のみ）
        if (!joinMessageShown && clientTickCount > 40 && mc.gui != null && mc.gui.getChat() != null) {
            joinMessageShown = true;
            if (IndicatorConfig.isShowModeOnJoin()) {
                sendModeStatusMessage(localPlayer);
            }
        }

        // サーバー同期モード稼働中の場合はクライアント側のHP変動監視は行わない（サーバー側パケットが完全正確なため）
        if (serverPacketReceived) {
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
                    boolean isPlayerCaused = isPlayerDamage(localPlayer, entity, entityId, mc);

                    // onlyPlayerDamage設定がtrueの場合はプレイヤー攻撃のみに絞る
                    if (!IndicatorConfig.isOnlyPlayerDamage() || isPlayerCaused) {
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

        // クリーンアップ
        if (entity.isDeadOrDying() || entity.isRemoved()) {
            lastHealthMap.remove(entityId);
            packetProcessedTicks.remove(entityId);
            playerAttackTargets.remove(entityId);
        }
    }

    /**
     * プレイヤー自身が与えたダメージかどうかの高精度判定
     */
    private static boolean isPlayerDamage(Player localPlayer, LivingEntity entity, int entityId, Minecraft mc) {
        // 1. 直近で直接攻撃したターゲット
        Long attackTick = playerAttackTargets.get(entityId);
        if (attackTick != null && (clientTickCount - attackTick) <= 20) {
            return true;
        }

        // 2. クロスヘアで直接照準しているターゲット
        Entity crosshairTarget = mc.crosshairPickEntity;
        if (crosshairTarget != null && crosshairTarget.getId() == entityId) {
            return true;
        }

        // 3. 直近でプレイヤーが攻撃・射撃動作を行っており、視線方向の扇状範囲（64ブロック以内）にいるか
        if ((clientTickCount - lastPlayerActionTick) <= 15) {
            Vec3 lookVec = localPlayer.getViewVector(1.0f).normalize();
            Vec3 toEntity = entity.position().subtract(localPlayer.position());
            double dist = toEntity.length();
            if (dist < 64.0 && dist > 0.1) {
                double dot = lookVec.dot(toEntity.normalize());
                // 視野角約40度以内
                if (dot > 0.75) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * コマンド登録 (/taczindicator)
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("taczindicator")
                        .then(net.minecraft.commands.Commands.literal("status")
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        sendModeStatusMessage(mc.player);
                                    }
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                sendModeStatusMessage(mc.player);
                            }
                            return 1;
                        })
        );
    }

    public static void sendModeStatusMessage(Player player) {
        player.sendSystemMessage(Component.literal("§6[TaCZ Indicator] §f動作ステータス情報:"));
        player.sendSystemMessage(Component.literal("  §7- 動作モード: " + currentOperatingMode.getDisplayName()));
        player.sendSystemMessage(Component.literal("    §8" + currentOperatingMode.getDescription()));
        player.sendSystemMessage(Component.literal("  §7- 描画モード: §b" + IndicatorConfig.getRenderMode().name()));
        player.sendSystemMessage(Component.literal("  §7- 連続ダメージ: §b" + IndicatorConfig.getConsecutiveMode().name()));
        player.sendSystemMessage(Component.literal("  §7- プレイヤー限定: " + (IndicatorConfig.isOnlyPlayerDamage() ? "§a有効 (ON)" : "§c全ダメージ表示 (OFF)")));
    }

    public static OperatingMode getCurrentOperatingMode() {
        return currentOperatingMode;
    }

    public static void incrementTick() {
        clientTickCount++;
    }
}
