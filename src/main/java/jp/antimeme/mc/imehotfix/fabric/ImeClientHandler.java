package jp.antimeme.mc.imehotfix.fabric;

import com.mojang.logging.LogUtils;
import java.util.List;
import jp.antimeme.mc.imehotfix.core.ExternalImeBackend;
import jp.antimeme.mc.imehotfix.core.ImeConfigFile;
import jp.antimeme.mc.imehotfix.core.ImeLogger;
import jp.antimeme.mc.imehotfix.core.ImePreedit;
import jp.antimeme.mc.imehotfix.core.ImeSupport;
import net.minecraft.client.input.PreeditEvent;
import org.slf4j.Logger;

/**
 * Fabric-side replacement for the Forge {@code ImeClientHandler}.
 *
 * <p>The only behavioural change versus the Forge build is the source of the
 * config directory: Forge used {@code FMLPaths.CONFIGDIR.get()}, Fabric uses
 * {@code FabricLoader.getInstance().getConfigDir()}. Everything else — the
 * bridge logger, the external backend, the preedit conversion — is identical
 * to the upstream mod.</p>
 */
public final class ImeClientHandler {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ImeLogger LOG_BRIDGE = new ImeLogger() {
        @Override
        public void info(String message) {
            LOGGER.info("[IME Hotfix] {}", message);
        }

        @Override
        public void warn(String message, Throwable error) {
            if (error == null) {
                LOGGER.warn("[IME Hotfix] {}", message);
            } else {
                LOGGER.warn("[IME Hotfix] {}", message, error);
            }
        }

        @Override
        public void debug(String message) {
            LOGGER.debug("[IME Hotfix] {}", message);
        }
    };

    private static final ExternalImeBackend BACKEND = new ExternalImeBackend();
    private static ImeTextTarget activeTarget;

    private ImeClientHandler() {
    }

    public static void init() {
        try {
            ImeConfigFile.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        } catch (Throwable error) {
            LOGGER.warn("[IME Hotfix] Could not load the config; continuing with defaults", error);
        }
        ImeSupport.installBackend(BACKEND, LOG_BRIDGE);
    }

    public static void submitPreedit(ImeTextTarget target, PreeditEvent event) {
        activeTarget = event == null ? null : target;
        BACKEND.setInputActive(event != null);
        BACKEND.submitPreedit(convert(event));
    }

    private static ImePreedit convert(PreeditEvent event) {
        if (event == null || event.fullText().isEmpty()) {
            return ImePreedit.EMPTY;
        }
        String text = event.fullText();
        List<String> blocks = event.blocks();
        byte[] attributes = new byte[text.length()];
        int[] clauses = new int[blocks.size() + 1];
        int offset = 0;
        for (int i = 0; i < blocks.size(); i++) {
            clauses[i] = offset;
            int attribute = blocks.size() == 1 ? 0 : (i == event.focusedBlock() ? 1 : 2);
            int length = blocks.get(i).length();
            for (int j = 0; j < length && offset < attributes.length; j++) {
                attributes[offset++] = (byte) attribute;
            }
        }
        clauses[blocks.size()] = offset;
        return new ImePreedit(text, event.caretPosition(), attributes, clauses);
    }

    public static void commitCompositionInto(ImeTextTarget target) {
        if (!ImeSupport.isComposing() || !ImeSupport.options().commitCompositionOnBlur) {
            return;
        }
        String composed = ImeSupport.preedit().text();
        ImeSupport.cancelComposition();
        if (target != null && !composed.isEmpty()) {
            target.imehotfix$insertCommitted(composed);
        }
    }

    public static void commitCompositionIntoActiveTarget() {
        commitCompositionInto(activeTarget);
    }

    public static boolean isActiveTarget(ImeTextTarget target) {
        return activeTarget == target;
    }
}
