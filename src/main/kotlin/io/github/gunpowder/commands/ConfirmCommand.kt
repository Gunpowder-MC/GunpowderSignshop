package io.github.gunpowder.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.github.gunpowder.api.builders.Command
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText

object ConfirmCommand {
    private val waiting = mutableMapOf<ServerPlayerEntity, ()->Unit>()

    fun set(player: ServerPlayerEntity, callback: ()->Unit) {
        waiting[player] = callback
    }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        Command.builder(dispatcher) {
            command("gpss_confirm") {
                requires { it.player in waiting }
                executes(::execute)
            }
        }
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val p = context.source.player
        waiting[p]?.let { it() } ?: context.source.sendFeedback(LiteralText("No trades pending confirmation."), false)
        waiting.remove(p)
        return 1
    }
}
