package jp.antimeme.mc.imehotfix.core.win32;

final class WinIme {
    static final int WM_IME_STARTCOMPOSITION = 269;
    static final int WM_IME_ENDCOMPOSITION = 270;
    static final int WM_IME_COMPOSITION = 271;
    static final int WM_IME_SETCONTEXT = 641;
    static final int WM_IME_NOTIFY = 642;
    static final int GWLP_WNDPROC = -4;
    static final long ISC_SHOWUICOMPOSITIONWINDOW = 0x80000000L;
    static final int IMN_CHANGECANDIDATE = 3;
    static final int IMN_OPENCANDIDATE = 5;
    static final int IMN_SETCANDIDATEPOS = 9;
    static final int IMN_SETCOMPOSITIONWINDOW = 11;
    static final int GCS_COMPREADSTR = 1;
    static final int GCS_COMPREADATTR = 2;
    static final int GCS_COMPREADCLAUSE = 4;
    static final int GCS_COMPSTR = 8;
    static final int GCS_COMPATTR = 16;
    static final int GCS_COMPCLAUSE = 32;
    static final int GCS_CURSORPOS = 128;
    static final int GCS_DELTASTART = 256;
    static final int GCS_RESULTSTR = 2048;
    static final long GCS_DISPLAY_BITS = 447L;
    static final int CFS_POINT = 2;
    static final int CFS_EXCLUDE = 128;
    static final int NI_COMPOSITIONSTR = 21;
    static final int CPS_CANCEL = 4;
    static final int IACE_DEFAULT = 16;

    private WinIme() {
    }
}
