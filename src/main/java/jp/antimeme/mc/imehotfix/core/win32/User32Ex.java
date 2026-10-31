package jp.antimeme.mc.imehotfix.core.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;

interface User32Ex extends StdCallLibrary {
    User32Ex INSTANCE = Native.load("user32", User32Ex.class);

    Pointer SetWindowLongPtrW(WinDef.HWND hwnd, int nIndex, WinUser.WindowProc wndProc);

    Pointer SetWindowLongPtrW(WinDef.HWND hwnd, int nIndex, Pointer wndProc);

    WinDef.LRESULT CallWindowProcW(Pointer previous, WinDef.HWND hwnd, int msg,
                                   WinDef.WPARAM wParam, WinDef.LPARAM lParam);
}
