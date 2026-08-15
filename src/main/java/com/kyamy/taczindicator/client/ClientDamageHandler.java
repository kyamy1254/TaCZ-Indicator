package com.kyamy.taczindicator.client;

/**
 * パケット受信後のクライアント側ディスパッチャ
 */
public class ClientDamageHandler {

    public static void handlePacket(double x, double y, double z, float damage, boolean isHeadshot, boolean isCritical, boolean isTaCZ) {
        DamageIndicatorManager.getInstance().addIndicator(x, y, z, damage, isHeadshot, isCritical, isTaCZ);
    }
}
