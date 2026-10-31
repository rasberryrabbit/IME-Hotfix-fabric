package jp.antimeme.mc.imehotfix.fabric.mixin;

import java.util.ArrayList;
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
import jp.antimeme.mc.imehotfix.fabric.ImeClientHandler;
import jp.antimeme.mc.imehotfix.fabric.ImeOverflowAware;
import jp.antimeme.mc.imehotfix.fabric.ImeTextTarget;
import jp.antimeme.mc.imehotfix.fabric.mixin.MultilineTextFieldAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Style;

@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin implements ImeTextTarget, ImeOverflowAware {
    @Unique
    private static final int IMEHOTFIX$LINE_HEIGHT = 9;
    @Unique
    private static final int IMEHOTFIX$MAX_PAGE_CHARS = 1024;
    @Unique
    private static final int IMEHOTFIX$INNER_PADDING = 4;

    @Shadow
    @Final
    private MultilineTextField textField;
    @Shadow
    @Final
    private Font font;
    @Shadow
    private IMEPreeditOverlay preeditOverlay;

    @Unique
    private boolean imehotfix$swapped;
    @Unique
    private String imehotfix$savedValue;
    @Unique
    private int imehotfix$savedCursor;
    @Unique
    private int imehotfix$savedSelectCursor;
    @Unique
    private int imehotfix$compositionStart;
    @Unique
    private ImePreedit imehotfix$activePreedit;
    @Unique
    private Runnable imehotfix$overflowHandler;
    @Unique
    private int imehotfix$lastCaretX = Integer.MIN_VALUE;
    @Unique
    private int imehotfix$lastCaretY = Integer.MIN_VALUE;

    @Shadow
    public abstract String getValue();

    @Override
    public void imehotfix$insertCommitted(String text) {
        for (int i = 0; i < text.length(); i++) {
            this.textField.insertText(text.substring(i, i + 1));
        }
    }

    @Override
    public void imehotfix$setOverflowHandler(Runnable handler) {
        this.imehotfix$overflowHandler = handler;
    }

    @Redirect(method = "charTyped", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/MultilineTextField;insertText(Ljava/lang/String;)V"))
    private void imehotfix$insertWithOverflow(MultilineTextField field, String text) {
        String before = field.value();
        field.insertText(text);
        if (this.imehotfix$overflowHandler == null || !ImeSupport.textboxOptions().bookAutoPage || !before.equals(field.value())) {
            return;
        }
        this.imehotfix$overflowHandler.run();
        field.insertText(text);
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

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void imehotfix$commitBeforeBlur(boolean focused, CallbackInfo callback) {
        if (focused || !((MultiLineEditBox) (Object) this).isFocused()) {
            return;
        }
        ImeClientHandler.commitCompositionInto(this);
    }

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void imehotfix$spliceComposition(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        if (this.imehotfix$swapped) {
            this.imehotfix$restore();
        }
        if (!ImeSupport.options().inlinePreedit) {
            return;
        }
        ImePreedit preedit = ImeSupport.preedit();
        if (preedit.isEmpty() || !ImeClientHandler.isActiveTarget(this)) {
            return;
        }
        MultilineTextFieldAccessor field = (MultilineTextFieldAccessor) this.textField;
        String current = field.imehotfix$getValue();
        if (current == null) {
            return;
        }
        int cursor = Math.clamp(field.imehotfix$getCursor(), 0, current.length());
        int select = Math.clamp(field.imehotfix$getSelectCursor(), 0, current.length());
        int start = Math.min(cursor, select);
        int end = Math.max(cursor, select);
        this.imehotfix$savedValue = current;
        this.imehotfix$savedCursor = field.imehotfix$getCursor();
        this.imehotfix$savedSelectCursor = field.imehotfix$getSelectCursor();
        this.imehotfix$compositionStart = start;
        this.imehotfix$activePreedit = preedit;
        this.imehotfix$swapped = true;
        String composed = current.substring(0, start) + preedit.text() + current.substring(end);
        int composedCaret = start + preedit.caret();
        field.imehotfix$setValue(composed);
        field.imehotfix$setCursor(composedCaret);
        field.imehotfix$setSelectCursor(composedCaret);
        field.imehotfix$reflow();
        this.imehotfix$reportTextInputArea();
    }

    @Inject(method = "extractContents", at = @At("RETURN"))
    private void imehotfix$restoreAfterRender(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        try {
            if (this.imehotfix$swapped) {
                this.imehotfix$drawCompositionMarkers(graphics);
            }
        } finally {
            this.imehotfix$restore();
        }
    }

    @Unique
    private void imehotfix$restore() {
        if (!this.imehotfix$swapped) {
            return;
        }
        this.imehotfix$swapped = false;
        MultilineTextFieldAccessor field = (MultilineTextFieldAccessor) this.textField;
        field.imehotfix$setValue(this.imehotfix$savedValue);
        field.imehotfix$setCursor(this.imehotfix$savedCursor);
        field.imehotfix$setSelectCursor(this.imehotfix$savedSelectCursor);
        field.imehotfix$reflow();
        this.imehotfix$savedValue = null;
        this.imehotfix$activePreedit = null;
    }

    @Unique
    private void imehotfix$drawCompositionMarkers(GuiGraphicsExtractor graphics) {
        ImePreedit preedit = this.imehotfix$activePreedit;
        if (preedit == null) {
            return;
        }
        MultilineTextFieldAccessor field = (MultilineTextFieldAccessor) this.textField;
        String text = field.imehotfix$getValue();
        if (text == null || text.isEmpty()) {
            return;
        }
        Font font = this.font;
        List<int[]> lines = this.imehotfix$splitLines(font, text, field.imehotfix$getWrapWidth());
        int compositionStart = this.imehotfix$compositionStart;
        int compositionEnd = Math.min(compositionStart + preedit.length(), text.length());
        int overflowStart = this.imehotfix$overflowStart(text, lines, field.imehotfix$getLineLimit());
        ImeOptions options = ImeSupport.options();
        List<ImePreedit.Run> runs = preedit.runs();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            int lineStart = lines.get(lineIndex)[0];
            int lineEnd = lines.get(lineIndex)[1];
            int y = this.imehotfix$innerTop() + lineIndex * IMEHOTFIX$LINE_HEIGHT;
            for (ImePreedit.Run run : runs) {
                int from = Math.max(compositionStart + run.start(), lineStart);
                int to = Math.min(Math.min(compositionStart + run.end(), compositionEnd), lineEnd);
                if (from >= to) continue;
                int left = this.imehotfix$innerLeft() + font.width(text.substring(lineStart, from));
                int right = this.imehotfix$innerLeft() + font.width(text.substring(lineStart, to));
                if (run.style().isTarget()) {
                    graphics.fill(left, y - 1, right, y + 8, options.targetTint);
                    graphics.fill(left, y + 8, right, y + 10, options.targetUnderline);
                    continue;
                }
                graphics.fill(left, y + 8, right, y + 9, options.clauseUnderline);
            }
            if (!options.highlightOverflow || overflowStart < 0) continue;
            int from = Math.max(Math.max(overflowStart, compositionStart), lineStart);
            int to = Math.min(compositionEnd, lineEnd);
            if (from >= to) continue;
            int left = this.imehotfix$innerLeft() + font.width(text.substring(lineStart, from));
            int right = this.imehotfix$innerLeft() + font.width(text.substring(lineStart, to));
            boolean wraps = ImeSupport.textboxOptions().bookAutoPage;
            graphics.fill(left, y - 1, right, y + 9, wraps ? options.overflowWrapTint : options.overflowDropTint);
        }
    }

    @Unique
    private int imehotfix$innerLeft() {
        return ((MultiLineEditBox) (Object) this).getX() + IMEHOTFIX$INNER_PADDING;
    }

    @Unique
    private int imehotfix$innerTop() {
        return ((MultiLineEditBox) (Object) this).getY() + IMEHOTFIX$INNER_PADDING;
    }

    @Unique
    private List<int[]> imehotfix$splitLines(Font font, String text, int wrapWidth) {
        ArrayList<int[]> lines = new ArrayList<>();
        font.getSplitter().splitLines(text, wrapWidth, Style.EMPTY, false,
                (style, start, end) -> lines.add(new int[]{start, end}));
        return lines;
    }

    @Unique
    private int imehotfix$overflowStart(String text, List<int[]> lines, int lineLimit) {
        int overflow = -1;
        if (lineLimit > 0 && lines.size() > lineLimit) {
            overflow = lines.get(lineLimit)[0];
        }
        if (text.length() >= IMEHOTFIX$MAX_PAGE_CHARS) {
            int byLength = IMEHOTFIX$MAX_PAGE_CHARS - 1;
            overflow = overflow < 0 ? byLength : Math.min(overflow, byLength);
        }
        return overflow;
    }

    @Unique
    private void imehotfix$reportTextInputArea() {
        int caretX = this.imehotfix$innerLeft();
        int caretY = this.imehotfix$innerTop() + this.textField.getLineAtCursor() * IMEHOTFIX$LINE_HEIGHT;
        if (caretX == this.imehotfix$lastCaretX && caretY == this.imehotfix$lastCaretY) {
            return;
        }
        this.imehotfix$lastCaretX = caretX;
        this.imehotfix$lastCaretY = caretY;
        // Defensive: textInputManager() can be null during early init or teardown.
        var tm = Minecraft.getInstance().textInputManager();
        if (tm == null) {
            return;
        }
        tm.setTextInputArea(caretX, caretY, caretX + 1, caretY + IMEHOTFIX$LINE_HEIGHT);
    }
}
