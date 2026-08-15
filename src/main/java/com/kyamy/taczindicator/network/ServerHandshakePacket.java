package com.kyamy.taczindicator.network;

import com.kyamy.taczindicator.client.ClientDamageHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * サーバー接続時にサーバーMODの存在をクライアントに通知する同期ハンドシェイクパケット
 */
public class ServerHandshakePacket {

    public ServerHandshakePacket() {
    }

    public ServerHandshakePacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientDamageHandler.onServerHandshakeReceived();
            });
        });
        context.setPacketHandled(true);
    }
}
