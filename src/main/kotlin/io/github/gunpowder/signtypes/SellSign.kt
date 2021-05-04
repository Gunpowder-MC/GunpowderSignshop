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

import io.github.gunpowder.GunpowderSignshopModule
import io.github.gunpowder.api.GunpowderMod
import io.github.gunpowder.api.builders.SignType
import io.github.gunpowder.api.builders.Text
import io.github.gunpowder.api.module.currency.modelhandlers.BalanceHandler
import io.github.gunpowder.entities.ConfirmPopup
import net.minecraft.block.entity.*
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtOps
import net.minecraft.text.LiteralText
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.ItemScatterer
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.RegistryKey
import java.util.*
import java.util.function.Predicate

object SellSign {
    val dataCache = mutableMapOf<SignBlockEntity, SignSellData>()
    val handler by lazy {
        GunpowderMod.instance.registry.getModelHandler(BalanceHandler::class.java)
    }

    fun build() {
        SignType.builder {
            name("gp:sell")

            onClicked { signBlockEntity, serverPlayerEntity ->
                val data = dataCache[signBlockEntity] ?: return@onClicked
                val container by data.linkedContainer;

                ConfirmPopup(Text.builder {
                    text("Selling ${data.targetStack} for $${data.price}. ")
                    text("[Confirm]") {
                        color(Formatting.GREEN)
                        onClickCommand("gpss_confirm")
                    }
                }, serverPlayerEntity) {
                    if (container.world?.getBlockEntity(container.pos) != container) {
                        // Container was broken
                        serverPlayerEntity.sendMessage(LiteralText("Shop no longer exists!"), false)
                        return@ConfirmPopup
                    }

                    if (handler.getUser(data.ownerUUID).balance.toDouble() < data.price) {
                        serverPlayerEntity.sendMessage(LiteralText("Shop owner does not have enough money!"), false)
                    } else {
                        val amountExtractable = Inventories.remove(serverPlayerEntity.inventory, { it.isItemEqual(data.targetStack) }, data.targetStack.count, true)
                        val inv = SimpleInventory(1)
                        inv.setStack(0, data.targetStack.copy())
                        val res = HopperBlockEntity.transfer(inv, container, data.targetStack.copy(), Direction.UP)
                        if (res.count != 0) {
                            // Not enough room
                            serverPlayerEntity.sendMessage(LiteralText("Not enough space left in shop!"), false)
                            Inventories.remove(container, { it.isItemEqual(data.targetStack) }, data.targetStack.count - res.count, false)
                        }

                        if (amountExtractable < data.targetStack.count) {
                            serverPlayerEntity.sendMessage(LiteralText("Not enough items!"), false)
                            // Undo transferring items
                            Inventories.remove(container, { it.isItemEqual(data.targetStack) }, data.targetStack.count, false)
                        } else {
                            // Do transaction
                            handler.modifyUser(serverPlayerEntity.uuid) {
                                it.balance += data.price.toBigDecimal()
                                it
                            }

                            handler.modifyUser(data.ownerUUID) {
                                it.balance -= data.price.toBigDecimal()
                                it
                            }

                            // Target container already has the items, no need to insert
                            Inventories.remove(serverPlayerEntity.inventory, { it.isItemEqual(data.targetStack) }, data.targetStack.count, false)
                            serverPlayerEntity.sendMessage(LiteralText("Sold ${data.targetStack} for $${data.price}"), false)
                        }
                    }
                }.show()
            }

            onCreated { signBlockEntity, serverPlayerEntity ->
                val lastEntity = GunpowderSignshopModule.lastClickCache[serverPlayerEntity.uuid]
                if (lastEntity != null) {
                    val stack = lastEntity.getStack(0)
                    if (stack.isEmpty) {
                        serverPlayerEntity.sendMessage(LiteralText("No item in the first slot!"), false)
                        signBlockEntity.world?.removeBlock(signBlockEntity.pos, false)
                    } else {
                        val price = signBlockEntity.text.filter { it.asString().contains("$") }.map { it.asString().replace("$", "").toDoubleOrNull() }
                        if (price.isEmpty() || price.first() == null || price.first()!! <= 0) {
                            serverPlayerEntity.sendMessage(LiteralText("No price configured on the sign. Make sure to enter '$' on the line with the price."), false)
                            signBlockEntity.world?.removeBlock(signBlockEntity.pos, false)
                        } else {
                            serverPlayerEntity.sendMessage(LiteralText("Put up for sale: $stack for $${price.first()!!}"), false)
                            val data = SignSellData(lazy { lastEntity }, serverPlayerEntity.uuid, stack.copy(), price.first()!!)
                            GunpowderSignshopModule.lastClickCache.remove(serverPlayerEntity.uuid)
                            dataCache[signBlockEntity] = data
                        }
                    }
                } else {
                    serverPlayerEntity.sendMessage(LiteralText("No container selected! Select a container with a golden nugget first!"), false)
                    signBlockEntity.world?.removeBlock(signBlockEntity.pos, false)
                }
            }

            onDestroyed { signBlockEntity, serverPlayerEntity ->
                if (dataCache.containsKey(signBlockEntity)) {
                    dataCache.remove(signBlockEntity)
                }
            }

            serialize { signBlockEntity, compoundTag ->
                val data = dataCache[signBlockEntity]  ?: return@serialize
                val link = CompoundTag()
                val item = CompoundTag()
                val container by data.linkedContainer
                data.targetStack.toTag(item)
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
                val data = SignSellData(
                    lazy { GunpowderMod.instance.server.getWorld(RegistryKey.of(Registry.DIMENSION, Identifier(link.getString("world"))))?.getBlockEntity(BlockPos(link.getInt("x"), link.getInt("y"), link.getInt("z"))) as ChestBlockEntity },
                    link.getUuid("owner"),
                    ItemStack.fromTag(link.getCompound("item")),
                    link.getDouble("price")
                )
                dataCache[signBlockEntity] = data
            }
        }
    }

    data class SignSellData(
        val linkedContainer: Lazy<ChestBlockEntity>,
        val ownerUUID: UUID,
        val targetStack: ItemStack,
        val price: Double
    )
}