package com.kyamy.taczindicator.command;

import com.kyamy.taczindicator.network.ModMessages;
import com.kyamy.taczindicator.network.ResetCombatStatsPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;
import java.util.Collections;

/**
 * サーバーコマンドの登録およびハンドリング
 * /taczstats reset [<targets>] によるマルチサーバー戦闘統計一斉リセット
 */
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 1. /taczstats
        dispatcher.register(Commands.literal("taczstats")
                .then(Commands.literal("reset")
                        .executes(ModCommands::executeResetSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(src -> src.hasPermission(2))
                                .executes(ModCommands::executeResetTargets)))
                .then(Commands.literal("status")
                        .executes(ModCommands::executeStatus))
        );

        // 2. /taczindicator (エイリアス)
        dispatcher.register(Commands.literal("taczindicator")
                .then(Commands.literal("resetstats")
                        .executes(ModCommands::executeResetSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(src -> src.hasPermission(2))
                                .executes(ModCommands::executeResetTargets)))
        );
    }

    private static int executeResetSelf(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof ServerPlayer player) {
            ModMessages.sendToPlayer(new ResetCombatStatsPacket(true), player);
            src.sendSuccess(() -> Component.translatable("taczindicator.command.reset_self_success"), false);
        } else {
            // コンソール等の場合は全プレイヤーをリセット
            ModMessages.sendToAllPlayers(new ResetCombatStatsPacket(true));
            src.sendSuccess(() -> Component.translatable("taczindicator.command.reset_all_success"), true);
        }
        return 1;
    }

    private static int executeResetTargets(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
            for (ServerPlayer player : players) {
                ModMessages.sendToPlayer(new ResetCombatStatsPacket(true), player);
            }
            int count = players.size();
            src.sendSuccess(() -> Component.translatable("taczindicator.command.reset_targets_success", count), true);
            return count;
        } catch (Exception e) {
            src.sendFailure(Component.literal("§cエラー: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.translatable("taczindicator.status.title"), false);
        src.sendSuccess(() -> Component.translatable("taczindicator.status.mode_server_desc"), false);
        return 1;
    }
}
