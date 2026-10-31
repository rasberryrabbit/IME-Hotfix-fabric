package jp.antimeme.mc.imehotfix.core;

public final class ImeOptions {
    public volatile boolean disableImeOutsideTextFields = true;
    public volatile boolean suppressSystemCompositionWindow = true;
    public volatile boolean inlinePreedit = true;
    public volatile boolean pinCandidateWindowToCaret = true;
    public volatile boolean filterWithComposition = true;
    public volatile boolean commitCompositionOnBlur = true;
    public volatile boolean cancelCompositionOnFocusLoss = true;
    public volatile boolean highlightOverflow = true;
    public volatile boolean verboseLogging = false;
    public volatile int targetTint = 0x40FFFFFF;
    public volatile int targetUnderline = -1;
    public volatile int clauseUnderline = -6250336;
    public volatile int errorUnderline = -43691;
    public volatile int overflowWrapTint = 1614007551;
    public volatile int overflowDropTint = 1627337523;

    ImeOptions() {
    }
}
