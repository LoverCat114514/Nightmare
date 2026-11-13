package com.abrah.nightmare;

import org.json.JSONArray;
import org.json.JSONObject;
/**
 * MOSS-Image layout compiler — faithful port of Comfyui-Moss-LayoutPrompt layout.py
 * (abs/xyxy draft -> validated, centered yxyx compiled layout prompt).
 * Pure java + org.json (android runtime has it). Never throws: every entry returns null
 * on any problem, and a null result means "use the original prompt unchanged".
 */
public final class MossLayoutCompiler {
    private MossLayoutCompiler() {}
    /**
     * Entry used by the sampler ([SdSampler.render]) when its MOSS-only layout switch is on.
     * Returns the compiled prompt, or null to pass the original through unchanged (plain
     * prose, an already-compiled prompt, or anything malformed — the run must never die here).
     * [w]/[h] are the sampler's current width/height widgets: a draft without its own
     * width/height is centred on them, so the picture the user asked for is the space
     * the boxes are placed in.
     */
    public static String compileDraft(String draft, String wRaw, String hRaw) {
        try {
            if (draft == null) return null;
            draft = draft.trim();
            if (draft.isEmpty()) return null;
            // Already-compiled prompt? Pass through untouched (idempotence guard).
            if (draft.contains("\"abs_centralized\"") || draft.contains("\"yxyx\"")) return null;
            // No brace at all -> an ordinary prose prompt, untouched.
            if (draft.indexOf('{') < 0) return null;
            int w = parseIntSafe(wRaw), h = parseIntSafe(hRaw);
            if (w <= 0) w = 1024;
            if (h <= 0) h = 1024;
            return compile(draft, w, h);
        } catch (Throwable t) {
            return null;
        }
    }
    /** The node's width/height widget is a raw string: null or garbage -> the 1024 default. */
    private static int parseIntSafe(String s) {
        try { return s == null ? 0 : Integer.parseInt(s.trim()); } catch (Throwable t) { return 0; }
    }

