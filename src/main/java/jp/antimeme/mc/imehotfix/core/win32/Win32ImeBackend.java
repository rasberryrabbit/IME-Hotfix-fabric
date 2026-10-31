package jp.antimeme.mc.imehotfix.core.win32;

import com.sun.jna.Memory;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import java.util.Objects;
import jp.antimeme.mc.imehotfix.core.ImeBackend;
import jp.antimeme.mc.imehotfix.core.ImeOptions;
import jp.antimeme.mc.imehotfix.core.ImePreedit;
import jp.antimeme.mc.imehotfix.core.ImeSupport;

public final class Win32ImeBackend implements ImeBackend {
    private WinDef.HWND hwnd;
    private Pointer previousWndProc;
    private WinUser.WindowProc hook;
    private WinNT.HANDLE parkedContext;
    private volatile boolean attached;
    private volatile boolean inputActive;
    private volatile boolean composing;
    private boolean applyingCaret;
    private volatile ImePreedit preedit = ImePreedit.EMPTY;
    private volatile int caretX;
    private volatile int caretY;
    private volatile int caretWidth = 1;
    private volatile int caretHeight = 12;
    private volatile int exclusionX;
    private volatile int exclusionY;
    private volatile int exclusionWidth;
    private volatile int exclusionHeight;

    @Override
    public synchronized boolean attach(long nativeWindowHandle) {
        if (this.attached) {
            return true;
        }
        if (!Platform.isWindows()) {
            ImeSupport.logger().warn("Win32 IME backend requested on a non-Windows platform", null);
            return false;
        }
        if (!Platform.is64Bit()) {
            ImeSupport.logger().warn("Win32 IME backend needs a 64-bit JVM; staying inactive", null);
            return false;
        }
        this.hwnd = new WinDef.HWND(new Pointer(nativeWindowHandle));
        this.hook = new WinUser.WindowProc() {
            @Override
            public WinDef.LRESULT callback(WinDef.HWND wnd, int msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
                return Win32ImeBackend.this.windowProc(wnd, msg, wParam, lParam);
            }
        };
        Pointer previous = User32Ex.INSTANCE.SetWindowLongPtrW(this.hwnd, WinIme.GWLP_WNDPROC, this.hook);
        if (previous == null) {
            ImeSupport.logger().warn("SetWindowLongPtrW did not return the previous window procedure; refusing to hook", null);
            this.hook = null;
            this.hwnd = null;
            return false;
        }
        this.previousWndProc = previous;
        this.attached = true;
        this.inputActive = true;
        setInputActive(false);
        return true;
    }

    @Override
    public synchronized void detach() {
        if (!this.attached) {
            return;
        }
        this.attached = false;
        this.restoreInputContext();
        if (this.previousWndProc != null && this.hwnd != null) {
            User32Ex.INSTANCE.SetWindowLongPtrW(this.hwnd, WinIme.GWLP_WNDPROC, this.previousWndProc);
        }
        this.previousWndProc = null;
        this.hook = null;
        this.hwnd = null;
        this.parkedContext = null;
        this.composing = false;
        this.preedit = ImePreedit.EMPTY;
        this.inputActive = false;
    }

    @Override
    public boolean isAttached() {
        return this.attached;
    }

