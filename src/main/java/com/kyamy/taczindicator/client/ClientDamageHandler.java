package com.kyamy.taczindicator.client;

/**
 * パケット受信後のクライアント側ディスパッチャ
 */
public class ClientDamageHandler {

    public static void handlePacket(int entityId, double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        DamageIndicatorManager.getInstance().addIndicator(entityId, x, y, z, damage, isHeadshot, isCritical, isTaCZ);
    }
}
