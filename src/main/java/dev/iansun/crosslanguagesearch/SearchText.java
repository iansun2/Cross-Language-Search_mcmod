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
 * Search text normalization and generation of other-language variants.
 * <p>
 * Both the index side and the query side must go through the same {@link #fold(String)} for matches to line up.
 */
public final class SearchText {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Separator between the original text and its variants. Users cannot type it, so a query never matches across the boundary. */
    public static final char SEPARATOR = '\u0001';

    private static final String SECONDARY_LANGUAGE = "en_us";

    /** JECh (PinIn) searchers are not thread-safe; all calls share this lock */
    public static final Object PININ_LOCK = new Object();

    /** Same format syntax as vanilla TranslatableContents: %s, %1$s, %% */
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static final int MAX_DEPTH = 16;

    /** Traditional-to-Simplified table for the BMP; 0 means unchanged */
    private static final char[] T2S_BMP = new char[0x10000];
    /** The few characters outside the BMP */
    private static final Int2IntOpenHashMap T2S_SUPPLEMENTARY = new Int2IntOpenHashMap();

    private static volatile Language secondary;
    /** Our own copy of the current language table, bypassing Language.getInstance() which other mods may replace */
    private static volatile Language primaryLang;
    private static volatile boolean loaded;

    /**
     * Two-way Chinese/English lookup, for places that only have translated strings and no translation keys (RS, the Reverie Foundry catalog).
     */
    private static volatile ReverseIndex reverse = ReverseIndex.EMPTY;

    private static final int CACHE_LIMIT = 200_000;
    private static final Map<String, String> FOLD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> PLAIN_CACHE = new ConcurrentHashMap<>();
    /** ItemStack does not override equals, so this WeakHashMap is keyed by identity and entries vanish when stacks are collected */
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
                if (space >= 0) value = value.substring(0, space); // take the first candidate when one maps to several
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
     * Normalization: strip formatting codes, NFKC (full-width to half-width), lowercase, Traditional to Simplified.
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

    /** Cached {@link #fold(String)} for UIs that re-match every entry on each keystroke (RS, the catalog). */
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
     * Generic "does this translated string contain the query" check: matches if either the original or its reverse lookup contains it.
     * Both sides are folded, so Traditional/Simplified and full/half-width differences do not matter.
     */
    public static boolean plainContains(String text, String query) {
        if (text == null || query == null) return false;
        String q = foldCached(query.trim());
        if (q.isEmpty()) return true;
        return foldCached(text).contains(q) || plainSecondary(text).contains(q);
    }

    /**
     * Looks up each line of already-translated plain text and returns the folded other-language version (empty if none).
     * Works Chinese to English and English to Chinese, so text displayed in English can be searched in Chinese.
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
     * English version of an item tooltip (folded), rendered straight from its components; more accurate than the reverse lookup.
     * Must be called on the main thread; cached per ItemStack instance.
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
            for (int i = 1; i < lines.size(); i++) { // line 0 is the name, handled separately
                if (!sb.isEmpty()) sb.append(SEPARATOR);
                sb.append(render(lines.get(i), lang));
            }
        } catch (Throwable ignored) {
            // Some mods throw from tooltips in certain states; ignore
        }
        String result = fold(sb.toString());
        TOOLTIP_CACHE.put(stack, result);
        return result;
    }

    private static void buildReverse(Minecraft mc, String selected, Language secondaryLang) {
        // Not using Language.getInstance(): other mods may replace it, so it is not necessarily a ClientLanguage
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

    /** Loads the language tables only if they were never loaded (normally done on resource reload) */
    public static void ensureLoaded() {
        if (!loaded) reloadSecondaryLanguage();
    }

    /**
     * Called on resource reload (startup, language change, F3+T) to reload the current and secondary (English) language tables.
     * Skipped when the current language is already English, to avoid duplicate indexing.
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
     * Returns the original text plus its other-language versions (when different). Used for indexing; the result is folded afterwards with {@link #fold(String)}.
     * <p>
     * Besides English, the text is also re-rendered with our own copy of the current language: mods such as Untranslated Items
     * display item names in English, so the on-screen text is not Chinese, yet it should still be searchable in Chinese.
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

    /** Renders a component's text with the given language table without touching the game's current language. */
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
