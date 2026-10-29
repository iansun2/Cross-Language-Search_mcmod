package dev.iansun.crosslanguagesearch;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * EMI-specific initialization. Only called after EMI is confirmed to be installed,
 * so code referencing EMI classes, such as {@link DynamicTooltips}, is never loaded without EMI.
 */
final class EmiIntegration {
    private EmiIntegration() {}

    static void init() {
        DynamicTooltips.load();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> DynamicTooltips.tick());
    }
}
