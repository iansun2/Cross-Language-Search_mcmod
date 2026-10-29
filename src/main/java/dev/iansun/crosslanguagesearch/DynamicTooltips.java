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
 * Detects items whose tooltips change with player state, and makes EMI match them live.
 * <p>
 * On the main thread, a fixed time budget per tick is spent re-computing tooltips round-robin and recording a hash;
 * an item whose hash differs between passes is marked dynamic. Results are saved, so they apply from the start of the next session.
 */
public final class DynamicTooltips {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Maximum scanning time per tick */
    private static final long BUDGET_NANOS = 1_000_000L;
    /** Ticks to wait after a full pass before starting the next one */
    private static final int PASS_INTERVAL_TICKS = 20 * 30;
    /** More changes than this in one pass are treated as a global state change, not per-item changes */
    private static final int MAX_CHANGES_PER_PASS = 24;

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("crosslanguagesearch-dynamic.txt");

    /** Keyed by item ID; thread-safe because EMI's search thread reads it */
    private static final Set<ResourceLocation> DYNAMIC = ConcurrentHashMap.newKeySet();

    // Everything below is used on the main thread only
    private static final Map<EmiStack, Integer> baseline = new IdentityHashMap<>();
    private static List<EmiStack> scanning = List.of();
    private static int cursor;
    private static int cooldown;
    private static boolean lastAdvanced;
    private static final Set<ResourceLocation> candidates = new HashSet<>();
    private static volatile boolean resetRequested = true;

    private DynamicTooltips() {}

    /** Called from EMI's search thread: whether this item should be matched live */
    public static boolean isDynamic(EmiStack stack) {
        if (DYNAMIC.isEmpty()) return false;
        ResourceLocation id = stack.getId();
        return id != null && DYNAMIC.contains(id);
    }

    /** Called when EMI rebuilds its index (on the bake thread); the main thread resets on the next tick */
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

    /** Called on the main thread every client tick */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Toggling advanced tooltips (F3+H) changes every tooltip at once, so rebuild the baseline
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
        // Many mods show extra info while Shift / Ctrl / Alt is held; tooltips captured then are not comparable
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
     * Marking is decided only after a full pass. A per-item state change usually affects few items;
     * many changes at once mean a global state change (key held, settings, dimension...), so ignore them and rebuild the baseline.
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
        // Refresh the current search results right away
        try {
            EmiSearch.update();
        } catch (Throwable ignored) {
            // EMI has no search widget before its screen is initialized; safe to ignore
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
