package com.example.fakebonemeal.mixin;

import com.example.fakebonemeal.VisualState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Ikona / model itemu: zamienione itemy wygladaja jak bone meal. */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {

    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack fakebonemeal$swapModel(ItemStack stack) {
        return VisualState.modelStack(stack);
    }
}
