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

package io.github.gunpowder.signtypes

import io.github.gunpowder.api.GunpowderMod
import io.github.gunpowder.api.builders.SignType
import io.github.gunpowder.api.builders.Text
import io.github.gunpowder.api.components.with
import io.github.gunpowder.api.ext.getPresentPermission
import io.github.gunpowder.entities.ConfirmPopup
import io.github.gunpowder.entities.SignBuyData
import io.github.gunpowder.entities.SignDataComponent
import io.github.gunpowder.entities.SignPlayerComponent
import io.github.gunpowder.modelhandlers.BalanceHandler
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventories
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.LiteralText
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.RegistryKey

object BuySign {
    val handler by lazy {
        BalanceHandler
    }

    fun build() {
        SignType.builder {
            name("gp:buy")

            requires { signBlockEntity, serverPlayerEntity -> serverPlayerEntity.getPresentPermission("signshop.buy", false, serverPlayerEntity.hasPermissionLevel(0)) }

            onClicked { signBlockEntity, serverPlayerEntity ->
                val comp = signBlockEntity.with<SignDataComponent<SignBuyData>>()
                val data = comp.data ?: return@onClicked

                val container by data.linkedContainer;
                if (container.isRemoved) {
                    comp.data = null
                    return@onClicked
                }

                ConfirmPopup(Text.builder {
                    text("Buying ${data.targetStack} for $${data.price}. ")
                    text("[Confirm]") {
                        color(Formatting.GREEN)
                        onClickCommand("/gpss_confirm")
                    }
                }, serverPlayerEntity) {
                    if (container.world?.getBlockEntity(container.pos) != container) {
                        // Container was broken
                        serverPlayerEntity.sendMessage(LiteralText("Shop no longer exists!"), false)
                        return@ConfirmPopup
                    }

                    if (handler.getUser(serverPlayerEntity.uuid).balance.toDouble() < data.price) {
                        serverPlayerEntity.sendMessage(LiteralText("Not enough money!"), false)
                    } else {
                        val amountExtractable = Inventories.remove(container, { ItemStack.canCombine(it, data.targetStack) }, data.targetStack.count, true)

                        if (amountExtractable < data.targetStack.count) {
                            serverPlayerEntity.sendMessage(LiteralText("Shop not stocked"), false)
                        } else {
                            // Do transaction
                            handler.modifyUser(serverPlayerEntity.uuid) {
                                it.balance -= data.price.toBigDecimal()
                                it
                            }

                            handler.modifyUser(data.ownerUUID) {
                                it.balance += data.price.toBigDecimal()
                                it
                            }

                            Inventories.remove(container, { ItemStack.canCombine(it, data.targetStack) }, data.targetStack.count, false)
                            val stack = data.targetStack.copy()
                            if (!serverPlayerEntity.inventory.insertStack(stack)) {
                                serverPlayerEntity.world.spawnEntity(ItemEntity(serverPlayerEntity.world, serverPlayerEntity.x, serverPlayerEntity.y, serverPlayerEntity.z, stack))
                            }
                            serverPlayerEntity.sendMessage(LiteralText("Purchased ${data.targetStack} for $${data.price}"), false)
                        }
                    }
                }.show()
            }

            onCreated { signBlockEntity, serverPlayerEntity ->
                val comp = serverPlayerEntity.with<SignPlayerComponent>()
                val lastEntity = comp.selected
                if (lastEntity != null) {
                    val stack = lastEntity.getStack(0)
                    if (stack.isEmpty) {
                        serverPlayerEntity.sendMessage(LiteralText("No item in the first slot!"), false)
                        signBlockEntity.world?.removeBlock(signBlockEntity.pos, false)
                    } else {
                        val price = signBlockEntity.texts.filter { it.asString().contains("$") }.map { it.asString().replace("$", "").toDoubleOrNull() }
                        if (price.isEmpty() || price.first() == null || price.first()!! <= 0) {
                            serverPlayerEntity.sendMessage(LiteralText("No price configured on the sign. Make sure to enter '$' on the line with the price."), false)
                            signBlockEntity.world?.removeBlock(signBlockEntity.pos, false)
                        } else {
                            serverPlayerEntity.sendMessage(LiteralText("Put up for sale: $stack for $${price.first()!!}"), false)
                            val data = SignBuyData(lazy { lastEntity }, serverPlayerEntity.uuid, stack.copy(), price.first()!!)
                            comp.selected = null
                            signBlockEntity.with<SignDataComponent<SignBuyData>>().data = data
                        }
                    }
                } else {
                    serverPlayerEntity.sendMessage(LiteralText("No container selected! Select a container with a golden nugget first!"), false)
                    signBlockEntity.world?.removeBlock(signBlockEntity.pos, false)
                }
            }

            onDestroyed { signBlockEntity, serverPlayerEntity ->
                signBlockEntity.with<SignDataComponent<SignBuyData>>().data = null
            }

            serialize { signBlockEntity, compoundTag ->
                val comp = signBlockEntity.with<SignDataComponent<SignBuyData>>()
                val data = comp.data ?: return@serialize
                val link = NbtCompound()
                val item = NbtCompound()
                val container by data.linkedContainer
                data.targetStack.writeNbt(item)
                link.put("item", item)
                link.putUuid("owner", data.ownerUUID)
                link.putDouble("price", data.price)
                link.putString("world", signBlockEntity.world!!.registryKey.value.toString())
                link.putInt("x", container.pos.x)
                link.putInt("y", container.pos.y)
                link.putInt("z", container.pos.z)
                compoundTag.put("link", link)
            }

            deserialize { signBlockEntity, compoundTag ->
                val link = compoundTag.getCompound("link")
                val comp = signBlockEntity.with<SignDataComponent<SignBuyData>>()
                comp.data = SignBuyData(
                        lazy { GunpowderMod.instance.server.getWorld(RegistryKey.of(Registry.WORLD_KEY, Identifier(link.getString("world"))))?.getBlockEntity(BlockPos(link.getInt("x"), link.getInt("y"), link.getInt("z"))) as ChestBlockEntity },
                        link.getUuid("owner"),
                        ItemStack.fromNbt(link.getCompound("item")),
                        link.getDouble("price")
                )
            }
        }
    }
}