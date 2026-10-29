package dev.iansun.crosslanguagesearch;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * EMI 相關的初始化。只有在確認 EMI 已安裝後才會被呼叫，
 * 所以 {@link DynamicTooltips} 這類引用 EMI 類別的程式不會在沒裝 EMI 時被載入。
 */
final class EmiIntegration {
    private EmiIntegration() {}

    static void init() {
        DynamicTooltips.load();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> DynamicTooltips.tick());
    }
}
