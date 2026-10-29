package dev.iansun.crosslanguagesearch;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.loading.FMLPaths;

/**
 * 搜尋診斷記錄。只有在 config/crosslanguagesearch-debug.txt 存在時才輸出，平常不影響效能。
 */
public final class SearchDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path FLAG = FMLPaths.CONFIGDIR.get().resolve("crosslanguagesearch-debug.txt");

    public static final boolean ENABLED = Files.exists(FLAG);

    /** 開關檔裡每一行是一個「探針」字詞：建索引時，含有這些字的索引字串會被記錄下來 */
    private static final List<String> PROBES = loadProbes();
    private static final int PROBE_LIMIT = 15;
    private static final Map<String, AtomicInteger> PROBE_COUNTS = new ConcurrentHashMap<>();

    private static volatile long searchStart;
    private static volatile String searchQuery = "";

    private SearchDebug() {}

    private static List<String> loadProbes() {
        if (!ENABLED) return List.of();
        try {
            List<String> probes = new ArrayList<>();
            for (String line : Files.readAllLines(FLAG, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) probes.add(SearchText.fold(line));
            }
            return probes;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static void probeIndexed(String folded) {
        for (String probe : PROBES) {
            if (!folded.contains(probe)) continue;
            int n = PROBE_COUNTS.computeIfAbsent(probe, k -> new AtomicInteger()).incrementAndGet();
            if (n <= PROBE_LIMIT) log("Index entry containing \"{}\": {}", probe, visible(folded));
        }
    }

    public static void log(String format, Object... args) {
        if (ENABLED) LOGGER.info("[Search debug] " + format, args);
    }

    public static void searchStarted(String query) {
        if (!ENABLED) return;
        searchStart = System.nanoTime();
        searchQuery = query;
        log("Search started: \"{}\" -> folded \"{}\"", query, SearchText.fold(query));
    }

    public static void searchApplied(int results) {
        if (!ENABLED) return;
        log("Search \"{}\" finished: {} results in {} ms", searchQuery, results, (System.nanoTime() - searchStart) / 1_000_000);
    }

    /** 給診斷用：把分隔字元換成看得見的符號 */
    public static String visible(String s) {
        return s == null ? "null" : s.replace(SearchText.SEPARATOR, '｜');
    }
}
