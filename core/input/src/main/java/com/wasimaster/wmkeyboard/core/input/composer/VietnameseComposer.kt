package com.wasimaster.wmkeyboard.core.input.composer

import java.text.Normalizer

/**
 * Vietnamese input by the two dominant methods, Telex and VNI. Both are
 * transliterating composers: the roman keystrokes accumulate in the service
 * buffer and [composeBuffer] folds them into fully-toned Vietnamese, shown as
 * the composing region and committed as a unit — the same shape as Avro/Hangul,
 * so no dictionary and no candidate list are involved.
 *
 * Telex spells the diacritics with letters (`as`→á, `aa`→â, `aw`→ă, `ow`→ơ,
 * `w`→ư, `dd`→đ, tones s/f/r/x/j); VNI spells them with digits (1..5 tones,
 * 6 circumflex, 7 horn, 8 breve, 9 đ, 0 clears the tone). The engine is shared;
 * only the keystroke→intent mapping differs.
 *
 * The tone lands on the syllable's main vowel by the standard rule: a vowel that
 * already carries a mark (â ê ô ă ơ ư) wins; otherwise a single vowel takes it,
 * a closed cluster puts it on the last vowel, and an open cluster on the first
 * (except oa/oe/uy, which take the second). `qu`/`gi` onsets are not counted as
 * the nucleus.
 */
internal enum class VMark { NONE, CIRCUMFLEX, BREVE, HORN, STROKE }

internal enum class VTone(val combining: Char?) {
    NONE(null), ACUTE('́'), GRAVE('̀'), HOOK('̉'), TILDE('̃'), DOT('̣')
}

/**
 * [synthesized] marks a letter the engine made for a keystroke rather than one
 * the user typed: a bare `w` stands for ư, and that ư is spelled with a u no
 * finger ever pressed. Undoing the mark on such a letter has to take the letter
 * with it, or the u is left behind spelling a word the user never wrote.
 */
private class VLetter(
    var base: Char,
    var mark: VMark,
    val upper: Boolean,
    val synthesized: Boolean = false,
)

internal object VietnameseEngine {

    /**
     * The face a tone key's ring is drawn on: a placeholder circle carrying the
     * mark. Typed along with the mark, and swallowed by the transducer.
     */
    internal const val DOTTED_CIRCLE = '\u25CC'

    /** The tone [c] *is*, when it is a combining mark rather than a letter. */
    internal fun directTone(c: Char): VTone? = when (c) {
        '\u0301' -> VTone.ACUTE
        '\u0300' -> VTone.GRAVE
        '\u0309' -> VTone.HOOK
        '\u0303' -> VTone.TILDE
        '\u0323' -> VTone.DOT
        else -> null
    }

    /** Whether [c] is one of the characters a tone key sends. */
    internal fun isToneChar(c: Char): Boolean = c == DOTTED_CIRCLE || directTone(c) != null

    private fun isVowel(c: Char) = c in "aeiouy"

    private fun precompose(base: Char, mark: VMark): Char = when (mark) {
        VMark.NONE -> base
        VMark.CIRCUMFLEX -> when (base) { 'a' -> 'â'; 'e' -> 'ê'; 'o' -> 'ô'; else -> base }
        VMark.BREVE -> if (base == 'a') 'ă' else base
        VMark.HORN -> when (base) { 'o' -> 'ơ'; 'u' -> 'ư'; else -> base }
        VMark.STROKE -> if (base == 'd') 'đ' else base
    }

    /** The index of the tone-bearing vowel, or -1 if the syllable has no vowel. */
    private fun nucleus(letters: List<VLetter>): Int {
        val vowels = letters.indices.filter { isVowel(letters[it].base) }.toMutableList()
        // qu- and gi- onsets: the u / i is a glide, not the nucleus, unless it is
        // the syllable's only vowel.
        if (letters.size >= 2 && letters[0].base == 'q' && letters[1].base == 'u' &&
            vowels.any { it > 1 }
        ) vowels.remove(1)
        if (letters.size >= 2 && letters[0].base == 'g' && letters[1].base == 'i' &&
            vowels.any { it > 1 }
        ) vowels.remove(1)
        if (vowels.isEmpty()) return -1
        vowels.lastOrNull { letters[it].mark != VMark.NONE }?.let { return it }
        if (vowels.size == 1) return vowels[0]
        val last = vowels.last()
        val hasCoda = (last + 1..letters.lastIndex).any { !isVowel(letters[it].base) }
        if (hasCoda) return last
        if (vowels.size >= 3) return vowels[vowels.size - 2]
        val a = letters[vowels[0]].base
        val b = letters[vowels[1]].base
        return if ((a == 'o' && b == 'a') || (a == 'o' && b == 'e') || (a == 'u' && b == 'y')) {
            vowels[1]
        } else {
            vowels[0]
        }
    }

