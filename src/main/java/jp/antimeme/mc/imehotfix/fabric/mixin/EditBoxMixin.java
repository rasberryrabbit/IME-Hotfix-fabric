package jp.antimeme.mc.imehotfix.fabric.mixin;

import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.antimeme.mc.imehotfix.core.ImeOptions;
import jp.antimeme.mc.imehotfix.core.ImePreedit;
import jp.antimeme.mc.imehotfix.core.ImeSupport;
import jp.antimeme.mc.imehotfix.core.PreeditStyle;
import jp.antimeme.mc.imehotfix.fabric.ImeClientHandler;
import jp.antimeme.mc.imehotfix.fabric.ImeTextTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.input.PreeditEvent;

@Mixin(EditBox.class)
public abstract class EditBoxMixin implements ImeTextTarget {
    @Shadow
    private String value;
    @Shadow
    private int displayPos;
    @Shadow
    private int cursorPos;
    @Shadow
    private int highlightPos;
    @Shadow
    private boolean canLoseFocus;
    @Shadow
    private Consumer<String> responder;
    @Shadow
    private IMEPreeditOverlay preeditOverlay;
    @Shadow
    private int textX;
    @Shadow
    private int textY;
    @Shadow
    @Final
    private Font font;

    @Unique
    private boolean imehotfix$swapped;
    @Unique
    private String imehotfix$savedValue;
    @Unique
    private int imehotfix$savedCursor;
    @Unique
    private int imehotfix$savedHighlight;
    @Unique
    private int imehotfix$savedDisplayPos;
    @Unique
    private int imehotfix$compositionStart;
    @Unique
    private ImePreedit imehotfix$activePreedit;

    @Shadow
    public abstract void setCursorPosition(int value);

    @Shadow
    public abstract void setHighlightPos(int value);

    @Shadow
    public abstract boolean canConsumeInput();

    @Shadow
    public abstract int getInnerWidth();

    @Shadow
    public abstract void insertText(String value);

    @Override
    public void imehotfix$insertCommitted(String text) {
        this.insertText(text);
    }

    @Override
    public boolean imehotfix$reportsCompositionInValue() {
        return true;
    }

