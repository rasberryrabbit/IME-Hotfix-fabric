package jp.antimeme.mc.imehotfix.fabric.mixin;

import net.minecraft.client.gui.font.TextFieldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextFieldHelper.class)
public interface TextFieldHelperAccessor {
    @Accessor("cursorPos")
    int imehotfix$getCursorPos();

    @Accessor("cursorPos")
    void imehotfix$setCursorPos(int value);

    @Accessor("selectionPos")
    int imehotfix$getSelectionPos();

    @Accessor("selectionPos")
    void imehotfix$setSelectionPos(int value);
}
