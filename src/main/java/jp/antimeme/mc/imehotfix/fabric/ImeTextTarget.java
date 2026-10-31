package jp.antimeme.mc.imehotfix.fabric;

/**
 * Marker interface implemented by every GUI object that can receive committed
 * IME text. The mixins on {@code EditBox}, {@code MultiLineEditBox} and
 * {@code AbstractSignEditScreen} all implement this so that {@link ImeClientHandler}
 * can talk to them uniformly without reflection.
 *
 * <p>The {@code imehotfix$} prefix on every method name is deliberate: it keeps
 * these mixin-injected members out of the obfuscated name space and away from
 * name clashes with vanilla methods.</p>
 */
public interface ImeTextTarget {
    void imehotfix$insertCommitted(String text);

    boolean imehotfix$reportsCompositionInValue();
}
