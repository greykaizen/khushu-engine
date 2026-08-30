package com.khushu.engine.mushaf

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Capability entry point for mushaf glyph-atlas layout — the pure
 * computation behind QuranApp-style pixel-perfect page rendering.
 *
 * Inputs are explicit value objects ([AtlasSpec], [GlyphPlacement]); the
 * engine never reads bundles, files, or networks. Content-side specs live in
 * khushu-data-api (`inventory/atlas/{script}/6x.zip`) and are handed in by
 * the host.
 *
 * Pipeline summary (RTL typesetting):
 * 1. [measureWordWidthPx] — word advance width from glyph placements.
 * 2. [fitPageScale] — one uniform font scale per page so justified
 *    (`centered = false`) lines fit the content width (median-fill strategy).
 * 3. [fitLineShrink] — per-line shrink-only safety valve.
 * 4. [layoutWord] / [layoutAyah] — concrete pixel positions for every glyph.
 */
object Mushaf {

    // ── Tuning defaults (ported from donor TextDecorator/QuranTextMeasurer) ─

    /** Line height as a multiple of font size for mushaf pages. */
    const val LINE_HEIGHT_MULTIPLIER: Float = 2f

    /** Lowest page font scale relative to the dimen-derived base size. */
    const val FONT_SCALE_AT_MIN_WIDTH: Float = 0.85f

    /** Highest page font scale relative to the dimen-derived base size. */
    const val FONT_SCALE_AT_MAX_WIDTH: Float = 1.5f

    /** Screen-width interpolation floor for [screenWidthScale]. */
    const val SCREEN_WIDTH_DP_MIN: Float = 260f

    /** Screen-width interpolation ceiling for [screenWidthScale]. */
    const val SCREEN_WIDTH_DP_MAX: Float = 600f

    /** Inter-word gap as a fraction of font size on centered lines. */
    const val CENTERED_GAP_FRACTION: Float = 0.22f

    /** Minimum inter-word gap (all lines), fraction of font size. */
    const val MIN_INTER_WORD_GAP_FRACTION: Float = 0.1f

    /** Floor for per-line shrink-only fitting. */
    const val LINE_SHRINK_MIN: Float = 0.16f

    /** A justified line is considered "wide" from this fill ratio on. */
    const val WIDE_LINE_FILL_THRESHOLD: Float = 0.82f

    // ── Measurement ─────────────────────────────────────────────────────────

    /** Throws on non-finite font sizes so NaN can never leak into a layout. */
    private fun requireFontSize(fontSizePx: Float, name: String = "fontSizePx") {
        if (!fontSizePx.isFinite() || fontSizePx <= 0f) {
            throw InvalidParameterException(name, "$fontSizePx", "must be finite and > 0")
        }
    }

    private fun requireFinite(name: String, v: Float) {
        if (!v.isFinite() || v < 0f) {
            throw InvalidParameterException(name, "$v", "must be finite and >= 0")
        }
    }

    /** Raw advance width of one word in font units. */
    fun wordWidthFu(placements: List<GlyphPlacement>): Double = placements.sumOf { it.xAdvanceFu }

    /** Word width in pixels at [fontSizePx] (advance sum scaled by units-per-em). */
    fun measureWordWidthPx(
        spec: AtlasSpec,
        placements: List<GlyphPlacement>,
        fontSizePx: Float,
    ): Float {
        requireFontSize(fontSizePx)
        return (wordWidthFu(placements) * fontSizePx / spec.font.unitsPerEm).toFloat()
    }

    /**
     * Line width in pixels given pre-measured word widths at one font size.
     * Centered lines use the larger of the centered gap and the minimum gap;
     * justified lines use the minimum gap.
     */
    fun measureLineWidthPx(
        wordWidthsPx: List<Float>,
        centered: Boolean,
        baseFontSizePx: Float,
    ): Float {
        var sum = wordWidthsPx.sum()
        if (wordWidthsPx.size > 1) {
            val centeredGap = baseFontSizePx * CENTERED_GAP_FRACTION
            val minGap = baseFontSizePx * MIN_INTER_WORD_GAP_FRACTION
            val gap = if (centered) max(centeredGap, minGap) else minGap
            sum += gap * (wordWidthsPx.size - 1)
        }
        return sum
    }

