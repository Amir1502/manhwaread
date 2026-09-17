package com.manhwaread.core.vision

import kotlin.math.floor

/**
 * Вписывание текста в маску бабла (КОНТРАКТ ФАЗЫ 5 — сигнатуры закреплены в AGENTS.md).
 * Каскад деградаций: межстрочный интервал → перенос по слогам → межбуквенный → сжатие по X → клип.
 */
interface TextMeasurer {
    fun measureLine(text: String, sizePx: Float, letterSpacing: Float, scaleX: Float): Float
    fun lineHeight(sizePx: Float, lineSpacingMult: Float): Float
}

// Закреплённые значения по умолчанию (AGENTS.md); отрицательный -0.02f detekt не покрывает ignorePropertyDeclaration.
@Suppress("MagicNumber")
data class FitConfig(
    val minSizePx: Float = 14f,
    val maxSizePx: Float = 128f,
    val stepPx: Float = 1f,
    val paddingPx: Float = 6f,
    val lineSpacingStart: Float = 1.0f,
    val lineSpacingMin: Float = 0.9f,
    val letterSpacingMin: Float = -0.02f,
    val scaleXMin: Float = 0.75f,
    val maxCharsPerLine: Int = 40,
)

data class FitResult(
    val lines: List<String>,
    val sizePx: Float,
    val lineSpacingMult: Float,
    val letterSpacing: Float,
    val scaleX: Float,
    val overflow: Boolean,
    val degradations: Set<Degradation>,
)

enum class Degradation { LINE_SPACING, SYLLABLE_WRAP, LETTER_SPACING, SCALE_X, CLIPPED }

/**
 * Детерминированный оценочный измеритель для JVM-тестов и превью:
 * ширина символа ≈ charWidthFactor * sizePx * scaleX, letterSpacing — доля кегля.
 */
class ApproximateTextMeasurer(
    private val charWidthFactor: Float = 0.5f,
    private val lineHeightFactor: Float = 1.2f,
) : TextMeasurer {
    override fun measureLine(text: String, sizePx: Float, letterSpacing: Float, scaleX: Float): Float {
        if (text.isEmpty()) return 0f
        val perChar = sizePx * charWidthFactor * scaleX
        return text.length * perChar + (text.length - 1) * letterSpacing * sizePx
    }

    override fun lineHeight(sizePx: Float, lineSpacingMult: Float): Float = sizePx * lineHeightFactor * lineSpacingMult
}

/** Шаг каскада деградаций: параметры раскладки + накопленные деградации. */
private data class LadderStep(
    val lineSpacingMult: Float,
    val letterSpacing: Float,
    val scaleX: Float,
    val degradations: Set<Degradation>,
)

/** Неизменяемый контекст раскладки одного прогона (кегль + шаг каскада). */
private data class TypeContext(
    val mask: Mask,
    val measurer: TextMeasurer,
    val sizePx: Float,
    val step: LadderStep,
    val config: FitConfig,
    val lineH: Float,
)

/** Результат раскладки слов в строки на фиксированном кегле. */
private data class WrapOutcome(
    val lines: List<String>,
    val totalHeight: Float,
    val syllableWrapUsed: Boolean,
)

/** Куски длинного слова + центр последней строки после нарезки. */
private data class ChunkOutcome(
    val chunks: List<String>,
    val lastCenterY: Float,
)

/**
 * Вписывает [text] в [mask]: подбирает максимальный кегль от [FitConfig.maxSizePx]
 * с шагом [FitConfig.stepPx], при нехватке места применяет каскад деградаций.
 * Не влез даже на минимальном кегле — overflow=true + CLIPPED (строки обрезаются по высоте).
 */
fun fit(text: String, mask: Mask, measurer: TextMeasurer, config: FitConfig = FitConfig()): FitResult {
    val words = tokenize(text)
    if (words.isEmpty()) {
        return FitResult(
            lines = emptyList(),
            sizePx = config.minSizePx,
            lineSpacingMult = config.lineSpacingStart,
            letterSpacing = 0f,
            scaleX = 1f,
            overflow = false,
            degradations = emptySet(),
        )
    }
    val availHeight = availableHeight(mask, config)
    val ladder = degradationLadder(config)
    val sizeStep = if (config.stepPx > 0f) config.stepPx else 1f
    var sizePx = config.maxSizePx
    while (sizePx >= config.minSizePx) {
        for (ladderStep in ladder) {
            val outcome = wrapLines(words, contextFor(mask, measurer, sizePx, ladderStep, config))
            if (outcome.totalHeight <= availHeight) {
                return FitResult(
                    lines = outcome.lines,
                    sizePx = sizePx,
                    lineSpacingMult = ladderStep.lineSpacingMult,
                    letterSpacing = ladderStep.letterSpacing,
                    scaleX = ladderStep.scaleX,
                    overflow = false,
                    degradations = ladderStep.degradations + syllableDegradation(outcome),
                )
            }
        }
        sizePx -= sizeStep
    }
    return clippedResult(words, mask, measurer, config, ladder.last(), availHeight)
}

