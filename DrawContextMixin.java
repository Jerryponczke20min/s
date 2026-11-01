package com.example.fakebonemeal.mixin;

import com.example.fakebonemeal.VisualState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Licznik pod ikona: wizualnie zmniejszany przy uzyciu. */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {

    @ModifyVariable(
            method = {
                    "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
                    "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V"
            },
            at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack fakebonemeal$swapOverlay(ItemStack stack) {
        return VisualState.overlayStack(stack);
    }
}
