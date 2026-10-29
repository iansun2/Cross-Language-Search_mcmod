package dev.iansun.crosslanguagesearch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import dev.iansun.crosslanguagesearch.mixin.ClientLanguageAccessor;

/**
 * 搜尋文字的正規化與英文版本產生。
 * <p>
 * 索引端與查詢端都必須經過同一個 {@link #fold(String)}，比對才會一致。
 */
public final class SearchText {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 串接「原文」與「英文版本」時使用的分隔字元，使用者不可能輸入，所以查詢不會跨越邊界。 */
    public static final char SEPARATOR = '\u0001';

    private static final String SECONDARY_LANGUAGE = "en_us";

    /** JECh（PinIn）搜尋器不是執行緒安全的，所有呼叫共用這把鎖 */
    public static final Object PININ_LOCK = new Object();

    /** 與原版 TranslatableContents 相同的格式語法：%s、%1$s、%% */
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static final int MAX_DEPTH = 16;

    /** BMP 範圍的繁→簡對照表，0 代表不轉換 */
    private static final char[] T2S_BMP = new char[0x10000];
    /** 超出 BMP 的少數字 */
    private static final Int2IntOpenHashMap T2S_SUPPLEMENTARY = new Int2IntOpenHashMap();

    private static volatile Language secondary;
    /** 自己載入的目前語言表，不經過其他模組替換過的 Language.getInstance() */
    private static volatile Language primaryLang;
    private static volatile boolean loaded;

    /**
     * 中英雙向反查。給只拿得到已翻譯字串、拿不到翻譯鍵的地方使用（RS、Reverie Foundry 目錄）。
     */
    private static volatile ReverseIndex reverse = ReverseIndex.EMPTY;

    private static final int CACHE_LIMIT = 200_000;
    private static final Map<String, String> FOLD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> PLAIN_CACHE = new ConcurrentHashMap<>();
    /** ItemStack 沒有覆寫 equals，WeakHashMap 等同以實例為鍵，物品被回收時自動移除 */
    private static final Map<ItemStack, String> TOOLTIP_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    static {
        loadT2S();
    }

    private SearchText() {}

    private static void loadT2S() {
        try (InputStream in = SearchText.class.getResourceAsStream("/crosslanguagesearch/TSCharacters.txt")) {
            if (in == null) {
                LOGGER.error("TSCharacters.txt not found; Traditional/Simplified conversion disabled");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String key = line.substring(0, tab);
                String value = line.substring(tab + 1);
                int space = value.indexOf(' ');
                if (space >= 0) value = value.substring(0, space); // 一繁對多簡時取第一個
                if (key.codePointCount(0, key.length()) != 1 || value.codePointCount(0, value.length()) != 1) continue;
                int from = key.codePointAt(0);
                int to = value.codePointAt(0);
                if (from == to) continue;
                if (from < 0x10000 && to < 0x10000) T2S_BMP[from] = (char) to;
                else T2S_SUPPLEMENTARY.put(from, to);
                count++;
            }
            LOGGER.info("Loaded {} Traditional-to-Simplified character mappings", count);
        } catch (Exception e) {
            LOGGER.error("Failed to load Traditional/Simplified table; conversion disabled", e);
        }
    }

