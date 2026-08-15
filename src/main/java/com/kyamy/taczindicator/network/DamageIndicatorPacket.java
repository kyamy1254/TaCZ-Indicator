package com.kyamy.taczindicator.network;

import com.kyamy.taczindicator.client.ClientDamageHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * サーバーからクライアントへダメージ発生情報を通知するネットワークパケット
 */
public class DamageIndicatorPacket {
    private final int entityId;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final float damage;
    private final boolean isHeadshot;
    private final boolean isCritical;
    private final boolean isTaCZ;

    public DamageIndicatorPacket(int entityId, double posX, double posY, double posZ, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        this.entityId = entityId;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.damage = damage;
        this.isHeadshot = isHeadshot;
        this.isCritical = isCritical;
        this.isTaCZ = isTaCZ;
    }

    public DamageIndicatorPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.posX = buf.readDouble();
        this.posY = buf.readDouble();
        this.posZ = buf.readDouble();
        this.damage = buf.readFloat();
        this.isHeadshot = buf.readBoolean();
        this.isCritical = buf.readBoolean();
        this.isTaCZ = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeDouble(this.posX);
        buf.writeDouble(this.posY);
        buf.writeDouble(this.posZ);
        buf.writeFloat(this.damage);
        buf.writeBoolean(this.isHeadshot);
        buf.writeBoolean(this.isCritical);
        buf.writeBoolean(this.isTaCZ);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // クライアント側でのみ処理を実行
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientDamageHandler.handlePacket(entityId, posX, posY, posZ, damage, isHeadshot, isCritical, isTaCZ);
            });
        });
        context.setPacketHandled(true);
    }

    public int getEntityId() { return entityId; }
    public double getPosX() { return posX; }
    public double getPosY() { return posY; }
    public double getPosZ() { return posZ; }
    public float getDamage() { return damage; }
    public boolean isHeadshot() { return isHeadshot; }
    public boolean isCritical() { return isCritical; }
    public boolean isTaCZ() { return isTaCZ; }
}
