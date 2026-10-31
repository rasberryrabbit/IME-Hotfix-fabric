package jp.antimeme.mc.imehotfix.core;

public enum PreeditStyle {
    INPUT(false),
    TARGET_CONVERTED(true),
    CONVERTED(false),
    TARGET_NOT_CONVERTED(true),
    INPUT_ERROR(false),
    FIXED_CONVERTED(false);

    private static final PreeditStyle[] BY_ATTRIBUTE = values();
    private final boolean target;

    PreeditStyle(boolean target) {
        this.target = target;
    }

    public boolean isTarget() {
        return this.target;
    }

    public static PreeditStyle fromAttribute(int attribute) {
        if (attribute < 0 || attribute >= BY_ATTRIBUTE.length) {
            return INPUT;
        }
        return BY_ATTRIBUTE[attribute];
    }
}
