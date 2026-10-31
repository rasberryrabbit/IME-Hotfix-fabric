package jp.antimeme.mc.imehotfix.fabric;

/**
 * Mixed into {@code MultiLineEditBox} so the book-edit screen can hand it a
 * callback to invoke when a typed character would overflow the current page.
 *
 * @see jp.antimeme.mc.imehotfix.fabric.mixin.MultiLineEditBoxMixin
 */
public interface ImeOverflowAware {
    void imehotfix$setOverflowHandler(Runnable handler);
}