    @Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
    private void imehotfix$capturePreedit(PreeditEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (!ImeSupport.options().inlinePreedit) {
            return;
        }
        this.preeditOverlay = null;
        ImeClientHandler.submitPreedit(this, event);
        // The responder is the lambda a screen attaches to an EditBox to be
        // notified of value changes (e.g. CreativeModeInventoryScreen uses it
        // to refresh search results). Calling it during preedit lets searches
        // react to the in-flight composition. Wrap it in a try/catch so a
        // misbehaving responder can never tear the game down — at worst the
        // search doesn't refresh until the next keystroke.
        if (ImeSupport.options().filterWithComposition && this.responder != null) {
            try {
                this.responder.accept(((EditBox) (Object) this).getValue());
            } catch (Throwable error) {
                ImeClientHandler.LOGGER
                        .warn("[IME Hotfix] EditBox responder threw during preedit; ignoring", error);
            }
        }
        callback.setReturnValue(true);
    }

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void imehotfix$reportValueWithComposition(CallbackInfoReturnable<String> callback) {
        if (this.imehotfix$swapped || !ImeSupport.options().filterWithComposition) {
            return;
        }
        if (!ImeClientHandler.isActiveTarget(this)) {
            return;
        }
        String composition = ImeSupport.preedit().text();
        if (composition.isEmpty()) {
            return;
        }
        int selectionStart = Math.min(this.cursorPos, this.highlightPos);
        int selectionEnd = Math.max(this.cursorPos, this.highlightPos);
        callback.setReturnValue(this.value.substring(0, selectionStart) + composition + this.value.substring(selectionEnd));
    }

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void imehotfix$commitBeforeBlur(boolean focused, CallbackInfo callback) {
        EditBox self = (EditBox) (Object) this;
        if (focused || !this.canLoseFocus || !self.isFocused()) {
            return;
        }
        ImeClientHandler.commitCompositionInto(this);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void imehotfix$spliceComposition(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        if (this.imehotfix$swapped) {
            this.imehotfix$restore();
        }
        if (!this.canConsumeInput() || !ImeSupport.options().inlinePreedit) {
            return;
        }
        ImePreedit preedit = ImeSupport.preedit();
        if (preedit.isEmpty() || !ImeClientHandler.isActiveTarget(this)) {
            return;
        }
        int selectionStart = Math.min(this.cursorPos, this.highlightPos);
        int selectionEnd = Math.max(this.cursorPos, this.highlightPos);
        this.imehotfix$savedValue = this.value;
        this.imehotfix$savedCursor = this.cursorPos;
        this.imehotfix$savedHighlight = this.highlightPos;
        this.imehotfix$savedDisplayPos = this.displayPos;
        this.imehotfix$compositionStart = selectionStart;
        this.imehotfix$activePreedit = preedit;
        this.imehotfix$swapped = true;
        this.value = this.imehotfix$savedValue.substring(0, selectionStart) + preedit.text() + this.imehotfix$savedValue.substring(selectionEnd);
        int caret = selectionStart + preedit.caret();
        this.setCursorPosition(caret);
        this.setHighlightPos(caret);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    private void imehotfix$drawComposition(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        try {
            if (this.imehotfix$swapped) {
                this.imehotfix$drawCompositionMarkers(graphics);
            }
            this.imehotfix$reportTextInputArea();
        } finally {
            this.imehotfix$restore();
        }
    }

    @Unique
    private void imehotfix$reportTextInputArea() {
        if (!this.canConsumeInput()) {
            return;
        }
        int caretX = this.imehotfix$xAt(this.cursorPos);
        // Defensive: textInputManager() can be null during early init or
        // teardown. Skip the cursor-rectangle update in that case rather than
        // NPE'ing.
        var tm = Minecraft.getInstance().textInputManager();
        if (tm == null) {
            return;
        }
        tm.setTextInputArea(caretX, this.textY, caretX + 1, this.textY + 9);
    }

    @Unique
    private void imehotfix$restore() {
        if (!this.imehotfix$swapped) {
            return;
        }
        this.value = this.imehotfix$savedValue;
        this.cursorPos = this.imehotfix$savedCursor;
        this.highlightPos = this.imehotfix$savedHighlight;
        this.displayPos = this.imehotfix$savedDisplayPos;
        this.imehotfix$savedValue = null;
        this.imehotfix$activePreedit = null;
        this.imehotfix$swapped = false;
    }

    @Unique
    private void imehotfix$drawCompositionMarkers(GuiGraphicsExtractor graphics) {
        ImePreedit preedit = this.imehotfix$activePreedit;
        if (preedit == null) {
            return;
        }
        int underline = this.textY + 9;
        ImeOptions options = ImeSupport.options();
        List<ImePreedit.Run> runs = preedit.runs();
        for (ImePreedit.Run run : runs) {
            int left = this.imehotfix$xAt(this.imehotfix$compositionStart + run.start());
            int right = this.imehotfix$xAt(this.imehotfix$compositionStart + run.end());
            if (right <= left) continue;
            if (run.style().isTarget()) {
                graphics.fill(left, this.textY - 1, right, underline, options.targetTint);
                graphics.fill(left, underline, right, underline + 2, options.targetUnderline);
                continue;
            }
            if (run.style() == PreeditStyle.INPUT_ERROR) {
                graphics.fill(left, underline, right, underline + 1, options.errorUnderline);
                continue;
            }
            graphics.fill(left, underline, right, underline + 1, options.clauseUnderline);
        }
    }

    @Unique
    private int imehotfix$xAt(int absoluteIndex) {
        String visible = this.imehotfix$visibleText();
        int relative = absoluteIndex - this.displayPos;
        if (relative < 0) {
            relative = 0;
        } else if (relative > visible.length()) {
            relative = visible.length();
        }
        return this.textX + this.font.width(visible.substring(0, relative));
    }

    @Unique
    private String imehotfix$visibleText() {
        int start = Math.min(this.displayPos, this.value.length());
        return this.font.plainSubstrByWidth(this.value.substring(start), this.getInnerWidth());
    }
}
