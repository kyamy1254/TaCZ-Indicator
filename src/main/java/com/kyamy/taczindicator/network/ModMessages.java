package com.kyamy.taczindicator.network;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * ネットワークパケットの登録および送信用クラス
 */
public class ModMessages {
    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(TaCZIndicatorMod.MOD_ID, "messages"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(DamageIndicatorPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(DamageIndicatorPacket::new)
                .encoder(DamageIndicatorPacket::toBytes)
                .consumerMainThread(DamageIndicatorPacket::handle)
                .add();

        net.messageBuilder(ServerHandshakePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ServerHandshakePacket::new)
                .encoder(ServerHandshakePacket::toBytes)
                .consumerMainThread(ServerHandshakePacket::handle)
                .add();

        net.messageBuilder(ResetCombatStatsPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ResetCombatStatsPacket::new)
                .encoder(ResetCombatStatsPacket::toBytes)
                .consumerMainThread(ResetCombatStatsPacket::handle)
                .add();
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        if (INSTANCE != null && player != null) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static <MSG> void sendToAllPlayers(MSG message) {
        if (INSTANCE != null) {
            INSTANCE.send(PacketDistributor.ALL.noArg(), message);
        }
    }
}
