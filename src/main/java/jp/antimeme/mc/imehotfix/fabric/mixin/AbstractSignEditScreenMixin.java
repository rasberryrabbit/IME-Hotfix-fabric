package jp.antimeme.mc.imehotfix.fabric.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.antimeme.mc.imehotfix.core.ImeOptions;
import jp.antimeme.mc.imehotfix.core.ImePreedit;
import jp.antimeme.mc.imehotfix.core.ImeSupport;
import jp.antimeme.mc.imehotfix.core.PreeditStyle;
import jp.antimeme.mc.imehotfix.fabric.ImeClientHandler;
import jp.antimeme.mc.imehotfix.fabric.ImeTextTarget;
import jp.antimeme.mc.imehotfix.fabric.mixin.TextFieldHelperAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.joml.Vector2f;

import java.util.Objects;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen implements ImeTextTarget {
    @Unique
    private static final int IMEHOTFIX$LAST_LINE = 3;

    @Shadow
    @Final
    protected SignBlockEntity sign;
    @Shadow
    @Final
    private String[] messages;
    @Shadow
    private int line;
    @Shadow
    private TextFieldHelper signField;
    @Shadow
    private IMEPreeditOverlay preeditOverlay;

    @Unique
    private boolean imehotfix$swapped;
    @Unique
    private String imehotfix$savedLine;
    @Unique
    private int imehotfix$savedCursor;
    @Unique
    private int imehotfix$savedSelection;
    @Unique
    private int imehotfix$compositionStart;
    @Unique
    private ImePreedit imehotfix$activePreedit;

    protected AbstractSignEditScreenMixin(Component title) {
        super(title);
    }

    @Override
    public void imehotfix$insertCommitted(String text) {
        if (this.signField == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            this.signField.insertText(text.substring(i, i + 1));
        }
    }

    @Override
    public boolean imehotfix$reportsCompositionInValue() {
        return false;
    }

    @Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
    private void imehotfix$capturePreedit(PreeditEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (!ImeSupport.options().inlinePreedit) {
            return;
        }
        this.preeditOverlay = null;
        ImeClientHandler.submitPreedit(this, event);
        callback.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void imehotfix$commitBeforeClose(CallbackInfo callback) {
        this.imehotfix$restore();
        ImeClientHandler.commitCompositionInto(this);
    }

    @Redirect(method = "charTyped", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/TextFieldHelper;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z"))
    private boolean imehotfix$charTypedWithAutoWrap(TextFieldHelper helper, CharacterEvent event) {
        if (!ImeSupport.textboxOptions().signAutoWrap) {
            return helper.charTyped(event);
        }
        String before = this.messages[this.line];
        boolean handled = helper.charTyped(event);
        if (!Objects.equals(before, this.messages[this.line])) {
            return handled;
        }
        if (this.line < IMEHOTFIX$LAST_LINE) {
            this.line++;
            helper.setCursorToEnd();
            return helper.charTyped(event);
        }
        return handled;
    }

    @Inject(method = "extractSignText", at = @At("HEAD"))
    private void imehotfix$spliceComposition(GuiGraphicsExtractor graphics, Vector2f cursorOutput, CallbackInfo callback) {
        if (this.imehotfix$swapped) {
            this.imehotfix$restore();
        }
        if (this.signField == null || !ImeSupport.options().inlinePreedit) {
            return;
        }
        ImePreedit preedit = ImeSupport.preedit();
        if (preedit.isEmpty() || !ImeClientHandler.isActiveTarget(this)) {
            return;
        }
        String current = this.messages[this.line];
        if (current == null) {
            return;
        }
        TextFieldHelperAccessor caret = (TextFieldHelperAccessor) this.signField;
        int cursor = Mth.clamp(caret.imehotfix$getCursorPos(), 0, current.length());
        int selection = Mth.clamp(caret.imehotfix$getSelectionPos(), 0, current.length());
        int start = Math.min(cursor, selection);
        int end = Math.max(cursor, selection);
        this.imehotfix$savedLine = current;
        this.imehotfix$savedCursor = caret.imehotfix$getCursorPos();
        this.imehotfix$savedSelection = caret.imehotfix$getSelectionPos();
        this.imehotfix$compositionStart = start;
        this.imehotfix$activePreedit = preedit;
        this.imehotfix$swapped = true;
        this.messages[this.line] = current.substring(0, start) + preedit.text() + current.substring(end);
        int composedCaret = start + preedit.caret();
        caret.imehotfix$setCursorPos(composedCaret);
        caret.imehotfix$setSelectionPos(composedCaret);
    }

    @Inject(method = "extractSignText", at = @At("RETURN"))
    private void imehotfix$drawComposition(GuiGraphicsExtractor graphics, Vector2f cursorOutput, CallbackInfo callback) {
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
        if (this.signField == null) {
            return;
        }
        int lineHeight = this.sign.getTextLineHeight();
        int caretY = 90 + this.line * lineHeight - 2 * lineHeight;
        int caretX = this.width / 2;
        // Defensive: textInputManager() can be null during early init or teardown.
        var tm = Minecraft.getInstance().textInputManager();
        if (tm == null) {
            return;
        }
        tm.setTextInputArea(caretX, caretY, caretX + 1, caretY + lineHeight);
    }

    @Unique
    private void imehotfix$restore() {
        if (!this.imehotfix$swapped) {
            return;
        }
        this.messages[this.line] = this.imehotfix$savedLine;
        if (this.signField != null) {
            TextFieldHelperAccessor caret = (TextFieldHelperAccessor) this.signField;
            caret.imehotfix$setCursorPos(this.imehotfix$savedCursor);
            caret.imehotfix$setSelectionPos(this.imehotfix$savedSelection);
        }
        this.imehotfix$savedLine = null;
        this.imehotfix$activePreedit = null;
        this.imehotfix$swapped = false;
    }

    @Unique
    private void imehotfix$drawCompositionMarkers(GuiGraphicsExtractor graphics) {
        ImePreedit preedit = this.imehotfix$activePreedit;
        String text = this.messages[this.line];
        if (preedit == null || text == null) {
            return;
        }
        int lineHeight = this.sign.getTextLineHeight();
        int origin = -this.font.width(text) / 2;
        int top = this.line * lineHeight - 4 * lineHeight / 2;
        int underline = top + 9;
        ImeOptions options = ImeSupport.options();
        List<ImePreedit.Run> runs = preedit.runs();
        for (ImePreedit.Run run : runs) {
            int from = Mth.clamp(this.imehotfix$compositionStart + run.start(), 0, text.length());
            int to = Mth.clamp(this.imehotfix$compositionStart + run.end(), 0, text.length());
            if (from >= to) continue;
            int left = origin + this.font.width(text.substring(0, from));
            int right = origin + this.font.width(text.substring(0, to));
            if (run.style().isTarget()) {
                graphics.fill(left, top - 1, right, underline, options.targetTint);
                graphics.fill(left, underline, right, underline + 2, options.targetUnderline);
                continue;
            }
            if (run.style() == PreeditStyle.INPUT_ERROR) {
                graphics.fill(left, underline, right, underline + 1, options.errorUnderline);
                continue;
            }
            graphics.fill(left, underline, right, underline + 1, options.clauseUnderline);
        }
        this.imehotfix$drawOverflow(graphics, text, origin, top, underline);
    }

    @Unique
    private void imehotfix$drawOverflow(GuiGraphicsExtractor graphics, String text, int origin, int top, int underline) {
        ImeOptions options = ImeSupport.options();
        if (!options.highlightOverflow || this.imehotfix$activePreedit == null) {
            return;
        }
        int limit = this.font.plainSubstrByWidth(text, this.sign.getMaxTextLineWidth()).length();
        int compositionEnd = Math.min(this.imehotfix$compositionStart + this.imehotfix$activePreedit.length(), text.length());
        int from = Math.max(limit, this.imehotfix$compositionStart);
        if (from >= compositionEnd) {
            return;
        }
        int wrapEnd = Math.min(this.imehotfix$wrappableEnd(text, from), compositionEnd);
        if (wrapEnd > from) {
            graphics.fill(this.imehotfix$xOf(text, origin, from), top - 1, this.imehotfix$xOf(text, origin, wrapEnd), underline + 1, options.overflowWrapTint);
        }
        if (compositionEnd > wrapEnd) {
            graphics.fill(this.imehotfix$xOf(text, origin, wrapEnd), top - 1, this.imehotfix$xOf(text, origin, compositionEnd), underline + 1, options.overflowDropTint);
        }
    }

    @Unique
    private int imehotfix$xOf(String text, int origin, int index) {
        return origin + this.font.width(text.substring(0, index));
    }

    @Unique
    private int imehotfix$wrappableEnd(String text, int overflowStart) {
        if (!ImeSupport.textboxOptions().signAutoWrap) {
            return overflowStart;
        }
        int maxWidth = this.sign.getMaxTextLineWidth();
        int placed = overflowStart;
        for (int target = this.line + 1; target <= IMEHOTFIX$LAST_LINE && placed < text.length(); target++) {
            String existing = this.messages[target] == null ? "" : this.messages[target];
            String candidate = existing + text.substring(placed);
            int accepted = this.font.plainSubstrByWidth(candidate, maxWidth).length() - existing.length();
            if (accepted <= 0) continue;
            placed += accepted;
        }
        return placed;
    }
}
