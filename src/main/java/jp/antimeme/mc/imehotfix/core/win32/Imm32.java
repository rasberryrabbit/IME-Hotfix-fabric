package jp.antimeme.mc.imehotfix.core.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;

interface Imm32 extends StdCallLibrary {
    Imm32 INSTANCE = Native.load("imm32", Imm32.class);

    WinNT.HANDLE ImmGetContext(WinDef.HWND hwnd);

    boolean ImmReleaseContext(WinDef.HWND hwnd, WinNT.HANDLE himc);

    WinNT.HANDLE ImmAssociateContext(WinDef.HWND hwnd, WinNT.HANDLE himc);

    boolean ImmAssociateContextEx(WinDef.HWND hwnd, WinNT.HANDLE himc, int flags);

    int ImmGetCompositionStringW(WinNT.HANDLE himc, int index, Pointer buf, int bufLen);

    boolean ImmSetCompositionWindow(WinNT.HANDLE himc, CompositionForm form);

    boolean ImmSetCandidateWindow(WinNT.HANDLE himc, CandidateForm form);

    boolean ImmNotifyIME(WinNT.HANDLE himc, int action, int subAction, int lParam);
}
