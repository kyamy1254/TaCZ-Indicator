package com.kyamy.taczindicator.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * サーバーからクライアントへ戦闘統計（DPS・総ダメージ・命中・キル）のリセットを同期・命令するパケット
 */
public class ResetCombatStatsPacket {
    private final boolean showNotice;

    public ResetCombatStatsPacket(boolean showNotice) {
        this.showNotice = showNotice;
    }

    public ResetCombatStatsPacket() {
        this(true);
    }

    public ResetCombatStatsPacket(FriendlyByteBuf buf) {
        this.showNotice = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.showNotice);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.kyamy.taczindicator.client.stats.CombatStatsManager.getInstance().resetStats();
                if (this.showNotice) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.translatable("taczindicator.stats.reset_notice"));
                    }
                }
            });
        });
        ctx.setPacketHandled(true);
        return true;
    }
}