    /**
     * 正規化：去除格式碼、NFKC（全形→半形）、轉小寫、繁→簡。
     */
    public static String fold(String s) {
        if (s == null || s.isEmpty()) return s;
        boolean plain = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x80 || c == '§') {
                plain = false;
                break;
            }
        }
        if (plain) return s.toLowerCase(Locale.ROOT);

        String n = Normalizer.normalize(stripFormatting(s), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = null;
        for (int i = 0; i < n.length(); ) {
            int cp = n.codePointAt(i);
            int len = Character.charCount(cp);
            int mapped = cp < 0x10000 ? (T2S_BMP[cp] != 0 ? T2S_BMP[cp] : cp) : T2S_SUPPLEMENTARY.getOrDefault(cp, cp);
            if (mapped != cp && sb == null) {
                sb = new StringBuilder(n.length());
                sb.append(n, 0, i);
            }
            if (sb != null) sb.appendCodePoint(mapped);
            i += len;
        }
        return sb == null ? n : sb.toString();
    }

    /** 帶快取的 {@link #fold(String)}，給每打一個字就重新比對全部項目的介面使用（RS、目錄）。 */
    public static String foldCached(String s) {
        if (s == null || s.isEmpty()) return s;
        String cached = FOLD_CACHE.get(s);
        if (cached != null) return cached;
        String folded = fold(s);
        if (FOLD_CACHE.size() > CACHE_LIMIT) FOLD_CACHE.clear();
        FOLD_CACHE.put(s, folded);
        return folded;
    }

    /**
     * 「已翻譯字串是否包含查詢」的通用判斷：原文與英文反查結果任一包含即可。
     * 兩邊都會經過 fold，所以繁簡、全半形都不影響。
     */
    public static boolean plainContains(String text, String query) {
        if (text == null || query == null) return false;
        String q = foldCached(query.trim());
        if (q.isEmpty()) return true;
        return foldCached(text).contains(q) || plainSecondary(text).contains(q);
    }

    /**
     * 對一段已翻譯的純文字，逐行查反查表，回傳已 fold 的另一種語言版本（查不到時為空字串）。
     * 中→英、英→中都會查，所以畫面顯示英文的地方也能用中文搜。
     */
    public static String plainSecondary(String text) {
        ReverseIndex index = reverse;
        if (text == null || text.isEmpty() || index.isEmpty()) return "";
        String cached = PLAIN_CACHE.get(text);
        if (cached != null) return cached;
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String folded = fold(line).trim();
            if (folded.isEmpty()) continue;
            String other = index.lookupLine(folded);
            if (other != null) {
                if (!sb.isEmpty()) sb.append(SEPARATOR);
                sb.append(other);
            }
        }
        String result = sb.toString();
        if (PLAIN_CACHE.size() > CACHE_LIMIT) PLAIN_CACHE.clear();
        PLAIN_CACHE.put(text, result);
        return result;
    }

    /**
     * 物品工具提示的英文版本（已 fold），直接從文字元件組出，比反查表準確。
     * 必須在主執行緒呼叫；結果依 ItemStack 實例快取。
     */
    public static String tooltipSecondary(ItemStack stack) {
        Language lang = secondary;
        if (lang == null || stack == null || stack.isEmpty()) return "";
        String cached = TOOLTIP_CACHE.get(stack);
        if (cached != null) return cached;
        StringBuilder sb = new StringBuilder();
        try {
            Minecraft mc = Minecraft.getInstance();
            List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
            for (int i = 1; i < lines.size(); i++) { // 第一行是名稱，名稱另外處理
                if (!sb.isEmpty()) sb.append(SEPARATOR);
                sb.append(render(lines.get(i), lang));
            }
        } catch (Throwable ignored) {
            // 有些模組的工具提示在特定狀態下會拋錯，忽略即可
        }
        String result = fold(sb.toString());
        TOOLTIP_CACHE.put(stack, result);
        return result;
    }

    private static void buildReverse(Minecraft mc, String selected, Language secondaryLang) {
        // 不直接用 Language.getInstance()：模組包裡有其他模組會替換它，型別不一定是 ClientLanguage
        Language current = Language.getInstance();
        Map<String, String> primary = Map.of();
        Language loaded = ClientLanguage.loadFrom(mc.getResourceManager(), List.of(SECONDARY_LANGUAGE, selected), current.isDefaultRightToLeft());
        if (loaded instanceof ClientLanguageAccessor accessor) primary = accessor.cls$getStorage();
        primaryLang = loaded;
        Map<String, String> english = secondaryLang instanceof ClientLanguageAccessor accessor ? accessor.cls$getStorage() : Map.of();

        ReverseIndex index = ReverseIndex.build(primary, english);
        reverse = index;
        PLAIN_CACHE.clear();
        LOGGER.info("Built reverse translation index: {} exact entries, {} format templates (current Language impl: {})",
                index.exactSize(), index.templateCount(), current.getClass().getName());
    }

    private static String stripFormatting(String s) {
        if (s.indexOf('§') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 語言表還沒載入過時才載入（正常情況下資源載入時已經載好） */
    public static void ensureLoaded() {
        if (!loaded) reloadSecondaryLanguage();
    }

    /**
     * 資源重新載入時呼叫（啟動、切換語言、F3+T），重新載入目前語言與第二語言（英文）的語言表。
     * 目前語言已經是英文時不載入，避免重複索引。
     */
    public static void reloadSecondaryLanguage() {
        loaded = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            String selected = mc.getLanguageManager().getSelected();
            TOOLTIP_CACHE.clear();
            if (SECONDARY_LANGUAGE.equalsIgnoreCase(selected)) {
                secondary = null;
                primaryLang = null;
                reverse = ReverseIndex.EMPTY;
                return;
            }
            long start = System.currentTimeMillis();
            Language lang = ClientLanguage.loadFrom(mc.getResourceManager(), List.of(SECONDARY_LANGUAGE), false);
            secondary = lang;
            buildReverse(mc, selected, lang);
            FOLD_CACHE.clear();
            LOGGER.info("Loaded secondary language {} in {} ms", SECONDARY_LANGUAGE, System.currentTimeMillis() - start);
        } catch (Exception e) {
            secondary = null;
            reverse = ReverseIndex.EMPTY;
            LOGGER.error("Failed to load secondary language; English search disabled", e);
        }
    }

    /**
     * 回傳「原文」加上其他語言版本（若不同）。供索引使用，之後還會再經過 {@link #fold(String)}。
     * <p>
     * 除了英文，也用自己載入的目前語言表重新組一次：像 Untranslated Items 這類模組會讓物品名稱
     * 顯示英文，畫面上的原文就不是中文，這時還是要能用中文搜到。
     */
    public static String withSecondary(Component component, String original) {
        Language en = secondary;
        if (en == null || component == null) return original;
        try {
            StringBuilder sb = new StringBuilder(original);
            Language primary = primaryLang;
            String native_ = primary != null ? render(component, primary) : "";
            if (!native_.isEmpty() && !native_.equals(original)) sb.append(SEPARATOR).append(native_);
            String english = render(component, en);
            if (!english.isEmpty() && !english.equals(original) && !english.equals(native_)) sb.append(SEPARATOR).append(english);
            return sb.toString();
        } catch (Exception e) {
            return original;
        }
    }

    /** 用指定語言表重新組出元件的文字，不改動遊戲目前的語言。 */
    static String render(Component component, Language lang) {
        StringBuilder sb = new StringBuilder();
        append(component, lang, sb, 0);
        return sb.toString();
    }

    private static void append(Component component, Language lang, StringBuilder sb, int depth) {
        if (depth > MAX_DEPTH) return;
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents translatable) {
            String fallback = translatable.getFallback() != null ? translatable.getFallback() : translatable.getKey();
            format(lang.getOrDefault(translatable.getKey(), fallback), translatable.getArgs(), lang, sb, depth);
        } else {
            contents.visit(text -> {
                sb.append(text);
                return Optional.empty();
            });
        }
        for (Component sibling : component.getSiblings()) {
            append(sibling, lang, sb, depth + 1);
        }
    }

    private static void format(String pattern, Object[] args, Language lang, StringBuilder sb, int depth) {
        Matcher m = FORMAT.matcher(pattern);
        int last = 0;
        int nextArg = 0;
        while (m.find()) {
            sb.append(pattern, last, m.start());
            String type = m.group(2);
            if ("%".equals(type)) {
                sb.append('%');
            } else if ("s".equals(type)) {
                int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : nextArg++;
                if (index >= 0 && index < args.length) {
                    Object arg = args[index];
                    if (arg instanceof Component c) append(c, lang, sb, depth + 1);
                    else sb.append(arg);
                }
            } else {
                sb.append(m.group());
            }
            last = m.end();
        }
        sb.append(pattern, last, pattern.length());
    }
}