    /** Returns compiled prompt, or null if the text is not a layout draft (plain text prompt). */
    public static String compile(String text, int w, int h) {
        try {
            JSONObject raw = parseLoose(text);
            if (raw == null) return null; // not JSON at all -> normal prompt
            JSONObject d = raw;
            if (d.has("elements")) {
                d = sceneToDraft(d, w, h);
                if (d == null) return null;
            }
            if (!d.has("items")) return null; // no items key -> treat as plain prompt
            JSONArray items = d.optJSONArray("items");
            if (items == null) return null;

            int cw = d.optInt("width", w), ch = d.optInt("height", h);
            if (cw <= 0 || ch <= 0 || cw % 16 != 0 || ch % 16 != 0) return null;

            // ⭐ v2 normalized draft (2026-11-13): the reverse template emits
            // bboxes on a [0,1000] SCALE with NO width/height and a mandatory
            // [0,0,1000,1000] photo_background marker. The scale is decoupled
            // from the canvas on purpose — rescale every bbox onto the target
            // canvas (the sampler's current widgets) before centering, so the
            // reverse-computed composition adapts to whatever size the user
            // generates at. Detection requires BOTH the missing canvas and the
            // marker: an absolute-pixel draft on a ≤1000px canvas would
            // otherwise be misjudged and double-scaled.
            if (!d.has("width") && !d.has("height")) {
                JSONArray probe = d.optJSONArray("items");
                boolean marker = false;
                boolean within = probe != null && probe.length() > 0;
                for (int i = 0; probe != null && i < probe.length(); i++) {
                    JSONObject pit = probe.optJSONObject(i);
                    if (pit == null) continue;
                    JSONArray pb = pit.optJSONArray("bbox");
                    if (pb == null || pb.length() != 4) { within = false; break; }
                    double px1 = pb.optDouble(0), py1 = pb.optDouble(1);
                    double px2 = pb.optDouble(2), py2 = pb.optDouble(3);
                    if (px1 == 0 && py1 == 0 && px2 == 1000 && py2 == 1000 &&
                        pit.optString("caption", "").trim().startsWith("photo_background")) marker = true;
                    if (px1 < 0 || py1 < 0 || px2 > 1000 || py2 > 1000) { within = false; break; }
                }
                if (marker && within) {
                    for (int i = 0; i < probe.length(); i++) {
                        JSONObject pit = probe.optJSONObject(i);
                        if (pit == null) continue;
                        JSONArray pb = pit.optJSONArray("bbox");
                        if (pb == null || pb.length() != 4) continue;
                        JSONArray scaled = new JSONArray()
                                .put(pb.optDouble(0) * cw / 1000.0)
                                .put(pb.optDouble(1) * ch / 1000.0)
                                .put(pb.optDouble(2) * cw / 1000.0)
                                .put(pb.optDouble(3) * ch / 1000.0);
                        pit.put("bbox", scaled);
                    }
                    d.put("width", cw).put("height", ch);
                }
            }

            String cs = d.optString("bbox_coord_space", "abs"), bf = d.optString("bbox_format", "xyxy");
            if (!"abs".equals(cs) || !"xyxy".equals(bf)) return null; // looks already-compiled / alien

            JSONObject summary = d.optJSONObject("summary");
            if (summary == null) summary = new JSONObject().put("caption", "");

            if (items.length() == 0) {
                return summary.optString("caption", "").trim(); // plugin behaviour: plain caption
            }

            JSONArray out = new JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) return null;
                String label = it.optString("label", "");
                if (!"object".equals(label) && !"character".equals(label) && !"text".equals(label)) return null;
                if (!it.has("caption") || !it.has("bbox")) return null;
                if ("text".equals(label) && !it.has("text")) return null;
                if ("text".equals(label) && it.optString("text", "").trim().isEmpty()) return null;
                JSONArray bbox = it.optJSONArray("bbox");
                if (bbox == null || bbox.length() != 4) return null;
                JSONArray centered = centered(bbox, cw, ch);
                if (centered == null) return null;
                JSONObject copy = new JSONObject(it.toString());
                copy.remove("color"); // editor-only field
                copy.put("bbox", centered);
                out.put(copy);
            }
            JSONObject prompt = new JSONObject()
                    .put("summary", summary)
                    .put("items", out)
                    .put("bbox_coord_space", "abs_centralized")
                    .put("bbox_format", "yxyx");
            return prompt.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** layout.py _centered: abs xyxy -> centered yxyx with round9/floor/ceil semantics. Integers stay integers. */
    private static JSONArray centered(JSONArray b, int w, int h) {
        try {
            double x1 = b.getDouble(0), y1 = b.getDouble(1), x2 = b.getDouble(2), y2 = b.getDouble(3);
            if (!(x1 >= 0 && x1 < x2 && x2 <= w && y1 >= 0 && y1 < y2 && y2 <= h)) return null;
            long o1 = (long) Math.floor(r9(y1)) - h / 2;
            long o2 = (long) Math.floor(r9(x1)) - w / 2;
            long o3 = (long) Math.ceil(r9(y2)) - h / 2;
            long o4 = (long) Math.ceil(r9(x2)) - w / 2;
            if (!(o1 < o3 && o2 < o4)) return null;
            return new JSONArray().put(o1).put(o2).put(o3).put(o4);
        } catch (Throwable t) {
            return null;
        }
    }

    /** round to 9 decimals, like layout.py's round(v, 9). */
    private static double r9(double v) {
        return Math.round(v * 1e9) / 1e9;
    }

