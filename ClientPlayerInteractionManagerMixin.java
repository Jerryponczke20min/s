package com.example.fakebonemeal.mixin;

import com.example.fakebonemeal.FakeBloom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gdy klikasz zamienionym itemem w trawe, kliknięcie jest obsluzone lokalnie i pakiet NIE trafia na serwer.
 * Dzieki temu na serwerze nic sie nie dzieje (nie postawi sie prawdziwy blok).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void fakebonemeal$interact(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<ActionResult> cir) {
        if (FakeBloom.tryUse(player, hand, hit)) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
