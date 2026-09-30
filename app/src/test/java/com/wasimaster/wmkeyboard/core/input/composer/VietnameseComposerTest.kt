package com.wasimaster.wmkeyboard.core.input.composer

import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM Unit tests for Vietnamese Telex and VNI transliteration engine.
 * Fast local test execution without requiring ADB or Android devices.
 */
class VietnameseComposerTest {

    private val latinScript = ScriptRegistry[ScriptId.LATIN]

    @Test
    fun factoryMapsTelexAndVniComposers() {
        val telex = composerFor(latinScript, ComposerType.TELEX)
        val vni = composerFor(latinScript, ComposerType.VNI)

        assertNotNull(telex)
        assertTrue(telex is VietnameseTelexComposer)
        assertTrue(telex.isTransliterating)

        assertNotNull(vni)
        assertTrue(vni is VietnameseVniComposer)
        assertTrue(vni.isTransliterating)
        assertTrue(vni.bufferDigits)
    }

    // --- Telex Tests ---

    @Test
    fun telexBasicTones() {
        val c = VietnameseTelexComposer
        assertEquals("á", c.composeBuffer("as"))
        assertEquals("à", c.composeBuffer("af"))
        assertEquals("ả", c.composeBuffer("ar"))
        assertEquals("ã", c.composeBuffer("ax"))
        assertEquals("ạ", c.composeBuffer("aj"))
    }

    @Test
    fun telexLetterMarks() {
        val c = VietnameseTelexComposer
        assertEquals("â", c.composeBuffer("aa"))
        assertEquals("ă", c.composeBuffer("aw"))
        assertEquals("ê", c.composeBuffer("ee"))
        assertEquals("ô", c.composeBuffer("oo"))
        assertEquals("ơ", c.composeBuffer("ow"))
        assertEquals("ư", c.composeBuffer("uw"))
        assertEquals("ư", c.composeBuffer("w"))
        assertEquals("đ", c.composeBuffer("dd"))
    }

    @Test
    fun telexBareWUndoTakesTheLetterWithIt() {
        val c = VietnameseTelexComposer
        // A bare w is ư, but the u it is spelled with was never typed. Undoing
        // the mark has to take that letter away with it, or the u is stranded:
        // `ww` is a plain w, not the `uw` a leftover u would leave behind.
        assertEquals("w", c.composeBuffer("ww"))
        // A u the user did type is theirs to keep, so the two-key spelling of ư
        // still round-trips to the two letters it was typed as.
        assertEquals("uw", c.composeBuffer("uww"))
    }

    @Test
    fun telexFullSyllables() {
        val c = VietnameseTelexComposer
        assertEquals("việt", c.composeBuffer("vieejt"))
        assertEquals("tiếng", c.composeBuffer("tieengs"))
        assertEquals("đây", c.composeBuffer("ddaay"))
        assertEquals("nước", c.composeBuffer("nuocsw"))
        assertEquals("quả", c.composeBuffer("quar"))
    }

    @Test
    fun telexToneAndMarkCancellation() {
        val c = VietnameseTelexComposer
        assertEquals("as", c.composeBuffer("ass"))
        assertEquals("af", c.composeBuffer("aff"))
        assertEquals("dd", c.composeBuffer("ddd"))
        assertEquals("aa", c.composeBuffer("aaa"))
        assertEquals("ee", c.composeBuffer("eee"))
        assertEquals("oo", c.composeBuffer("ooo"))
    }

    @Test
    fun telexCapitalization() {
        val c = VietnameseTelexComposer
        assertEquals("Việt", c.composeBuffer("Vieejt"))
        assertEquals("Tiếng", c.composeBuffer("Tieengs"))
        assertEquals("ĐÂY", c.composeBuffer("DDAAY"))
    }