    /** Balanced {...} extraction from prose-wrapped text (layout.py _json_objects). */
    private static JSONObject parseLoose(String text) {
        try { return new JSONObject(text); } catch (Throwable ignored) {}
        String best = null;
        int n = text.length();
        for (int s = 0; s < n; s++) {
            if (text.charAt(s) != '{') continue;
            int depth = 0; boolean inStr = false, esc = false;
            for (int i = s; i < n; i++) {
                char c = text.charAt(i);
                if (inStr) {
                    if (esc) esc = false;
                    else if (c == '\\') esc = true;
                    else if (c == '"') inStr = false;
                    continue;
                }
                if (c == '"') inStr = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String cand = text.substring(s, i + 1);
                        JSONObject o = tryParse(cand);
                        if (o != null && (o.has("summary") || o.has("items") || o.has("elements"))) return o;
                        if (o != null && best == null) best = cand;
                        break;
                    }
                }
            }
        }
        return best == null ? null : tryParse(best);
    }

    private static JSONObject tryParse(String s) {
        try { return new JSONObject(s); } catch (Throwable t) { return null; }
    }

    /** Legacy MOSS-Image scene schema -> draft (bbox centered space has y before x). */
    private static JSONObject sceneToDraft(JSONObject v, int w, int h) {
        try {
            JSONArray els = v.optJSONArray("elements");
            if (els == null) return null;
            int cw = v.optInt("width", w), ch = v.optInt("height", h);
            boolean centeredSpace = !"abs".equals(v.optString("bbox_coord_space", ""));
            JSONArray items = new JSONArray();
            for (int i = 0; i < els.length(); i++) {
                JSONObject e = els.optJSONObject(i);
                if (e == null) continue;
                JSONArray b = e.optJSONArray("bbox");
                if (b == null || b.length() != 4) continue;
                String type = e.optString("type", "").trim().toLowerCase();
                String label = "person".equals(type) || "character".equals(type) ? "character"
                        : "text".equals(type) ? "text" : "object";
                StringBuilder cap = new StringBuilder();
                for (String k : new String[]{"identity", "shape", "note"}) {
                    String t = e.optString(k, "").trim();
                    if (!t.isEmpty()) { if (cap.length() > 0) cap.append(' '); cap.append(t); }
                }
                JSONObject it = new JSONObject().put("label", label).put("caption", cap.toString());
                if ("text".equals(label)) {
                    String t = e.optString("text", "").trim();
                    if (t.isEmpty()) t = e.optString("identity", "").trim();
                    if (!t.isEmpty()) it.put("text", t); else it.put("label", "object");
                }
                if (centeredSpace) {
                    double by1 = b.getDouble(0), bx1 = b.getDouble(1), by2 = b.getDouble(2), bx2 = b.getDouble(3);
                    it.put("bbox", new JSONArray()
                            .put(bx1 + cw / 2.0).put(by1 + ch / 2.0)
                            .put(bx2 + cw / 2.0).put(by2 + ch / 2.0));
                } else {
                    it.put("bbox", new JSONArray()
                            .put(b.getDouble(0)).put(b.getDouble(1)).put(b.getDouble(2)).put(b.getDouble(3)));
                }
                items.put(it);
            }
            StringBuilder cap = new StringBuilder();
            for (String k : new String[]{"scene_type", "gen_brief"}) {
                String t = v.optString(k, "").trim();
                if (!t.isEmpty()) { if (cap.length() > 0) cap.append(". "); cap.append(t); }
            }
            // fit items into canvas (plugin _fit_items, centre scale)
            double hw = 0, hh = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONArray b = items.getJSONObject(i).optJSONArray("bbox");
                if (b == null) continue;
                hw = Math.max(hw, Math.max(Math.abs(b.optDouble(0) - cw / 2.0), Math.abs(b.optDouble(2) - cw / 2.0)));
                hh = Math.max(hh, Math.max(Math.abs(b.optDouble(1) - ch / 2.0), Math.abs(b.optDouble(3) - ch / 2.0)));
            }
            if (hw > 0 && hh > 0 && (hw > cw / 2.0 || hh > ch / 2.0)) {
                double sc = Math.min((cw / 2.0) / hw, (ch / 2.0) / hh);
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    JSONArray b = it.optJSONArray("bbox");
                    if (b == null) continue;
                    JSONArray nb = new JSONArray();
                    for (int k = 0; k < 4; k++) {
                        double val = b.optDouble(k);
                        nb.put(k % 2 == 0 ? (val - cw / 2.0) * sc + cw / 2.0
                                          : (val - ch / 2.0) * sc + ch / 2.0);
                    }
                    it.put("bbox", nb);
                }
            }
            return new JSONObject()
                    .put("width", cw).put("height", ch)
                    .put("bbox_coord_space", "abs").put("bbox_format", "xyxy")
                    .put("summary", new JSONObject().put("caption", cap.toString()))
                    .put("items", items);
        } catch (Throwable t) {
            return null;
        }
    }
}