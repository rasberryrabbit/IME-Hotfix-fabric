package jp.antimeme.mc.imehotfix.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.antimeme.mc.imehotfix.core.ImeSupport;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @Shadow
    private void refreshSearchResults() {
        throw new AssertionError("shadow");
    }

    @Inject(method = "preeditUpdated", at = @At("RETURN"))
    private void imehotfix$refreshOnComposition(PreeditEvent event, CallbackInfoReturnable<Boolean> callback) {
        // Defensive guard: refreshSearchResults() and the menu it touches expect
        // an active player. If the screen somehow receives a preedit event
        // before the player is fully set up (e.g. during disconnect, or during
        // a screen transition where the previous player is being torn down),
        // vanilla would NPE on minecraft.player.getInventory(). Skip the
        // refresh in that case — vanilla will re-trigger it on the next
        // legitimate value change anyway.
        if (!callback.getReturnValueZ()
                || !ImeSupport.options().filterWithComposition
                || Minecraft.getInstance().player == null) {
            return;
        }
        try {
            this.refreshSearchResults();
        } catch (Throwable error) {
            // Last-resort guard: never let an exception in the IME refresh
            // tear down the whole game. Log and move on.
            jp.antimeme.mc.imehotfix.fabric.ImeClientHandler.LOGGER
                    .warn("[IME Hotfix] refreshSearchResults threw during preedit; ignoring", error);
        }
    }
}
