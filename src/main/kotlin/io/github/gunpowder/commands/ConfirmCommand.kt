package io.github.gunpowder.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.github.gunpowder.api.builders.Command
import io.github.gunpowder.api.components.with
import io.github.gunpowder.entities.SignPlayerComponent
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText

object ConfirmCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        Command.builder(dispatcher) {
            command("gpss_confirm") {
                requires { it.player.with<SignPlayerComponent>().confimCallback != null }
                executes(::execute)
            }
        }
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val p = context.source.player
        val comp = p.with<SignPlayerComponent>()
        comp.confimCallback?.invoke() ?: context.source.sendFeedback(LiteralText("No trades pending confirmation."), false)
        comp.confimCallback = null
        return 1
    }
}
