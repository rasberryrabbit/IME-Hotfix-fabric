package jp.antimeme.mc.imehotfix.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric entry point for IME Hotfix.
 *
 * <p>This replaces the Forge {@code @Mod("imehotfix")} class. The original
 * checked {@code FMLEnvironment.dist == Dist.CLIENT} before calling
 * {@link ImeClientHandler#init()}; on Fabric the same effect comes from
 * declaring the entry point as {@code client} in {@code fabric.mod.json},
 * which means this class only loads on the client in the first place.</p>
 */
public final class IMEHotfixMod implements ClientModInitializer {
    public static final String MOD_ID = "imehotfix";

    @Override
    public void onInitializeClient() {
        ImeClientHandler.init();
    }
}
