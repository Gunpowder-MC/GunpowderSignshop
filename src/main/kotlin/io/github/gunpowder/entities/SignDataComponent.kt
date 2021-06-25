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

package io.github.gunpowder.entities

import io.github.gunpowder.api.components.Component
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.item.ItemStack
import java.util.*

interface SignData;

data class SignAdminBuyData(
    val targetStack: ItemStack,
    val price: Double
) : SignData

data class SignAdminSellData(
    val targetStack: ItemStack,
    val price: Double
) : SignData

data class SignBuyData(
    val linkedContainer: Lazy<ChestBlockEntity>,
    val ownerUUID: UUID,
    val targetStack: ItemStack,
    val price: Double
) : SignData

data class SignSellData(
    val linkedContainer: Lazy<ChestBlockEntity>,
    val ownerUUID: UUID,
    val targetStack: ItemStack,
    val price: Double
) : SignData

class SignDataComponent<T : SignData> : Component<SignBlockEntity>() {
    var data: T? = null
}