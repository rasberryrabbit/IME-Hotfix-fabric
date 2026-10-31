package jp.antimeme.mc.imehotfix.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImePreedit {
    private static final byte[] NO_ATTRIBUTES = new byte[0];
    private static final int[] NO_CLAUSES = new int[0];
    public static final ImePreedit EMPTY = new ImePreedit("", 0, NO_ATTRIBUTES, NO_CLAUSES);

    private final String text;
    private final int caret;
    private final byte[] attributes;
    private final int[] clauses;

    public ImePreedit(String text, int caret, byte[] attributes, int[] clauses) {
        this.text = text == null ? "" : text;
        this.caret = clamp(caret, 0, this.text.length());
        this.attributes = attributes == null ? NO_ATTRIBUTES : attributes;
        this.clauses = clauses == null ? NO_CLAUSES : clauses;
    }

    public boolean isEmpty() {
        return this.text.isEmpty();
    }

    public String text() {
        return this.text;
    }

    public int caret() {
        return this.caret;
    }

    public int length() {
        return this.text.length();
    }

    public PreeditStyle styleAt(int charIndex) {
        if (charIndex < 0 || charIndex >= this.attributes.length) {
            return PreeditStyle.INPUT;
        }
        return PreeditStyle.fromAttribute(this.attributes[charIndex] & 0xFF);
    }

    public List<Run> runs() {
        if (this.text.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Run> runs = new ArrayList<>();
        int runStart = 0;
        PreeditStyle runStyle = this.styleAt(0);
        for (int i = 1; i < this.text.length(); i++) {
            PreeditStyle style = this.styleAt(i);
            if (style == runStyle) continue;
            runs.add(new Run(runStart, i, runStyle));
            runStart = i;
            runStyle = style;
        }
        runs.add(new Run(runStart, this.text.length(), runStyle));
        return runs;
    }

    public int clauseCount() {
        return this.clauses.length == 0 ? 0 : this.clauses.length - 1;
    }

    @Override
    public String toString() {
        return "ImePreedit[text=" + this.text + ", caret=" + this.caret
                + ", attrs=" + this.attributes.length + ", clauses=" + this.clauseCount() + "]";
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static final class Run {
        private final int start;
        private final int end;
        private final PreeditStyle style;

        Run(int start, int end, PreeditStyle style) {
            this.start = start;
            this.end = end;
            this.style = style;
        }

        public int start() {
            return this.start;
        }

        public int end() {
            return this.end;
        }

        public PreeditStyle style() {
            return this.style;
        }
    }
}
