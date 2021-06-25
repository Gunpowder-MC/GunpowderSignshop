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

import io.github.gunpowder.api.builders.SignType
import io.github.gunpowder.api.builders.Text
import io.github.gunpowder.api.components.with
import io.github.gunpowder.entities.ConfirmPopup
import io.github.gunpowder.entities.SignAdminBuyData
import io.github.gunpowder.entities.SignDataComponent
import io.github.gunpowder.entities.SignPlayerComponent
import io.github.gunpowder.modelhandlers.BalanceHandler
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.LiteralText
import net.minecraft.util.Formatting
import net.minecraft.util.ItemScatterer
import net.minecraft.util.collection.DefaultedList

object AdminBuySign {
    val handler by lazy {
        BalanceHandler
    }

    fun build() {
        SignType.builder {
            name("gp:adminbuy")

            requires { signBlockEntity, serverPlayerEntity -> serverPlayerEntity.hasPermissionLevel(4) }

            onClicked { signBlockEntity, serverPlayerEntity ->
                val comp = signBlockEntity.with<SignDataComponent<SignAdminBuyData>>()
                val data = comp.data ?: return@onClicked

                ConfirmPopup(Text.builder {
                    text("Buying ${data.targetStack} for $${data.price}. ")
                    text("[Confirm]") {
                        color(Formatting.GREEN)
                        onClickCommand("/gpss_confirm")
                    }
                }, serverPlayerEntity) {
                    if (handler.getUser(serverPlayerEntity.uuid).balance.toDouble() < data.price) {
                        serverPlayerEntity.sendMessage(LiteralText("Not enough money!"), false)
                    } else {
                        // Do transaction
                        handler.modifyUser(serverPlayerEntity.uuid) {
                            it.balance -= data.price.toBigDecimal()
                            it
                        }

                        val stack = data.targetStack.copy()
                        if (!serverPlayerEntity.inventory.insertStack(stack)) {
                            serverPlayerEntity.world.spawnEntity(ItemEntity(serverPlayerEntity.world, serverPlayerEntity.x, serverPlayerEntity.y, serverPlayerEntity.z, stack))
                        }
                        serverPlayerEntity.sendMessage(LiteralText("Purchased ${data.targetStack} for $${data.price}"), false)
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
                            val data = SignAdminBuyData(stack.copy(), price.first()!!)
                            comp.selected = null
                            signBlockEntity.with<SignDataComponent<SignAdminBuyData>>().data = data
                        }
                    }
                } else {
                    serverPlayerEntity.sendMessage(LiteralText("No container selected! Select a container with a golden nugget first!"), false)
                    signBlockEntity.world?.removeBlock(signBlockEntity.pos, false)
                }
            }

            onDestroyed { signBlockEntity, serverPlayerEntity ->
                signBlockEntity.with<SignDataComponent<SignAdminBuyData>>().data = null

            }

            serialize { signBlockEntity, compoundTag ->
                val comp = signBlockEntity.with<SignDataComponent<SignAdminBuyData>>()
                val data = comp.data ?: return@serialize
                val link = NbtCompound()
                val item = NbtCompound()
                data.targetStack.writeNbt(item)
                link.put("item", item)
                link.putDouble("price", data.price)
                compoundTag.put("link", link)
            }

            deserialize { signBlockEntity, compoundTag ->
                val link = compoundTag.getCompound("link")
                val comp = signBlockEntity.with<SignDataComponent<SignAdminBuyData>>()
                comp.data = SignAdminBuyData(
                    ItemStack.fromNbt(link.getCompound("item")),
                    link.getDouble("price")
                )
            }
        }
    }
}