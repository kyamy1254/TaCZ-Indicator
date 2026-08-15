package com.kyamy.taczindicator.network;

import com.kyamy.taczindicator.client.ClientDamageHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * サーバーからクライアントへダメージ発生・キル情報を通知するネットワークパケット
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
    private final boolean isArmorPiercing;
    private final boolean hitArmor;
    private final boolean isKill;
    private final String victimName;

    public DamageIndicatorPacket(int entityId, double posX, double posY, double posZ, float damage,
                                 boolean isHeadshot, boolean isCritical, boolean isTaCZ,
                                 boolean isArmorPiercing, boolean hitArmor, boolean isKill, String victimName) {
        this.entityId = entityId;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.damage = damage;
        this.isHeadshot = isHeadshot;
        this.isCritical = isCritical;
        this.isTaCZ = isTaCZ;
        this.isArmorPiercing = isArmorPiercing;
        this.hitArmor = hitArmor;
        this.isKill = isKill;
        this.victimName = victimName != null ? victimName : "";
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
        this.isArmorPiercing = buf.readBoolean();
        this.hitArmor = buf.readBoolean();
        this.isKill = buf.readBoolean();
        this.victimName = buf.readUtf(256);
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
        buf.writeBoolean(this.isArmorPiercing);
        buf.writeBoolean(this.hitArmor);
        buf.writeBoolean(this.isKill);
        buf.writeUtf(this.victimName, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // クライアント側でのみ処理を実行
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientDamageHandler.handlePacket(
                        entityId, posX, posY, posZ, damage,
                        isHeadshot, isCritical, isTaCZ,
                        isArmorPiercing, hitArmor, isKill, victimName
                );
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
    public boolean isArmorPiercing() { return isArmorPiercing; }
    public boolean isHitArmor() { return hitArmor; }
    public boolean isKill() { return isKill; }
    public String getVictimName() { return victimName; }
}
