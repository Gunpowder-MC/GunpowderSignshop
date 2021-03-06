/*
 * MIT License
 *
 * Copyright (c) 2020 GunpowderMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.gunpowder

import io.github.gunpowder.api.GunpowderMod
import io.github.gunpowder.api.GunpowderModule
import io.github.gunpowder.api.components.Component
import io.github.gunpowder.api.components.bind
//import io.github.gunpowder.api.components.bind
import io.github.gunpowder.api.components.injected
import io.github.gunpowder.api.components.with
import io.github.gunpowder.commands.ConfirmCommand
import io.github.gunpowder.entities.SignDataComponent
import io.github.gunpowder.entities.SignPlayerComponent
import io.github.gunpowder.signtypes.AdminBuySign
import io.github.gunpowder.signtypes.AdminSellSign
import io.github.gunpowder.signtypes.BuySign
import io.github.gunpowder.signtypes.SellSign
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import kotlin.reflect.KClass

class GunpowderSignshopModule : GunpowderModule {
    override val name = "signshop"
    override val toggleable = true
    val gunpowder: GunpowderMod
        get() = GunpowderMod.instance

    override fun registerEvents() {
        UseItemCallback.EVENT.register { playerEntity, world, hand ->
            if (world.isClient()) {
                return@register TypedActionResult.pass(playerEntity.getStackInHand(hand))
            }

            val stack = playerEntity.getStackInHand(hand)
            if (stack.item == Items.GOLD_NUGGET) {
                val hit = playerEntity.raycast(5.0, 0.0f, false) ?: return@register TypedActionResult.pass(stack)
                val p = BlockPos(hit.pos)
                val state = world.getBlockState(p)
                if (state.block is ChestBlock) {
                    playerEntity.with<SignPlayerComponent>().selected = world.getBlockEntity(p) as ChestBlockEntity
                    playerEntity.sendMessage(LiteralText("Marked container at [${p.x}, ${p.y}, ${p.z}]"), false)
                    TypedActionResult.success(stack)
                }
            }
            TypedActionResult.pass(stack)
        }
    }


    override fun registerComponents() {
        ServerPlayerEntity::class.bind<SignPlayerComponent>()
        SignBlockEntity::class.bind<SignDataComponent<*>>()
    }

    override fun registerCommands() {
        gunpowder.registry.registerCommand(ConfirmCommand::register)
    }

    override fun onInitialize() {
        BuySign.build()
        SellSign.build()
        AdminBuySign.build()
        AdminSellSign.build()
    }
}
