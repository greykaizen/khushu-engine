package com.khushu.engine.mushaf

/**
 * Typographic metrics of the mushaf script font, in font units (fu).
 * Mirrors the `font` block of an atlas bundle `meta.json`.
 */
data class AtlasFontMetrics(
    val unitsPerEm: Int,
    val ascenderFu: Int,
    val descenderFu: Int,
    val heightFu: Int = ascenderFu - descenderFu,
    val lineGapFu: Int = 0,
)

/** Pixel rectangle of one glyph inside an atlas texture page. */
data class GlyphSrcRect(
    val textureIndex: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/**
 * One pre-rendered glyph: its texture rectangle plus FreeType-style metrics.
 * `bearingX`/`bearingY`/`advance` are measured at the bundle's ppem.
 */
data class GlyphMetrics(
    val rect: GlyphSrcRect,
    val bearingX: Int = 0,
    val bearingY: Int = 0,
    val advance: Double = 0.0,
)

/**
 * Everything needed to place glyphs of one atlas bundle — the computation
 * half of a glyph-atlas zip (`meta.json` font metrics + `atlas.json` glyph
 * table). Content comes from khushu-data-api `inventory/atlas/`; the engine
 * itself is IO-free and owns no bundles.
 */
data class AtlasSpec(
    val font: AtlasFontMetrics,
    /** Pixels-per-em the glyph texture was rasterized at (e.g. 192 at 6x). */
    val ppem: Int,
    /** glyph id → metrics. Missing ids are skipped during placement. */
    val glyphs: Map<Int, GlyphMetrics>,
)

/**
 * One glyph placement within a word: glyph id + positional parameters in
 * font units. `xAdvanceFu` moves the pen for the NEXT glyph; `xOffsetFu` /
 * `yOffsetFu` displace this glyph relative to the current pen position.
 */
data class GlyphPlacement(
    val glyphId: Int,
    val xAdvanceFu: Double = 0.0,
    val yAdvanceFu: Double = 0.0,
    val xOffsetFu: Double = 0.0,
    val yOffsetFu: Double = 0.0,
)

/** A glyph positioned in pixel space — source rect in the atlas + destination rect on screen. */
data class PlacedGlyph(
    val glyphId: Int,
    val textureIndex: Int,
    val srcX: Int,
    val srcY: Int,
    val srcW: Int,
    val srcH: Int,
    val dstX: Float,
    val dstY: Float,
    val dstW: Float,
    val dstH: Float,
)

/** Laid-out word: advance width plus its placed glyphs + tight vertical extents. */
data class WordLayout(
    val widthPx: Float,
    val tightMinYPx: Float,
    val tightHeightPx: Float,
    val glyphs: List<PlacedGlyph>,
)

/** One output line of an ayah layout pass: words placed right-to-left. */
data class AyahLine(
    val index: Int,
    val widthPx: Float,
    val topPx: Float,
    val boxHeightPx: Float,
    val glyphs: List<PlacedGlyph>,
)

/** Full layout of one ayah (possibly wrapped): canvas size + all glyphs. */
data class AyahLayout(
    val widthPx: Int,
    val heightPx: Int,
    val lines: List<AyahLine>,
    val glyphs: List<PlacedGlyph>,
)

/** Pre-measured line input for [Mushaf.fitPageScale]: word widths at base font size. */
data class LineMeasure(
    val centered: Boolean,
    val wordWidthsPx: List<Float>,
)

/** Result of [Mushaf.layoutLine]: per-word placement + line metrics. */
data class LineLayout(
    val widthPx: Float,
    val boxHeightPx: Float,
    val baselineYPx: Float,
    val words: List<LineWord>,
)

/** One word inside a [LineLayout], positioned right-to-left from the line origin. */
data class LineWord(
    val wordIndex: Int,
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val tightMinYPx: Float,
    val tightHeightPx: Float,
    val glyphs: List<PlacedGlyph>,
)