    private fun render(letters: List<VLetter>, tone: VTone): String {
        if (letters.isEmpty()) return ""
        val nuc = if (tone == VTone.NONE) -1 else nucleus(letters)
        val sb = StringBuilder()
        letters.forEachIndexed { i, l ->
            var c = precompose(l.base, l.mark)
            if (l.upper) c = c.uppercaseChar()
            sb.append(c)
            if (i == nuc) tone.combining?.let { sb.append(it) }
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }

    /** Apply [mark] to the last letter whose base is in [targets]; returns success. */
    private fun applyMark(letters: List<VLetter>, targets: String, mark: VMark): Boolean {
        for (i in letters.indices.reversed()) {
            if (letters[i].base in targets) {
                // A second press of the same mark key cancels it (Telex `aa` then
                // `a`, VNI double 6): toggle back to plain.
                letters[i].mark = if (letters[i].mark == mark) VMark.NONE else mark
                return true
            }
        }
        return false
    }

    /**
     * The composed word for a buffer, tone keys read as tones.
     *
     * With [VietnameseConfig.strictTones] on, two things change. A tone key
     * marks the word only while the word in hand could still become one
     * Vietnamese spells, and a word that carries a Vietnamese mark but is
     * neither a syllable nor the start of one is given back as the keys that
     * were typed. `fas` is `fá` by the loose rule and `fas` by this one, because
     * `f` is not an onset and there was never a word there to tone.
     *
     * Both are asked of the word in hand at each key, never of the finished
     * word, and that is the whole design. A rule that waited for the end could
     * not answer for `nuocsw`: at the `s` the word is `nuoc`, whose nucleus is
     * only finished two keys later by the `w`, and `nuoc` is a prefix of `nước`
     * even though it is not a syllable. See
     * [VietnameseOrthography.isSyllablePrefix].
     *
     * The last resort is the raw keys rather than the letters with their marks
     * taken off, because the two are not the same word: `rhees` was never `rhês`
     * with a mark removed, and giving back `rhees` is what leaves the user with
     * what they typed. A word with no Vietnamese mark in it is left alone
     * entirely, which is what keeps the repeated-key cancellation: `hass` is
     * `has` and stays `has`, because there is no mark there for the keyboard to
     * have made.
     */
    fun transduce(raw: String, vni: Boolean): String {
        val composed = compose(raw, vni).first
        if (!VietnameseConfig.strictTones) return composed
        if (!VietnameseOrthography.hasVietnameseMark(composed)) return composed
        if (VietnameseOrthography.isSyllable(composed)) return composed
        if (VietnameseOrthography.isSyllablePrefix(composed)) return composed
        return raw
    }

    /**
     * One pass of the rules. The letter marks (`aa`→`â`, `dd`→`đ`, `w`→`ư`) are
     * applied whatever the strict rule says: a word that is not Vietnamese is
     * answered for whole by [transduce], not key by key.
     */
    private fun compose(raw: String, vni: Boolean): Pair<String, VTone> {
        val letters = ArrayList<VLetter>()
        var tone = VTone.NONE

        fun toggleTone(t: VTone) { tone = if (tone == t) VTone.NONE else t }

        /**
         * Whether the vowels typed so far form one unbroken run.
         *
         * A Vietnamese syllable has exactly one vowel nucleus, so a tone key
         * after a broken run is not a tone at all — it is a letter, in a word
         * this composer has no business toning. "banana" + s stays `bananas`
         * rather than becoming `bánána`, and no real syllable is caught by it:
         * nguyễn, khuỷu and ngoèo all keep their vowels together.
         *
         * Counted rather than collected: this runs on every tone keystroke.
         */
        fun hasVowelCluster(): Boolean {
            var first = -1
            var last = -1
            var count = 0
            for (i in letters.indices) {
                if (isVowel(letters[i].base)) {
                    if (first < 0) first = i
                    last = i
                    count++
                }
            }
            return count > 0 && last - first + 1 == count
        }

        /**
         * Whether a tone key may mark the letters so far: the vowel-run rule the
         * keyboard has always had, and — with [VietnameseConfig.strictTones] on
         * — the word in hand being one Vietnamese could still spell.
         *
         * Asked of the letters with no tone on them, because a tone is a
         * property of the whole syllable and says nothing about whether the
         * letters are one yet.
         */
        fun toneAllowed(): Boolean =
            hasVowelCluster() &&
                (!VietnameseConfig.strictTones ||
                    VietnameseOrthography.isSyllablePrefix(render(letters, VTone.NONE)))

        for (ch in raw) {
            val upper = ch.isUpperCase()
            val lc = ch.lowercaseChar()
            // A tone typed as itself, from the tone key's own ring rather than
            // spelled with a letter or a digit. Shared by both methods: the key
            // is on both layouts, and a mark means the same thing on each.
            //
            // The ring's faces are written on a dotted circle, so a press sends
            // U+25CC and then the mark; the circle is swallowed here, which also
            // makes the bare circle the ring's "no tone" entry. Named outright,
            // a tone does not toggle — pressing acute twice means acute.
            if (lc == DOTTED_CIRCLE) { tone = VTone.NONE; continue }
            val direct = directTone(lc)
            if (direct != null) {
                if (toneAllowed()) tone = direct
                continue
            }
            if (vni) {
                // A digit that cannot do its job is a digit. Every branch here
                // falls through to the literal append when there is nothing to
                // tone or nothing to mark — otherwise a number typed inside a
                // word (`banana1`, an address, a model name) would silently
                // lose its digits to a tone that had nowhere to land.
                when (lc) {
                    '1', '2', '3', '4', '5' -> if (toneAllowed()) {
                        toggleTone(
                            when (lc) {
                                '1' -> VTone.ACUTE
                                '2' -> VTone.GRAVE
                                '3' -> VTone.HOOK
                                '4' -> VTone.TILDE
                                else -> VTone.DOT
                            },
                        )
                        continue
                    }
                    // Clearing a tone is not marking one, so the strict rule has
                    // nothing to say about it.
                    '0' -> if (hasVowelCluster()) { tone = VTone.NONE; continue }
                    '6' -> { if (applyMark(letters, "aeo", VMark.CIRCUMFLEX)) continue }
                    '7' -> { if (applyMark(letters, "ou", VMark.HORN)) continue }
                    '8' -> { if (applyMark(letters, "a", VMark.BREVE)) continue }
                    '9' -> { if (applyMark(letters, "d", VMark.STROKE)) continue }
                }
                letters.add(VLetter(lc, VMark.NONE, upper))
                continue
            }
            // Telex
            when (lc) {
                's', 'f', 'r', 'x', 'j' -> {
                    val t = when (lc) {
                        's' -> VTone.ACUTE; 'f' -> VTone.GRAVE; 'r' -> VTone.HOOK
                        'x' -> VTone.TILDE; else -> VTone.DOT
                    }
                    if (hasVowelCluster()) {
                        if (tone == t) {
                            // Repeating the tone key takes the mark back off and
                            // types the letter. That is the key's own effect and
                            // not a mark being placed, so the strict rule has no
                            // say in it — which is what leaves `has` for `hass`.
                            tone = VTone.NONE
                            letters.add(VLetter(lc, VMark.NONE, upper))
                        } else if (toneAllowed()) {
                            tone = t
                        } else {
                            letters.add(VLetter(lc, VMark.NONE, upper))
                        }
                    } else {
                        letters.add(VLetter(lc, VMark.NONE, upper))
                    }
                }
                'w' -> {
                    // Horn on uo cluster -> ươ (e.g. nuocsw -> nước, tuongw -> tương);
                    // otherwise horn/breve on the last a/o/u; a bare w types ư.
                    //
                    // A second w takes the mark back off *and* types the letter,
                    // which is what makes an English word survive the Telex
                    // layout: row, draw, show and flow are all a marked vowel
                    // plus a w that has nowhere else to go. Undoing the mark
                    // without typing the w left `ro` for `roww`.
                    val uIdx = letters.indexOfLast { it.base == 'u' }
                    val oIdx = letters.indexOfLast { it.base == 'o' }
                    if (uIdx != -1 && oIdx != -1 && oIdx == uIdx + 1) {
                        if (letters[uIdx].mark == VMark.HORN && letters[oIdx].mark == VMark.HORN) {
                            letters[uIdx].mark = VMark.NONE
                            letters[oIdx].mark = VMark.NONE
                            letters.add(VLetter('w', VMark.NONE, upper))
                        } else {
                            letters[uIdx].mark = VMark.HORN
                            letters[oIdx].mark = VMark.HORN
                        }
                    } else {
                        val marked = letters.indexOfLast {
                            (it.base == 'a' && it.mark == VMark.BREVE) ||
                                ((it.base == 'o' || it.base == 'u') && it.mark == VMark.HORN)
                        }
                        if (marked != -1) {
                            // A ư the engine spelled from a bare w was never
                            // typed, so its letter goes with the mark: `ww` is
                            // w, not the `uw` a stranded u would leave. A mark
                            // on a vowel the user typed keeps its letter, which
                            // is what leaves `row` for `roww`.
                            if (letters[marked].synthesized) letters.removeAt(marked)
                            else letters[marked].mark = VMark.NONE
                            letters.add(VLetter('w', VMark.NONE, upper))
                        } else {
                            val applied = applyMark(letters, "a", VMark.BREVE) ||
                                applyMark(letters, "ou", VMark.HORN)
                            // A bare w is ư, which is Telex as it is written.
                            if (!applied) letters.add(VLetter('u', VMark.HORN, upper, synthesized = true))
                        }
                    }
                }
                'a', 'e', 'o' -> {
                    val last = letters.lastOrNull()
                    if (last != null && last.base == lc) {
                        if (last.mark == VMark.CIRCUMFLEX) {
                            last.mark = VMark.NONE
                            letters.add(VLetter(lc, VMark.NONE, upper))
                        } else {
                            last.mark = VMark.CIRCUMFLEX
                        }
                    } else {
                        letters.add(VLetter(lc, VMark.NONE, upper))
                    }
                }
                'd' -> {
                    val last = letters.lastOrNull()
                    if (last != null && last.base == 'd') {
                        if (last.mark == VMark.STROKE) {
                            last.mark = VMark.NONE
                            letters.add(VLetter('d', VMark.NONE, upper))
                        } else {
                            last.mark = VMark.STROKE
                        }
                    } else {
                        letters.add(VLetter('d', VMark.NONE, upper))
                    }
                }
                else -> letters.add(VLetter(lc, VMark.NONE, upper))
            }
        }
        return render(letters, tone) to tone
    }
}

/** Vietnamese Telex: letters spell the diacritics (`as`→á, `aw`→ă, `dd`→đ). */
object VietnameseTelexComposer : Composer {
    override val isTransliterating: Boolean get() = true
    // The tone key sends combining marks, which are not letters: without this
    // the key would commit the syllable and type a stray mark after it.
    override fun buffersChar(c: Char): Boolean = VietnameseEngine.isToneChar(c)
    override fun isPlausibleWord(word: String): Boolean = VietnameseOrthography.isSyllable(word)
    override fun composeBuffer(buffer: String): String = VietnameseEngine.transduce(buffer, vni = false)
}

/** Vietnamese VNI: digits spell the diacritics (`a8`→ă, `a1`→á, `d9`→đ). */
object VietnameseVniComposer : Composer {
    override val isTransliterating: Boolean get() = true
    override val bufferDigits: Boolean get() = true
    override fun buffersChar(c: Char): Boolean = VietnameseEngine.isToneChar(c)
    override fun isPlausibleWord(word: String): Boolean = VietnameseOrthography.isSyllable(word)
    override fun composeBuffer(buffer: String): String = VietnameseEngine.transduce(buffer, vni = true)
}
