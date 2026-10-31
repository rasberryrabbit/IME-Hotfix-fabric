package jp.antimeme.mc.imehotfix.fabric.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
    @Accessor("value")
    String imehotfix$getValue();

    @Accessor("value")
    void imehotfix$setValue(String value);

    @Accessor("cursor")
    int imehotfix$getCursor();

    @Accessor("cursor")
    void imehotfix$setCursor(int value);

    @Accessor("selectCursor")
    int imehotfix$getSelectCursor();

    @Accessor("selectCursor")
    void imehotfix$setSelectCursor(int value);

    @Invoker("reflowDisplayLines")
    void imehotfix$reflow();

    @Invoker("overflowsLineLimit")
    boolean imehotfix$overflowsLineLimit(String value);

    @Accessor("width")
    int imehotfix$getWrapWidth();

    @Accessor("lineLimit")
    int imehotfix$getLineLimit();
}