    @Override
    public synchronized void setInputActive(boolean active) {
        if (!this.attached || active == this.inputActive) {
            return;
        }
        this.inputActive = active;
        if (!active && this.composing && options().cancelCompositionOnFocusLoss) {
            cancelComposition();
        }
        if (!options().disableImeOutsideTextFields) {
            return;
        }
        if (active) {
            this.restoreInputContext();
        } else {
            WinNT.HANDLE previous = Imm32.INSTANCE.ImmAssociateContext(this.hwnd, null);
            if (previous != null) {
                this.parkedContext = previous;
            }
            this.composing = false;
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
        return this.composing;
    }

    @Override
    public void setCaretRect(int x, int y, int width, int height) {
        if (x == this.caretX && y == this.caretY && width == this.caretWidth && height == this.caretHeight) {
            return;
        }
        this.caretX = x;
        this.caretY = y;
        this.caretWidth = Math.max(width, 1);
        this.caretHeight = Math.max(height, 1);
        if (this.attached && this.composing) {
            applyCaretToIme();
        }
    }

    @Override
    public void setExclusionRect(int x, int y, int width, int height) {
        if (x == this.exclusionX && y == this.exclusionY && width == this.exclusionWidth && height == this.exclusionHeight) {
            return;
        }
        this.exclusionX = x;
        this.exclusionY = y;
        this.exclusionWidth = width;
        this.exclusionHeight = height;
        if (this.attached && this.composing) {
            applyCaretToIme();
        }
    }

    @Override
    public void cancelComposition() {
        if (!this.attached) {
            return;
        }
        WinNT.HANDLE context = Imm32.INSTANCE.ImmGetContext(this.hwnd);
        if (context != null) {
            try {
                Imm32.INSTANCE.ImmNotifyIME(context, WinIme.NI_COMPOSITIONSTR, WinIme.CPS_CANCEL, 0);
            } finally {
                Imm32.INSTANCE.ImmReleaseContext(this.hwnd, context);
            }
        }
        this.composing = false;
        this.preedit = ImePreedit.EMPTY;
    }

    @Override
    public String describe() {
        return "Windows IMM32";
    }

    private WinDef.LRESULT windowProc(WinDef.HWND wnd, int msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
        WinDef.LPARAM forwarded = lParam;
        try {
            switch (msg) {
                case WinIme.WM_IME_SETCONTEXT:
                    if (!drawsPreeditOurselves()) break;
                    long masked = lParam.longValue() & 0xFFFFFFFF7FFFFFFFL;
                    forwarded = new WinDef.LPARAM(masked);
                    break;
                case WinIme.WM_IME_STARTCOMPOSITION:
                    this.composing = true;
                    this.preedit = ImePreedit.EMPTY;
                    applyCaretToIme();
                    trace("WM_IME_STARTCOMPOSITION");
                    if (!drawsPreeditOurselves()) break;
                    return new WinDef.LRESULT(0L);
                case WinIme.WM_IME_COMPOSITION:
                    readComposition();
                    applyCaretToIme();
                    trace("WM_IME_COMPOSITION " + this.preedit);
                    if (!drawsPreeditOurselves()) break;
                    long remaining = lParam.longValue() & 0xFFFFFFFFFFFFFE40L;
                    if (remaining == 0L) {
                        return new WinDef.LRESULT(0L);
                    }
                    forwarded = new WinDef.LPARAM(remaining);
                    break;
                case WinIme.WM_IME_ENDCOMPOSITION:
                    this.composing = false;
                    this.preedit = ImePreedit.EMPTY;
                    trace("WM_IME_ENDCOMPOSITION");
                    if (!drawsPreeditOurselves()) break;
                    return new WinDef.LRESULT(0L);
                case WinIme.WM_IME_NOTIFY:
                    int command = wParam.intValue();
                    if (command != WinIme.IMN_OPENCANDIDATE && command != WinIme.IMN_CHANGECANDIDATE) break;
                    applyCaretToIme();
                    break;
                default:
                    break;
            }
        } catch (Throwable error) {
            ImeSupport.logger().warn("IME window procedure failed on message 0x" + Integer.toHexString(msg), error);
        }
        return callPrevious(wnd, msg, wParam, forwarded);
    }

    private WinDef.LRESULT callPrevious(WinDef.HWND wnd, int msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
        Pointer previous = this.previousWndProc;
        if (previous == null) {
            return new WinDef.LRESULT(0L);
        }
        return User32Ex.INSTANCE.CallWindowProcW(previous, wnd, msg, wParam, lParam);
    }

    private void readComposition() {
        WinNT.HANDLE context = Imm32.INSTANCE.ImmGetContext(this.hwnd);
        if (context == null) {
            this.preedit = ImePreedit.EMPTY;
            return;
        }
        try {
            String text = readString(context, WinIme.GCS_COMPSTR);
            if (text.isEmpty()) {
                this.preedit = ImePreedit.EMPTY;
                return;
            }
            int caret = Imm32.INSTANCE.ImmGetCompositionStringW(context, WinIme.GCS_CURSORPOS, null, 0);
            byte[] attributes = readBytes(context, WinIme.GCS_COMPATTR);
            int[] clauses = readInts(context, WinIme.GCS_COMPCLAUSE);
            this.preedit = new ImePreedit(text, Math.max(caret, 0), attributes, clauses);
        } finally {
            Imm32.INSTANCE.ImmReleaseContext(this.hwnd, context);
        }
    }

    private void applyCaretToIme() {
        if (!this.attached || !this.composing || this.applyingCaret) {
            return;
        }
        if (!options().pinCandidateWindowToCaret) {
            return;
        }
        WinNT.HANDLE context = Imm32.INSTANCE.ImmGetContext(this.hwnd);
        if (context == null) {
            return;
        }
        this.applyingCaret = true;
        try {
            CompositionForm composition = new CompositionForm();
            composition.dwStyle = WinIme.CFS_POINT;
            composition.ptCurrentPos.x = this.caretX;
            composition.ptCurrentPos.y = this.caretY;
            Imm32.INSTANCE.ImmSetCompositionWindow(context, composition);
            CandidateForm candidate = new CandidateForm();
            candidate.dwIndex = 0;
            candidate.dwStyle = WinIme.CFS_EXCLUDE;
            candidate.ptCurrentPos.x = this.caretX;
            candidate.ptCurrentPos.y = this.caretY + this.caretHeight;
            boolean hasExclusion = this.exclusionWidth > 0 && this.exclusionHeight > 0;
            int left = hasExclusion ? this.exclusionX : this.caretX;
            int top = hasExclusion ? this.exclusionY : this.caretY;
            int right = hasExclusion ? this.exclusionX + this.exclusionWidth : this.caretX + this.caretWidth;
            int bottom = hasExclusion ? this.exclusionY + this.exclusionHeight : this.caretY + this.caretHeight;
            candidate.rcArea.left = Math.min(left, this.caretX);
            candidate.rcArea.top = Math.min(top, this.caretY);
            candidate.rcArea.right = Math.max(right, this.caretX + this.caretWidth);
            candidate.rcArea.bottom = Math.max(bottom, this.caretY + this.caretHeight);
            Imm32.INSTANCE.ImmSetCandidateWindow(context, candidate);
        } finally {
            this.applyingCaret = false;
            Imm32.INSTANCE.ImmReleaseContext(this.hwnd, context);
        }
    }

    private void restoreInputContext() {
        if (this.hwnd == null) {
            return;
        }
        if (this.parkedContext != null) {
            Imm32.INSTANCE.ImmAssociateContext(this.hwnd, this.parkedContext);
            this.parkedContext = null;
        } else {
            Imm32.INSTANCE.ImmAssociateContextEx(this.hwnd, null, WinIme.IACE_DEFAULT);
        }
    }

    private static String readString(WinNT.HANDLE context, int index) {
        int bytes = Imm32.INSTANCE.ImmGetCompositionStringW(context, index, null, 0);
        if (bytes <= 0) {
            return "";
        }
        try (Memory buffer = new Memory((long) bytes)) {
            int written = Imm32.INSTANCE.ImmGetCompositionStringW(context, index, buffer, bytes);
            if (written <= 0) {
                return "";
            }
            return new String(buffer.getCharArray(0L, written / 2));
        }
    }

    private static byte[] readBytes(WinNT.HANDLE context, int index) {
        int bytes = Imm32.INSTANCE.ImmGetCompositionStringW(context, index, null, 0);
        if (bytes <= 0) {
            return new byte[0];
        }
        try (Memory buffer = new Memory((long) bytes)) {
            int written = Imm32.INSTANCE.ImmGetCompositionStringW(context, index, buffer, bytes);
            if (written <= 0) {
                return new byte[0];
            }
            return buffer.getByteArray(0L, written);
        }
    }

    private static int[] readInts(WinNT.HANDLE context, int index) {
        int bytes = Imm32.INSTANCE.ImmGetCompositionStringW(context, index, null, 0);
        if (bytes < 4) {
            return new int[0];
        }
        try (Memory buffer = new Memory((long) bytes)) {
            int written = Imm32.INSTANCE.ImmGetCompositionStringW(context, index, buffer, bytes);
            if (written < 4) {
                return new int[0];
            }
            return buffer.getIntArray(0L, written / 4);
        }
    }

    private static ImeOptions options() {
        return ImeSupport.options();
    }

    private static boolean drawsPreeditOurselves() {
        ImeOptions options = options();
        return options.suppressSystemCompositionWindow && options.inlinePreedit;
    }

    private static void trace(String message) {
        if (ImeSupport.options().verboseLogging) {
            ImeSupport.logger().debug(message);
        }
    }

    // Reference added explicitly so static analysis tools do not flag the
    // platform-windows classes as unused; the hook is the only field that
    // actually keeps the WindowProc callback alive for JNA.
    @SuppressWarnings("unused")
    private static void referenceBridge() {
        Objects.requireNonNull(Win32ImeBackend.class);
    }
}
