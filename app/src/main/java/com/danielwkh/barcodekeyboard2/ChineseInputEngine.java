package com.danielwkh.barcodekeyboard2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline candidate indexes built from the bundled Rime text dictionaries. */
final class ChineseInputEngine {
    enum Mode { ENGLISH, PINYIN, STROKE, QUICK }
    private static final String COMMON = "的一是不了在人有我他這中大來上國個到說們為子和你地出道也時年得就那要下以生會自着去之過家學對可她里後小麼心多天而能好都然沒日於起還發成事只作當想看文無開手十用主行方又如前所本見經頭面公同三已老從動兩長知民樣現分將外但身些與高意進把法此實回二理美點月明其種正關問力它相高次好道區今經定化義使說直電備者制表水行社數記求結解政任認報調資夜則呢發更幾處放花很當省間候門立不再見色體聲城場業系通真建可廠特金品即動產葉職後臺熱難氣個爾條風辦隊應費業";
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<Mode, Map<String, List<String>>> indexes = new HashMap<>();
    private final Map<Mode, Boolean> loading = new HashMap<>();

    ChineseInputEngine(Context context) { app = context.getApplicationContext(); }

    synchronized void ensureLoaded(Mode mode, Runnable complete) {
        if (mode == Mode.ENGLISH || indexes.containsKey(mode)) { if (complete != null) main.post(complete); return; }
        if (Boolean.TRUE.equals(loading.get(mode))) return;
        loading.put(mode, true);
        worker.execute(() -> {
            Map<String, List<String>> result = new HashMap<>();
            String file = mode == Mode.PINYIN ? "luna_pinyin.dict.yaml" : mode == Mode.STROKE ? "stroke.dict.yaml" : "cangjie5.base.dict.yaml";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(app.getAssets().open("dictionaries/" + file), StandardCharsets.UTF_8), 64 * 1024)) {
                boolean data = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!data) { if (line.equals("...")) data = true; continue; }
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    int tab = line.indexOf('\t');
                    if (tab <= 0 || tab == line.length() - 1) continue;
                    String text = line.substring(0, tab).trim();
                    String code = line.substring(tab + 1).trim().toLowerCase(Locale.ROOT);
                    if (text.isEmpty() || code.isEmpty()) continue;
                    if (mode == Mode.PINYIN) {
                        if (!code.matches("[a-zv ]+")) continue;
                        code = code.replace(" ", "");
                    } else if (mode == Mode.STROKE) {
                        if (!code.matches("[hspnz]+")) continue;
                    } else {
                        if (!code.matches("[a-z]+") || text.codePointCount(0, text.length()) != 1) continue;
                        code = code.length() == 1 ? code : "" + code.charAt(0) + code.charAt(code.length() - 1); // 速成：倉頡碼首尾碼；單根字保留單碼
                    }
                    if (code.length() > (mode == Mode.STROKE ? 32 : 32)) continue;
                    List<String> bucket = result.get(code);
                    if (bucket == null) { bucket = new ArrayList<>(); result.put(code, bucket); }
                    if (bucket.size() < 512 && !bucket.contains(text)) bucket.add(text);
                }
            } catch (Exception ignored) { }
            for (List<String> bucket : result.values()) bucket.sort(Comparator.comparingInt(ChineseInputEngine::priority));
            synchronized (ChineseInputEngine.this) { indexes.put(mode, result); loading.put(mode, false); }
            if (complete != null) main.post(complete);
        });
    }

    synchronized List<String> candidates(Mode mode, String code) {
        Map<String, List<String>> index = indexes.get(mode);
        if (index == null || code == null || code.isEmpty()) return new ArrayList<>();
        List<String> values = index.get(code.toLowerCase(Locale.ROOT));
        if (values != null) return new ArrayList<>(values.subList(0, Math.min(8, values.size())));
        if (mode != Mode.PINYIN || code.length() > 24) return new ArrayList<>();
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        composePinyin(index, code.toLowerCase(Locale.ROOT), 0, new StringBuilder(), combined, 0);
        return new ArrayList<>(combined);
    }

    private void composePinyin(Map<String, List<String>> index, String input, int offset, StringBuilder prefix, LinkedHashSet<String> out, int depth) {
        if (out.size() >= 8 || depth >= 12) return;
        if (offset == input.length()) { if (prefix.length() > 0) out.add(prefix.toString()); return; }
        for (int end = input.length(); end > offset && out.size() < 8; end--) {
            List<String> chars = index.get(input.substring(offset, end));
            if (chars == null) continue;
            int oldLength = prefix.length();
            for (int i = 0; i < Math.min(2, chars.size()) && out.size() < 8; i++) {
                prefix.append(chars.get(i));
                composePinyin(index, input, end, prefix, out, depth + 1);
                prefix.setLength(oldLength);
            }
        }
    }

    synchronized boolean isReady(Mode mode) { return mode == Mode.ENGLISH || indexes.containsKey(mode); }

    private static int priority(String candidate) {
        int index = COMMON.indexOf(candidate.isEmpty() ? '\0' : candidate.charAt(0));
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
