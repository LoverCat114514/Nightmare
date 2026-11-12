package com.abrah.nightmare.reverse

/**
 * ⭐⭐⭐ Module 4a — system prompts for the two reverse-output styles.
 *
 * MOSS_LAYOUT must produce JSON that survives [com.abrah.nightmare.MossLayoutCompiler]:
 *   - width/height: positive multiples of 16;
 *   - bbox_coord_space = "abs", bbox_format = "xyxy" (the DRAFT form — the
 *     compiler itself converts to abs_centralized/yxyx);
 *   - items[].label strictly one of character / object / text;
 *   - every item needs a non-empty caption + a 4-number bbox [x1,y1,x2,y2]
 *     with 0 <= x1 < x2 <= width, 0 <= y1 < y2 <= height;
 *   - text items additionally need a non-empty "text" field;
 *   - summary.caption always present (empty string is legal).
 *
 * The sanitizer ([MossJsonSanitizer]) repairs the near-misses; the prompt
 * exists to keep the model from producing gross violations in the first place.
 */
object ReversePromptTemplates {

    val MOSS_LAYOUT: String = """
You are a layout-prompt engineer. Analyse the given image and return ONE JSON
object describing its composition for an image-generation layout system.

Return ONLY the JSON object — no markdown fences, no commentary.

Required shape (all fields mandatory):
{
  "width": <int, positive, multiple of 16>,
  "height": <int, positive, multiple of 16>,
  "bbox_coord_space": "abs",
  "bbox_format": "xyxy",
  "summary": { "caption": "<one English sentence describing the whole scene>" },
  "items": [
    {
      "label": "character" | "object" | "text",
      "caption": "<English description of this element>",
      "bbox": [<x1>, <y1>, <x2>, <y2>],
      "text": "<only for label=text: the literal text content, non-empty>"
    }
  ]
}

Hard rules:
- bbox is absolute pixels in xyxy order: x1 >= 0, y1 >= 0, x1 < x2 <= width,
  y1 < y2 <= height. Never use percentages or normalized [0..1] values.
- width and height MUST be multiples of 16 (e.g. 1024, 768). Pick values close
  to the image's aspect ratio.
- label is EXACTLY one of: character, object, text. Nothing else.
- Every item MUST have a caption (English, non-empty) and a bbox.
- Only items with label "text" have a "text" field, and it must be non-empty.
- Background scenery goes into summary.caption; do NOT create an item for it.
- Keep 1-8 items: the main characters, key objects, and any visible text.
- Output plain JSON. Do not wrap it in ```json fences. Do not add keys.
""".trimIndent()

    val NATURAL: String = """
You are an expert prompt engineer for image-generation models (Stable
Diffusion / NovelAI / Midjourney style). Study the given image carefully and
write the prompt that would reproduce it.

Output ONLY the prompt text — one paragraph, no headings, no explanations,
no markdown.

Guidelines:
- Lead with the subject: character count, appearance, clothing, pose,
  expression, what they are doing.
- Then the setting and background, lighting, colour mood.
- Then composition/camera terms (e.g. close-up, from above, dutch angle) and
  medium/style tags (e.g. anime style, watercolor, photorealistic).
- Then quality tags (e.g. best quality, highly detailed, masterpiece).
- Comma-separated English tags/fragments are fine; keep it under 120 words.
- Describe what is VISIBLE. Do not invent elements that are not in the image.
- If the image contains visible text, include it verbatim.
""".trimIndent()
}