    /**
     * Suggested maximum page font scale for a screen: linear interpolation
     * between [FONT_SCALE_AT_MIN_WIDTH] (at [SCREEN_WIDTH_DP_MIN] dp) and
     * [FONT_SCALE_AT_MAX_WIDTH] (at [SCREEN_WIDTH_DP_MAX] dp).
     */
    fun screenWidthScale(lineInnerWidthDp: Float): Float {
        val w = lineInnerWidthDp.coerceIn(SCREEN_WIDTH_DP_MIN, SCREEN_WIDTH_DP_MAX)
        val t = (w - SCREEN_WIDTH_DP_MIN) / (SCREEN_WIDTH_DP_MAX - SCREEN_WIDTH_DP_MIN)
        return (FONT_SCALE_AT_MIN_WIDTH +
            t * (FONT_SCALE_AT_MAX_WIDTH - FONT_SCALE_AT_MIN_WIDTH))
            .coerceIn(FONT_SCALE_AT_MIN_WIDTH, FONT_SCALE_AT_MAX_WIDTH)
    }

    // ── Page fitting ────────────────────────────────────────────────────────

    /**
     * One uniform font scale for a whole page: for every justified line that
     * already fills at least [WIDE_LINE_FILL_THRESHOLD] of the content width,
     * the width ratio `contentWidth / measuredWidth` is collected and the
     * MEDIAN adopted, clamped to `[FONT_SCALE_AT_MIN_WIDTH,
     * min(fallbackScale, FONT_SCALE_AT_MAX_WIDTH)]`. Returns
     * [fallbackScale] unchanged when no line qualifies.
     *
     * [lines] are measured at the (un-capped) base font size.
     */
    fun fitPageScale(
        lines: List<LineMeasure>,
        contentWidthPx: Float,
        baseFontSizePx: Float,
        fallbackScale: Float,
    ): Float {
        val width = contentWidthPx.coerceAtLeast(1f)
        val ratios = ArrayList<Float>()
        for (line in lines) {
            if (line.wordWidthsPx.isEmpty()) continue
            val measured = measureLineWidthPx(line.wordWidthsPx, line.centered, baseFontSizePx)
                .coerceAtLeast(1f)
            val fill = measured / width
            if (!line.centered && fill >= WIDE_LINE_FILL_THRESHOLD) {
                ratios += (width / measured).coerceAtLeast(0f)
            }
        }
        if (ratios.isEmpty()) return fallbackScale
        val median = median(ratios.sorted())
        val cap = min(fallbackScale, FONT_SCALE_AT_MAX_WIDTH)
        return median.coerceIn(FONT_SCALE_AT_MIN_WIDTH, cap)
    }

    /**
     * Shrink-only multiplier so a measured line fits [maxLineWidthPx].
     * Never enlarges; with [bounded] `false` always returns 1.
     */
    fun fitLineShrink(measuredWidthPx: Float, maxLineWidthPx: Float, bounded: Boolean): Float {
        if (!bounded) return 1f
        if (measuredWidthPx <= maxLineWidthPx) return 1f
        return (maxLineWidthPx / measuredWidthPx.coerceAtLeast(1f)).coerceAtLeast(LINE_SHRINK_MIN)
    }

    /** Standard mushaf line height for a font size. */
    fun lineHeightPx(fontSizePx: Float): Float = fontSizePx * LINE_HEIGHT_MULTIPLIER

    // ── Glyph placement ─────────────────────────────────────────────────────

