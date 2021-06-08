package io.github.gunpowder.entities

import io.github.gunpowder.api.components.Component
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.server.network.ServerPlayerEntity

class SignPlayerComponent : Component<ServerPlayerEntity>() {
    var selected: ChestBlockEntity? = null
    var confimCallback: (() -> Unit)? = null
}