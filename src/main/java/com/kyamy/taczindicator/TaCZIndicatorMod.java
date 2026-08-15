package com.kyamy.taczindicator;

import com.kyamy.taczindicator.client.ClientDamageHandler;
import com.kyamy.taczindicator.client.DamageIndicatorHudRenderer;
import com.kyamy.taczindicator.client.DamageIndicatorRenderer;
import com.kyamy.taczindicator.client.gui.IndicatorConfigScreen;
import com.kyamy.taczindicator.client.keybind.ModKeyBindings;
import com.kyamy.taczindicator.config.IndicatorConfig;
import com.kyamy.taczindicator.network.ModMessages;
import com.kyamy.taczindicator.server.DamageEventHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
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
    public static final Logger LOGGER = LogManager.getLogger("TaCZIndicator");

    public TaCZIndicatorMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ライフサイクルイベントリスナー登録
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // 設定の登録
        IndicatorConfig.register();

        // サーバー・共通イベントハンドラの明示的登録
        MinecraftForge.EVENT_BUS.register(DamageEventHandler.class);

        // クライアント側イベントハンドラ・GUIファクトリの登録
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(ClientDamageHandler.class);
            MinecraftForge.EVENT_BUS.register(DamageIndicatorHudRenderer.class);
            MinecraftForge.EVENT_BUS.register(DamageIndicatorRenderer.class);
            MinecraftForge.EVENT_BUS.register(ModKeyBindings.class);
            modEventBus.addListener(ModKeyBindings::onRegisterKeyMappings);

            // Mod Menu / Forge Mods画面のConfigボタン連携
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new IndicatorConfigScreen(screen))
            );
        });

        LOGGER.info("TaCZ Damage Indicator: Initialized and event handlers registered.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // パケットの登録
            ModMessages.register();
            LOGGER.info("TaCZ Damage Indicator: Network messages registered successfully.");
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("TaCZ Damage Indicator: Client setup complete.");
    }
}