    /**
     * Places every glyph of one word at its pixel offset, pen-based, in
     * visual order (left-to-right pixel space; the caller lines words up
     * right-to-left).
     *
     * Glyph ids missing from [AtlasSpec.glyphs] are skipped for drawing but
     * still advance the pen — matching the donor rasterizer.
     */
    fun layoutWord(
        spec: AtlasSpec,
        placements: List<GlyphPlacement>,
        fontSizePx: Float,
    ): WordLayout {
        requireFontSize(fontSizePx)
        val fontScale = fontSizePx / spec.font.unitsPerEm
        val glyphScale = fontSizePx / spec.ppem
        val ascenderFu = spec.font.ascenderFu
        val heightFu = spec.font.heightFu
        val fallbackHeightPx = if (heightFu > 0) heightFu * fontScale else fontSizePx
        val baselineY = if (ascenderFu > 0) {
            ascenderFu * fontScale
        } else {
            fallbackHeightPx * 0.8f
        }

        val prepared = ArrayList<PlacedGlyph>(placements.size)
        val skipped = ArrayList<Int>()
        var currentX = 0f
        for (p in placements) {
            val g = spec.glyphs[p.glyphId]
            if (g != null) {
                val x = currentX + (p.xOffsetFu * fontScale).toFloat() + g.bearingX * glyphScale
                val y = baselineY - (p.yOffsetFu * fontScale).toFloat() - g.bearingY * glyphScale
                prepared += PlacedGlyph(
                    glyphId = p.glyphId,
                    textureIndex = g.rect.textureIndex,
                    srcX = g.rect.x,
                    srcY = g.rect.y,
                    srcW = g.rect.w,
                    srcH = g.rect.h,
                    dstX = x,
                    dstY = y,
                    dstW = g.rect.w * glyphScale,
                    dstH = g.rect.h * glyphScale,
                )
            } else {
                skipped += p.glyphId
            }
            currentX += (p.xAdvanceFu * fontScale).toFloat()
        }

        val widthPx = if (prepared.isEmpty()) {
            max(fontSizePx * 0.35f, fontSizePx * 0.4f)
        } else {
            currentX
        }

        val tightMinY: Float
        val tightHeightPx: Float
        if (prepared.isEmpty()) {
            tightMinY = 0f
            tightHeightPx = fallbackHeightPx
        } else {
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (d in prepared) {
                minY = min(minY, d.dstY)
                maxY = max(maxY, d.dstY + d.dstH)
            }
            tightMinY = minY
            tightHeightPx = (maxY - minY).coerceAtLeast(1f)
        }

        return WordLayout(widthPx, tightMinY, tightHeightPx, prepared, skipped)
    }

    /**
     * Full line composition: lays words out right-to-left from the line's
     * right edge, vertically centered by tight extents within
     * [maxOf] line height. Returns word placements relative to the line
     * origin (x=0 at the line's left content edge).
     */
    fun layoutLine(
        spec: AtlasSpec,
        words: List<List<GlyphPlacement>>,
        fontSizePx: Float,
        lineHeightPx: Float,
        wordGapPx: Float,
    ): LineLayout {
        requireFontSize(fontSizePx)
        requireFinite("lineHeightPx", lineHeightPx)
        requireFinite("wordGapPx", wordGapPx)
        val layouts = words.map { layoutWord(spec, it, fontSizePx) }
        return composeLine(layouts, lineHeightPx, wordGapPx)
    }

