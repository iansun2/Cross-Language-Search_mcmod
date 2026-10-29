package dev.iansun.crosslanguagesearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.search.EmiSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/**
 * 偵測「工具提示會隨玩家狀態改變」的物品，讓 EMI 對它們改用即時比對。
 * <p>
 * 做法：在主執行緒上，每個 tick 只花固定時間，輪流重算物品的工具提示並記下雜湊；
 * 同一個物品前後兩次不同，就標記為動態。標記結果會存檔，下次進遊戲一開始就直接即時比對。
 */
public final class DynamicTooltips {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 每個 tick 最多花在掃描上的時間 */
    private static final long BUDGET_NANOS = 1_000_000L;
    /** 一輪掃完後，隔多少 tick 再開始下一輪 */
    private static final int PASS_INTERVAL_TICKS = 20 * 30;
    /** 一輪內超過這個數量同時改變，視為全域狀態變化而非個別物品 */
    private static final int MAX_CHANGES_PER_PASS = 24;

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("crosslanguagesearch-dynamic.txt");

    /** 以物品 ID 記錄，執行緒安全，EMI 的搜尋執行緒會讀取 */
    private static final Set<ResourceLocation> DYNAMIC = ConcurrentHashMap.newKeySet();

    // 以下只在主執行緒使用
    private static final Map<EmiStack, Integer> baseline = new IdentityHashMap<>();
    private static List<EmiStack> scanning = List.of();
    private static int cursor;
    private static int cooldown;
    private static boolean lastAdvanced;
    private static final Set<ResourceLocation> candidates = new HashSet<>();
    private static volatile boolean resetRequested = true;

    private DynamicTooltips() {}

    /** EMI 搜尋執行緒呼叫：這個物品是否要改用即時比對 */
    public static boolean isDynamic(EmiStack stack) {
        if (DYNAMIC.isEmpty()) return false;
        ResourceLocation id = stack.getId();
        return id != null && DYNAMIC.contains(id);
    }

    /** EMI 重新建索引時呼叫（建索引執行緒），下個 tick 由主執行緒重置 */
    public static void requestReset() {
        resetRequested = true;
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                ResourceLocation id = ResourceLocation.tryParse(line);
                if (id != null) DYNAMIC.add(id);
            }
            LOGGER.info("Loaded {} items with dynamic tooltips", DYNAMIC.size());
        } catch (IOException e) {
            LOGGER.error("Failed to read {}", FILE, e);
        }
    }

    private static void save() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# Items detected by Cross-Language-Search as having dynamic tooltips; their tooltips are re-evaluated on every search.\n");
            sb.append("# Edit freely, one item ID per line. Delete this file to re-detect from scratch.\n");
            for (ResourceLocation id : new TreeSet<>(DYNAMIC)) sb.append(id).append('\n');
            Files.writeString(FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write {}", FILE, e);
        }
    }

    /** 主執行緒每個 client tick 呼叫 */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 進階工具提示（F3+H）切換會讓所有物品的工具提示一起變，重新建立基準
        boolean advanced = mc.options.advancedItemTooltips;
        if (advanced != lastAdvanced) {
            lastAdvanced = advanced;
            resetRequested = true;
        }
        if (resetRequested) {
            resetRequested = false;
            baseline.clear();
            candidates.clear();
            scanning = List.copyOf(EmiStackList.stacks);
            cursor = 0;
            cooldown = 0;
        }
        if (scanning.isEmpty()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // 很多模組按住 Shift / Ctrl / Alt 會顯示更多資訊，這時的工具提示不能拿來比較
        if (Screen.hasShiftDown() || Screen.hasControlDown() || Screen.hasAltDown()) return;

        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (System.nanoTime() < deadline) {
            if (cursor >= scanning.size()) {
                finishPass();
                break;
            }
            EmiStack stack = scanning.get(cursor++);
            if (isDynamic(stack)) continue;
            int hash = signature(stack);
            Integer previous = baseline.put(stack, hash);
            if (previous != null && previous != hash) {
                ResourceLocation id = stack.getId();
                if (id != null) candidates.add(id);
            }
        }
    }

    /**
     * 一輪掃完才決定要不要標記。單一物品的狀態變化通常只影響少數物品；
     * 一次變很多代表是全域狀態改變（切換按鍵、設定、維度等），這時全部忽略並重新建立基準。
     */
    private static void finishPass() {
        cursor = 0;
        cooldown = PASS_INTERVAL_TICKS;
        if (candidates.isEmpty()) return;

        if (candidates.size() > MAX_CHANGES_PER_PASS) {
            LOGGER.info("{} tooltips changed in one pass; treating it as a global state change and ignoring", candidates.size());
            candidates.clear();
            baseline.clear();
            return;
        }
        boolean added = false;
        for (ResourceLocation id : candidates) {
            if (DYNAMIC.add(id)) {
                LOGGER.info("Detected dynamic tooltip: {}", id);
                added = true;
            }
        }
        candidates.clear();
        if (!added) return;
        save();
        // 讓目前的搜尋結果立刻反映
        try {
            EmiSearch.update();
        } catch (Throwable ignored) {
            // EMI 介面還沒初始化時沒有搜尋欄，忽略即可
        }
    }

    private static int signature(EmiStack stack) {
        try {
            int hash = 1;
            for (Component line : stack.getTooltipText()) {
                if (line != null) hash = 31 * hash + line.getString().hashCode();
            }
            return hash;
        } catch (Throwable t) {
            return 0;
        }
    }
}