/** Разбиение текста на слова; переводы строк и повторяющиеся пробелы — разделители. */
private fun tokenize(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun availableHeight(mask: Mask, config: FitConfig): Float =
    (mask.bounds.height() - 2 * config.paddingPx).coerceAtLeast(0f)

private fun availableWidth(mask: Mask, centerY: Float, config: FitConfig): Float =
    (mask.widthAt(centerY) - 2 * config.paddingPx).coerceAtLeast(0f)

private fun syllableDegradation(outcome: WrapOutcome): Set<Degradation> =
    if (outcome.syllableWrapUsed) setOf(Degradation.SYLLABLE_WRAP) else emptySet()

private fun contextFor(mask: Mask, measurer: TextMeasurer, sizePx: Float, step: LadderStep, config: FitConfig) =
    TypeContext(
        mask = mask,
        measurer = measurer,
        sizePx = sizePx,
        step = step,
        config = config,
        lineH = measurer.lineHeight(sizePx, step.lineSpacingMult),
    )

// Каскад: полный интервал → минимальный интервал → отрицательный letterSpacing → сжатие scaleX.
private fun degradationLadder(config: FitConfig): List<LadderStep> {
    val steps = mutableListOf(LadderStep(config.lineSpacingStart, 0f, 1f, emptySet()))
    val afterSpacing = if (config.lineSpacingMin < config.lineSpacingStart) {
        steps += LadderStep(config.lineSpacingMin, 0f, 1f, setOf(Degradation.LINE_SPACING))
        setOf(Degradation.LINE_SPACING)
    } else {
        emptySet()
    }
    val afterLetter = if (config.letterSpacingMin < 0f) {
        val degradations = afterSpacing + Degradation.LETTER_SPACING
        steps += LadderStep(config.lineSpacingMin, config.letterSpacingMin, 1f, degradations)
        degradations
    } else {
        afterSpacing
    }
    if (config.scaleXMin < 1f) {
        val degradations = afterLetter + Degradation.SCALE_X
        steps += LadderStep(config.lineSpacingMin, config.letterSpacingMin, config.scaleXMin, degradations)
    }
    return steps
}

/**
 * Жадный перенос слов по строкам: ширина строки берётся из маски по её центру
 * (эллипс сужается к полюсам). Слово шире строки режется посимвольно (SYLLABLE_WRAP).
 */
private fun wrapLines(words: List<String>, ctx: TypeContext): WrapOutcome {
    val lines = mutableListOf<String>()
    var syllableUsed = false
    var current = ""
    var lineCenterY = ctx.mask.bounds.top + ctx.config.paddingPx + ctx.lineH / 2f

    fun lineAccepts(candidate: String): Boolean {
        val width = ctx.measurer.measureLine(candidate, ctx.sizePx, ctx.step.letterSpacing, ctx.step.scaleX)
        val limit = availableWidth(ctx.mask, lineCenterY, ctx.config)
        return width <= limit && candidate.length <= ctx.config.maxCharsPerLine
    }

    fun flush() {
        if (current.isNotEmpty()) {
            lines += current
            current = ""
            lineCenterY += ctx.lineH
        }
    }

    fun placeWord(word: String) {
        if (lineAccepts(word)) {
            current = word
        } else {
            syllableUsed = true
            val outcome = chunkWord(word, ctx, lineCenterY)
            current = outcome.chunks.last()
            lines += outcome.chunks.dropLast(1)
            lineCenterY = outcome.lastCenterY
        }
    }

    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (lineAccepts(candidate)) {
            current = candidate
        } else {
            flush()
            placeWord(word)
        }
    }
    if (current.isNotEmpty()) lines += current
    return WrapOutcome(lines, lines.size * ctx.lineH, syllableUsed)
}

// Посимвольная нарезка длинного слова; гарантирует прогресс (минимум 1 символ на кусок).
private fun chunkWord(word: String, ctx: TypeContext, startY: Float): ChunkOutcome {
    val chunks = mutableListOf<String>()
    var rest = word
    var centerY = startY
    while (rest.isNotEmpty()) {
        val limit = availableWidth(ctx.mask, centerY, ctx.config)
        var take = 0
        while (take < rest.length && take + 1 <= ctx.config.maxCharsPerLine && chunkFits(rest, take + 1, limit, ctx)) {
            take++
        }
        if (take == 0) take = 1
        chunks += rest.take(take)
        rest = rest.drop(take)
        if (rest.isNotEmpty()) centerY += ctx.lineH
    }
    return ChunkOutcome(chunks, centerY)
}

private fun chunkFits(rest: String, count: Int, limit: Float, ctx: TypeContext): Boolean =
    ctx.measurer.measureLine(rest.take(count), ctx.sizePx, ctx.step.letterSpacing, ctx.step.scaleX) <= limit

// Текст не влез на минимальном кегле: обрезаем строки по высоте и помечаем overflow + CLIPPED.
private fun clippedResult(
    words: List<String>,
    mask: Mask,
    measurer: TextMeasurer,
    config: FitConfig,
    step: LadderStep,
    availHeight: Float,
): FitResult {
    val ctx = contextFor(mask, measurer, config.minSizePx, step, config)
    val outcome = wrapLines(words, ctx)
    val maxLines = if (ctx.lineH <= 0f) 1 else floor(availHeight / ctx.lineH).toInt().coerceAtLeast(1)
    val degradations = step.degradations + Degradation.CLIPPED + syllableDegradation(outcome)
    return FitResult(
        lines = outcome.lines.take(maxLines),
        sizePx = config.minSizePx,
        lineSpacingMult = step.lineSpacingMult,
        letterSpacing = step.letterSpacing,
        scaleX = step.scaleX,
        overflow = true,
        degradations = degradations,
    )
}
