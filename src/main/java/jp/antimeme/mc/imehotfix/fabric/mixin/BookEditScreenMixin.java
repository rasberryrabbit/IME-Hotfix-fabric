package jp.antimeme.mc.imehotfix.fabric.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.antimeme.mc.imehotfix.fabric.ImeOverflowAware;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
    @Shadow
    private MultiLineEditBox page;

    @Shadow
    private void pageForward() {
        throw new AssertionError("shadow");
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void imehotfix$installOverflowHandler(CallbackInfo callback) {
        MultiLineEditBox multiLineEditBox = this.page;
        if (multiLineEditBox instanceof ImeOverflowAware overflowAware) {
            overflowAware.imehotfix$setOverflowHandler(this::pageForward);
        }
    }
}
