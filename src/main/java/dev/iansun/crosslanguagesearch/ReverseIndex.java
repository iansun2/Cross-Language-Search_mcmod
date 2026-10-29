package dev.iansun.crosslanguagesearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 從兩份語言表建立的雙向反查：給一段已翻譯的純文字，找出另一種語言的版本。
 * <ul>
 * <li>整句：fold(中文) ↔ fold(英文)，兩個方向都收錄</li>
 * <li>格式字串：例如「 - 量子化：%s%%」，用前綴找候選範本，再用正規表示式取出參數代入英文</li>
 * </ul>
 * 建好之後不再修改，可在多執行緒下讀取。
 */
final class ReverseIndex {
    static final ReverseIndex EMPTY = new ReverseIndex(Map.of(), Map.of());

    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final int MIN_PREFIX = 2;
    private static final int MAX_PREFIX = 48;

    private final Map<String, String> exact;
    private final Map<String, List<Template>> templatesByPrefix;

    private ReverseIndex(Map<String, String> exact, Map<String, List<Template>> templatesByPrefix) {
        this.exact = exact;
        this.templatesByPrefix = templatesByPrefix;
    }

    boolean isEmpty() {
        return exact.isEmpty() && templatesByPrefix.isEmpty();
    }

    int exactSize() {
        return exact.size();
    }

    int templateCount() {
        return templatesByPrefix.values().stream().mapToInt(List::size).sum();
    }

    static ReverseIndex build(Map<String, String> primary, Map<String, String> secondary) {
        Map<String, Set<String>> collected = new HashMap<>(primary.size() * 2);
        Map<String, List<Template>> templates = new HashMap<>();
        for (Map.Entry<String, String> entry : primary.entrySet()) {
            String zh = entry.getValue();
            String en = secondary.get(entry.getKey());
            if (zh == null || en == null || zh.isEmpty() || en.isEmpty() || zh.equals(en)) continue;

            if (zh.indexOf('%') >= 0) {
                Template t = Template.parse(zh, en);
                if (t != null) templates.computeIfAbsent(t.prefix, k -> new ArrayList<>(1)).add(t);
                continue;
            }
            String fz = SearchText.fold(zh).trim();
            String fe = SearchText.fold(en).trim();
            if (fz.isEmpty() || fe.isEmpty() || fz.equals(fe)) continue;
            collected.computeIfAbsent(fz, k -> new LinkedHashSet<>(2)).add(fe);
            collected.computeIfAbsent(fe, k -> new LinkedHashSet<>(2)).add(fz);
        }
        Map<String, String> exact = new HashMap<>(collected.size());
        String sep = String.valueOf(SearchText.SEPARATOR);
        collected.forEach((k, v) -> exact.put(k, String.join(sep, v)));
        return new ReverseIndex(exact, templates);
    }

    /** 查一行已 fold、已 trim 的文字，回傳另一種語言（已 fold），查不到回傳 null */
    String lookupLine(String line) {
        String hit = exact.get(line);
        if (hit != null) return hit;
        if (templatesByPrefix.isEmpty()) return null;
        int max = Math.min(line.length(), MAX_PREFIX);
        for (int len = max; len >= MIN_PREFIX; len--) {
            List<Template> candidates = templatesByPrefix.get(line.substring(0, len));
            if (candidates == null) continue;
            for (Template t : candidates) {
                String translated = t.apply(line, this);
                if (translated != null) return translated;
            }
        }
        return null;
    }

    /** 參數本身也可能是翻譯過的文字（例如附魔名稱），能查到就一起換 */
    String lookupArg(String arg) {
        String hit = exact.get(arg.trim());
        if (hit == null) return arg;
        int sep = hit.indexOf(SearchText.SEPARATOR);
        return sep < 0 ? hit : hit.substring(0, sep);
    }

    private static final class Template {
        final String prefix;
        final Pattern pattern;
        final int[] argOrder;
        final List<Object> english; // String 為字面文字，Integer 為參數索引

        private Template(String prefix, Pattern pattern, int[] argOrder, List<Object> english) {
            this.prefix = prefix;
            this.pattern = pattern;
            this.argOrder = argOrder;
            this.english = english;
        }

        static Template parse(String zh, String en) {
            List<Object> zhParts = tokenize(zh);
            List<Object> enParts = tokenize(en);
            if (zhParts == null || enParts == null) return null;
            if (zhParts.isEmpty() || !(zhParts.get(0) instanceof String first)) return null;

            String prefix = SearchText.fold(first).stripLeading();
            if (prefix.length() < MIN_PREFIX) return null;
            if (prefix.length() > MAX_PREFIX) prefix = prefix.substring(0, MAX_PREFIX);

            StringBuilder regex = new StringBuilder();
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < zhParts.size(); i++) {
                Object part = zhParts.get(i);
                if (part instanceof String literal) {
                    String folded = SearchText.fold(literal);
                    if (i == 0) folded = folded.stripLeading();
                    if (i == zhParts.size() - 1) folded = folded.stripTrailing();
                    if (!folded.isEmpty()) regex.append(Pattern.quote(folded));
                } else {
                    regex.append("(.*?)");
                    order.add((Integer) part);
                }
            }
            if (order.isEmpty()) return null;

            List<Object> english = new ArrayList<>(enParts.size());
            for (Object part : enParts) english.add(part instanceof String s ? SearchText.fold(s) : part);
            return new Template(prefix, Pattern.compile(regex.toString(), Pattern.DOTALL),
                    order.stream().mapToInt(Integer::intValue).toArray(), english);
        }

        /** 拆成字面文字與參數索引；遇到不支援的格式回傳 null */
        private static List<Object> tokenize(String format) {
            List<Object> parts = new ArrayList<>();
            Matcher m = FORMAT.matcher(format);
            StringBuilder literal = new StringBuilder();
            int last = 0;
            int nextArg = 0;
            while (m.find()) {
                literal.append(format, last, m.start());
                String type = m.group(2);
                if ("%".equals(type)) {
                    literal.append('%');
                } else if ("s".equals(type)) {
                    if (!literal.isEmpty()) {
                        parts.add(literal.toString());
                        literal.setLength(0);
                    }
                    parts.add(m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : nextArg++);
                } else {
                    return null;
                }
                last = m.end();
            }
            literal.append(format, last, format.length());
            if (!literal.isEmpty()) parts.add(literal.toString());
            return parts;
        }

        String apply(String line, ReverseIndex index) {
            Matcher m = pattern.matcher(line);
            if (!m.matches()) return null;
            String[] args = new String[argOrder.length + 8];
            for (int i = 0; i < argOrder.length; i++) {
                int idx = argOrder[i];
                if (idx >= 0 && idx < args.length) args[idx] = index.lookupArg(m.group(i + 1));
            }
            StringBuilder sb = new StringBuilder();
            for (Object part : english) {
                if (part instanceof String s) sb.append(s);
                else {
                    int idx = (Integer) part;
                    if (idx >= 0 && idx < args.length && args[idx] != null) sb.append(args[idx]);
                }
            }
            return sb.toString().trim();
        }
    }
}
