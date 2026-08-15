package com.kyamy.taczindicator;

import com.kyamy.taczindicator.client.ClientInit;
import com.kyamy.taczindicator.config.IndicatorConfig;
import com.kyamy.taczindicator.network.ModMessages;
import com.kyamy.taczindicator.server.DamageEventHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * [TaCZ] Damage Indicator MODメインエントリーポイント
 * クライアントとサーバー（Dedicated Server）の安全な分離
 */
@Mod(TaCZIndicatorMod.MOD_ID)
public class TaCZIndicatorMod {
    public static final String MOD_ID = "taczindicator";
    public static final Logger LOGGER = LogManager.getLogger("TaCZIndicator");

    public TaCZIndicatorMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ライフサイクルイベントリスナー登録
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // 設定の登録
        IndicatorConfig.register();

        // サーバー側イベントハンドラの明示的登録
        MinecraftForge.EVENT_BUS.register(DamageEventHandler.class);

        // TaCZ互換イベントリスナーの動的初期化
        com.kyamy.taczindicator.server.TaCZCompatHandler.init();

        // クライアント専用初期化の安全な呼び出し（専用サーバー環境でのクラスロードエラー防止）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientInit::init);

        LOGGER.info("[TaCZ] Damage Indicator: Initialized and event handlers registered.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // パケットの登録
            ModMessages.register();
            LOGGER.info("[TaCZ] Damage Indicator: Network messages registered successfully.");
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[TaCZ] Damage Indicator: Client setup complete.");
    }
}
