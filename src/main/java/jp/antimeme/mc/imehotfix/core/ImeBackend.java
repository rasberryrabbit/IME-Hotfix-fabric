package jp.antimeme.mc.imehotfix.core;

public interface ImeBackend {
    boolean attach(long nativeWindowHandle);

    void detach();

    boolean isAttached();

    void setInputActive(boolean active);

    boolean isInputActive();

    ImePreedit preedit();

    boolean isComposing();

    void setCaretRect(int x, int y, int width, int height);

    void setExclusionRect(int x, int y, int width, int height);

    void cancelComposition();

    String describe();
}
