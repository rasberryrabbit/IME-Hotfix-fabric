package jp.antimeme.mc.imehotfix.core;

/**
 * IME backend that does not own the composition itself: it only stores the
 * preedit the game reports through {@code PreeditEvent} and feeds it back to
 * whoever asks. The Windows native backend ({@code Win32ImeBackend}) is used
 * on Windows when the OS-side composition needs to be intercepted; on every
 * other platform this lightweight backend is what the mod falls back to.
 */
public final class ExternalImeBackend implements ImeBackend {
    private volatile boolean attached;
    private volatile boolean inputActive;
    private volatile ImePreedit preedit = ImePreedit.EMPTY;

    public void submitPreedit(ImePreedit composition) {
        this.preedit = composition == null ? ImePreedit.EMPTY : composition;
    }

    @Override
    public boolean attach(long nativeWindowHandle) {
        this.attached = true;
        return true;
    }

    @Override
    public void detach() {
        this.attached = false;
        this.preedit = ImePreedit.EMPTY;
    }

    @Override
    public boolean isAttached() {
        return this.attached;
    }

    @Override
    public void setInputActive(boolean active) {
        this.inputActive = active;
        if (!active) {
            this.preedit = ImePreedit.EMPTY;
        }
    }

    @Override
    public boolean isInputActive() {
        return this.inputActive;
    }

    @Override
    public ImePreedit preedit() {
        return this.preedit;
    }

    @Override
    public boolean isComposing() {
        return !this.preedit.isEmpty();
    }

    @Override
    public void setCaretRect(int x, int y, int width, int height) {
    }

    @Override
    public void setExclusionRect(int x, int y, int width, int height) {
    }

    @Override
    public void cancelComposition() {
        this.preedit = ImePreedit.EMPTY;
    }

    @Override
    public String describe() {
        return "game-provided (GLFW input method)";
    }
}
