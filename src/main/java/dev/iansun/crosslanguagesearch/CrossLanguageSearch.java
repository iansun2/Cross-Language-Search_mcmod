package dev.iansun.crosslanguagesearch;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@Mod(value = CrossLanguageSearch.MOD_ID, dist = Dist.CLIENT)
public class CrossLanguageSearch {
    public static final String MOD_ID = "crosslanguagesearch";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] SUPPORTED = { "emi", "refinedstorage", "apothic_enchanting", "reveriefoundry", "jecharacters" };

    public CrossLanguageSearch(IEventBus modBus) {
        StringBuilder found = new StringBuilder();
        for (String id : SUPPORTED) {
            if (ModList.get().isLoaded(id)) found.append(found.isEmpty() ? "" : ", ").append(id);
        }
        if (found.isEmpty()) {
            LOGGER.info("No supported mods detected; Cross-Language-Search stays inactive");
            return;
        }
        LOGGER.info("Cross-Language-Search active, detected: {}", found);

        // 語言表在資源載入時建立，不依賴 EMI，RS / 附魔圖書館 / 目錄也用得到
        modBus.addListener(RegisterClientReloadListenersEvent.class, event ->
                event.registerReloadListener((ResourceManagerReloadListener) manager -> SearchText.reloadSecondaryLanguage()));

        if (ModList.get().isLoaded("emi")) {
            // 動態工具提示偵測只服務 EMI；放在獨立類別裡，沒裝 EMI 時不會載入任何 EMI 類別
            EmiIntegration.init();
        }
    }
}
