package com.kyamy.taczindicator;

import com.kyamy.taczindicator.config.IndicatorConfig;
import com.kyamy.taczindicator.network.ModMessages;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TaCZ Damage Indicator MODメインエントリーポイント
 */
@Mod(TaCZIndicatorMod.MOD_ID)
public class TaCZIndicatorMod {
    public static final String MOD_ID = "taczindicator";
    public static final Logger LOGGER = LogManager.getLogger();

    public TaCZIndicatorMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ライフサイクルイベントリスナー登録
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // 設定の登録
        IndicatorConfig.register();

        // Forgeメインイベントバスへの登録
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // パケットの登録
            ModMessages.register();
            LOGGER.info("TaCZ Damage Indicator: Network messages registered successfully.");
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("TaCZ Damage Indicator: Client initialized.");
    }
}
