package com.kyamy.taczindicator.client.keybind;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.client.gui.IndicatorConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * ゲーム内GUI設定画面を開くキーバインド登録
 */
@Mod.EventBusSubscriber(modid = TaCZIndicatorMod.MOD_ID, value = Dist.CLIENT)
public class ModKeyBindings {

    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.taczindicator.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.taczindicator"
    );

    public static final KeyMapping OPEN_STATS_KEY = new KeyMapping(
            "key.taczindicator.open_stats",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.taczindicator"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
        event.register(OPEN_STATS_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                if (OPEN_CONFIG_KEY.consumeClick()) {
                    mc.setScreen(new IndicatorConfigScreen(null));
                } else if (OPEN_STATS_KEY.consumeClick()) {
                    mc.setScreen(new com.kyamy.taczindicator.client.gui.CombatStatsScreen(null));
                }
            }
        }
    }
}
