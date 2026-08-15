package com.kyamy.taczindicator.client;

import com.kyamy.taczindicator.client.gui.IndicatorConfigScreen;
import com.kyamy.taczindicator.client.keybind.ModKeyBindings;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * クライアント専用の初期化クラス
 * 専用サーバー（Dedicated Server）でクライアントクラスが誤ロードされるのを完全に防止
 */
public class ClientInit {

    public static void init() {
        // クライアント側イベントハンドラの登録
        MinecraftForge.EVENT_BUS.register(ClientDamageHandler.class);
        MinecraftForge.EVENT_BUS.register(DamageIndicatorHudRenderer.class);
        MinecraftForge.EVENT_BUS.register(DamageIndicatorRenderer.class);
        MinecraftForge.EVENT_BUS.register(ModKeyBindings.class);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ModKeyBindings::onRegisterKeyMappings);

        // Mod Menu / Forge Mods画面のConfigボタン連携
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new IndicatorConfigScreen(screen))
        );
    }
}