    /**
     * Lays out one ayat (word list) with optional greedy wrapping at
     * [maxLineWidthPx] (`0` = single line). Returns absolute pixel positions
     * within a canvas sized [AyahLayout.widthPx] x [AyahLayout.heightPx] —
     * hosts blit the glyph source rects at those destinations.
     */
    fun layoutAyah(
        spec: AtlasSpec,
        words: List<List<GlyphPlacement>>,
        fontSizePx: Float,
        lineHeightPx: Float,
        wordGapPx: Float,
        maxLineWidthPx: Float = 0f,
    ): AyahLayout {
        requireFontSize(fontSizePx)
        requireFinite("lineHeightPx", lineHeightPx)
        requireFinite("wordGapPx", wordGapPx)
        if (maxLineWidthPx < 0f || !maxLineWidthPx.isFinite()) {
            throw InvalidParameterException("maxLineWidthPx", "$maxLineWidthPx", "must be finite and >= 0")
        }
        if (words.isEmpty()) return AyahLayout(0, 0, emptyList(), emptyList())

        val layouts = words.map { layoutWord(spec, it, fontSizePx) }
        val lines = if (maxLineWidthPx > 0f) {
            wrapWords(layouts, wordGapPx, maxLineWidthPx)
        } else {
            listOf(layouts)
        }
        val interLineGapPx = max(lineHeightPx * 0.2f, wordGapPx * 0.5f)

        val composed = lines.map { composeLine(it, lineHeightPx, wordGapPx) }
        val widthPx = composed.maxOf { it.widthPx }.roundToInt().coerceAtLeast(1)

        var totalHeight = 0f
        for (line in composed) totalHeight += line.boxHeightPx
        if (composed.size > 1) totalHeight += interLineGapPx * (composed.size - 1)
        val heightPx = totalHeight.roundToInt().coerceAtLeast(1)

        val skippedAll = layouts.flatMap { it.skippedGlyphIds }.distinct()

        val ayahLines = ArrayList<AyahLine>(composed.size)
        val allGlyphs = ArrayList<PlacedGlyph>()
        var yLine = 0f

        composed.forEachIndexed { i, line ->
            val offsetX = (widthPx - line.widthPx) / 2f
            val adjusted = line.words.flatMap { word ->
                word.glyphs.map { g ->
                    g.copy(dstX = g.dstX + offsetX, dstY = g.dstY + yLine)
                }
            }
            ayahLines += AyahLine(
                index = i,
                widthPx = line.widthPx,
                topPx = yLine,
                boxHeightPx = line.boxHeightPx,
                glyphs = adjusted,
            )
            allGlyphs += adjusted
            yLine += line.boxHeightPx
            if (i != composed.lastIndex) yLine += interLineGapPx
        }

        return AyahLayout(widthPx, heightPx, ayahLines, allGlyphs, skippedAll)
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun composeLine(
        layouts: List<WordLayout>,
        lineHeightPx: Float,
        wordGapPx: Float,
    ): LineLayout {
        if (layouts.isEmpty()) return LineLayout(0f, lineHeightPx, lineHeightPx, emptyList())
        val contentW = layouts.sumOf { it.widthPx.toDouble() }.toFloat() +
            (layouts.size - 1) * wordGapPx
        val maxTight = layouts.maxOf { it.tightHeightPx }
        val boxHeight = max(lineHeightPx, maxTight).coerceAtLeast(1f)

        val words = ArrayList<LineWord>(layouts.size)
        var cursorX = contentW
        layouts.forEachIndexed { i, layout ->
            cursorX -= layout.widthPx
            val verticalInset = ((boxHeight - layout.tightHeightPx) / 2f).coerceAtLeast(0f)
            val dy = verticalInset - layout.tightMinYPx
            val glyphs = layout.glyphs.map { g ->
                g.copy(dstX = g.dstX + cursorX, dstY = g.dstY + dy)
            }
            words += LineWord(
                wordIndex = i,
                leftPx = cursorX,
                topPx = dy,
                widthPx = layout.widthPx,
                tightMinYPx = layout.tightMinYPx,
                tightHeightPx = layout.tightHeightPx,
                glyphs = glyphs,
            )
            if (i != layouts.lastIndex) cursorX -= wordGapPx
        }
        return LineLayout(contentW, boxHeight, boxHeight, words)
    }

    private fun wrapWords(
        layouts: List<WordLayout>,
        wordGapPx: Float,
        maxLineWidthPx: Float,
    ): List<List<WordLayout>> {
        if (layouts.isEmpty()) return emptyList()
        val lines = ArrayList<ArrayList<WordLayout>>()
        var current = ArrayList<WordLayout>()
        var currentW = 0f
        for (layout in layouts) {
            val extra = if (current.isEmpty()) 0f else wordGapPx
            val need = layout.widthPx + extra
            if (current.isNotEmpty() && currentW + need > maxLineWidthPx) {
                lines += current
                current = ArrayList()
                currentW = 0f
            }
            current += layout
            currentW = if (current.size == 1) layout.widthPx else currentW + extra + layout.widthPx
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun median(sorted: List<Float>): Float {
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }
}
