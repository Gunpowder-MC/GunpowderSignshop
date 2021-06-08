package io.github.gunpowder.entities

import io.github.gunpowder.api.components.with
import io.github.gunpowder.commands.ConfirmCommand
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ConfirmPopup(val message: Text, val player: ServerPlayerEntity, val callback: () -> Unit) {
    fun show() {
        player.sendMessage(message, false)
        player.with<SignPlayerComponent>().confimCallback = callback
    }
}
