package jp.antimeme.mc.imehotfix.core;

public interface ImeLogger {
    ImeLogger NOOP = new ImeLogger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message, Throwable error) {
        }

        @Override
        public void debug(String message) {
        }
    };

    void info(String message);

    void warn(String message, Throwable error);

    void debug(String message);
}