    @Test
    fun telexToneNeedsOneUnbrokenVowelRun() {
        val c = VietnameseTelexComposer
        // A Vietnamese syllable has exactly one vowel nucleus, so a tone key
        // after a broken run is the letter it is drawn as.
        assertEquals("bananas", c.composeBuffer("bananas"))
        assertEquals("relax", c.composeBuffer("relax"))
        assertEquals("inbox", c.composeBuffer("inbox"))
        // The rule catches nothing real: every syllable keeps its vowels
        // together, however many of them there are.
        assertEquals("nguyễn", c.composeBuffer("nguyeenx"))
        assertEquals("khuỷu", c.composeBuffer("khuyur"))
        assertEquals("ngoèo", c.composeBuffer("ngoeof"))
    }

    @Test
    fun telexSecondWTakesTheMarkOffAndTypesTheLetter() {
        val c = VietnameseTelexComposer
        assertEquals("row", c.composeBuffer("roww"))
        assertEquals("draw", c.composeBuffer("draww"))
        assertEquals("show", c.composeBuffer("showw"))
        assertEquals("flow", c.composeBuffer("floww"))
        assertEquals("ow", c.composeBuffer("oww"))
        assertEquals("uw", c.composeBuffer("uww"))
        // The uo cluster behaves the same way, both marks at once.
        assertEquals("dương", c.composeBuffer("duongw"))
        assertEquals("đương", c.composeBuffer("dduongw"))
        assertEquals("duongw", c.composeBuffer("duongww"))
    }

    @Test
    fun telexRunOfWTypesWAfterTheFirst() {
        val c = VietnameseTelexComposer
        // A bare `w` is ư, and the `w` after it takes that back and types the
        // letter. Every `w` after *that* is the letter too — holding the key
        // down types a run of `w`s, one shorter than the presses, rather than
        // ư coming back on every second key and leaving `wư`, `ww`, `wư`…
        assertEquals("ư", c.composeBuffer("w"))
        assertEquals("w", c.composeBuffer("ww"))
        assertEquals("ww", c.composeBuffer("www"))
        assertEquals("www", c.composeBuffer("wwww"))
        assertEquals("wwww", c.composeBuffer("wwwww"))
        assertEquals("wwwwwww", c.composeBuffer("wwwwwwww"))
        assertEquals("wwwwwwww", c.composeBuffer("wwwwwwwww"))
        // A `w` that horns a vowel is still that mark, whatever came before it.
        assertEquals("ă", c.composeBuffer("aw"))
        assertEquals("nước", c.composeBuffer("nuocsw"))
    }

    @Test
    fun telexRunOfWReadsAsWUnderStrictTones() {
        // The same run, with the strict rule on. `ww` carries no Vietnamese
        // mark, so the strict pass leaves it alone — but `wư`, which the old
        // rule produced on every odd press, does, and the strict pass would
        // hand back the whole run of keys instead.
        val was = VietnameseConfig.strictTones
        VietnameseConfig.strictTones = true
        try {
            val c = VietnameseTelexComposer
            assertEquals("ư", c.composeBuffer("w"))
            assertEquals("w", c.composeBuffer("ww"))
            assertEquals("ww", c.composeBuffer("www"))
            assertEquals("www", c.composeBuffer("wwww"))
            assertEquals("wwww", c.composeBuffer("wwwww"))
            assertEquals("wwwwwww", c.composeBuffer("wwwwwwww"))
        } finally {
            VietnameseConfig.strictTones = was
        }
    }

    @Test
    fun telexHornsBothVowelsOfUoOnlyWhenACodaFollows() {
        val c = VietnameseTelexComposer
        // A `uo` pair takes the horn on both letters when a coda follows — the
        // coda being what tells `hương` (h-ư-ơ-ng) from `huơ` (h-u-ơ), since
        // the two are the same two keys up to that point. With nothing coming
        // after, only the `o` is horned, which is the word `huơ`, `quơ`, `thuở`
        // are spelled with.
        assertEquals("huơ", c.composeBuffer("huow"))
        assertEquals("quơ", c.composeBuffer("quow"))
        assertEquals("thuở", c.composeBuffer("thuowr"))
        // The coda may arrive after the `w`, and then it counts.
        assertEquals("hươn", c.composeBuffer("huown"))
        assertEquals("hương", c.composeBuffer("huowng"))
        assertEquals("hướng", c.composeBuffer("huowngs"))
        // A tone key is not a coda: it rides the word without changing which
        // vowel the horn landed on.
        assertEquals("huờ", c.composeBuffer("huowf"))
        // A second `w` horns the `u` the first one left plain.
        assertEquals("hươ", c.composeBuffer("huoww"))
        // A `u` the user horned himself is not un-horned by the open reading:
        // `uwow` spells `ươ`, not the `ưo` a lone `o` horn would leave.
        assertEquals("ươ", c.composeBuffer("uwow"))
        // Unchanged: a coda already in the buffer counts the same as one ahead,
        // and the pair still toggles off on a second w when there is one.
        assertEquals("nước", c.composeBuffer("nuocsw"))
        assertEquals("dương", c.composeBuffer("duongw"))
        assertEquals("duongw", c.composeBuffer("duongww"))
        assertEquals("tương", c.composeBuffer("tuongw"))
    }

