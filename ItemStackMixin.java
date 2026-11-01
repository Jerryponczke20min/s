package com.example.fakebonemeal.mixin;

import com.example.fakebonemeal.VisualState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Nazwa / opis po najechaniu: "Bone Meal". */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void fakebonemeal$tooltip(Item.TooltipContext context, PlayerEntity player, TooltipType type,
                                      CallbackInfoReturnable<List<Text>> cir) {
        if (VisualState.isFake((ItemStack) (Object) this)) {
            cir.setReturnValue(new ItemStack(Items.BONE_MEAL).getTooltip(context, player, type));
        }
    }
}