    @Test
    fun telexTakesToneMarksTypedAsThemselves() {
        val c = VietnameseTelexComposer
        assertEquals("cháo", c.composeBuffer("chao\u0301"))
        assertEquals("chào", c.composeBuffer("chao\u0300"))
        assertEquals("chảo", c.composeBuffer("chao\u0309"))
        assertEquals("chão", c.composeBuffer("chao\u0303"))
        assertEquals("chạo", c.composeBuffer("chao\u0323"))
        // The key's faces are drawn on a dotted circle, which is swallowed —
        // so the ring's bare circle is its "no tone" entry.
        assertEquals("cháo", c.composeBuffer("chao\u25CC\u0301"))
        assertEquals("chao", c.composeBuffer("chaos\u25CC"))
        // Named outright, a tone does not toggle the way a letter key does.
        assertEquals("cháo", c.composeBuffer("chao\u0301\u0301"))
        // And it still needs a nucleus to land on.
        assertEquals("bcd", c.composeBuffer("bcd\u0301"))
    }

    @Test
    fun toneKeyCharactersStayInTheBuffer() {
        for (c in listOf(VietnameseTelexComposer, VietnameseVniComposer)) {
            for (mark in "\u25CC\u0301\u0300\u0309\u0303\u0323") {
                assertTrue(c.toString(), c.buffersChar(mark))
            }
            assertTrue(c.toString(), !c.buffersChar('z'))
        }
    }

    // --- VNI Tests ---

    @Test
    fun vniBasicTones() {
        val c = VietnameseVniComposer
        assertEquals("á", c.composeBuffer("a1"))
        assertEquals("à", c.composeBuffer("a2"))
        assertEquals("ả", c.composeBuffer("a3"))
        assertEquals("ã", c.composeBuffer("a4"))
        assertEquals("ạ", c.composeBuffer("a5"))
        assertEquals("a", c.composeBuffer("a10")) // 0 clears tone
    }

    @Test
    fun vniLetterMarks() {
        val c = VietnameseVniComposer
        assertEquals("â", c.composeBuffer("a6"))
        assertEquals("ơ", c.composeBuffer("o7"))
        assertEquals("ư", c.composeBuffer("u7"))
        assertEquals("ă", c.composeBuffer("a8"))
        assertEquals("đ", c.composeBuffer("d9"))
    }

    @Test
    fun vniFullSyllables() {
        val c = VietnameseVniComposer
        assertEquals("việt", c.composeBuffer("viet65"))
        assertEquals("tiếng", c.composeBuffer("tieng61"))
        assertEquals("đây", c.composeBuffer("d9ay6"))
    }

    @Test
    fun vniToneMarksAndVowelRun() {
        val c = VietnameseVniComposer
        assertEquals("cháo", c.composeBuffer("chao\u0301"))
        assertEquals("chao", c.composeBuffer("chao1\u25CC"))
        assertEquals("banana1", c.composeBuffer("banana1"))
    }

    @Test
    fun vniCapitalization() {
        val c = VietnameseVniComposer
        assertEquals("Việt", c.composeBuffer("Viet65"))
        assertEquals("Tiếng", c.composeBuffer("Tieng61"))
    }
}
