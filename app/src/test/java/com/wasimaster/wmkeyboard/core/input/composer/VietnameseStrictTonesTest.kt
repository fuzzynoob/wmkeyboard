package com.wasimaster.wmkeyboard.core.input.composer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "No free tone marking": with [VietnameseConfig.strictTones] on, a tone key
 * tones only a syllable Vietnamese actually spells.
 *
 * The loose behaviour is Telex as it has always been here — a tone key marks
 * whatever the vowel run allows, so `fas` is `fá` even though `f` is not an
 * onset and there is no word there. Strict says the key was the letter it is
 * drawn as after all, and `fas` stays `fas`.
 *
 * What must not change is the point of these tests: every real syllable still
 * composes, including the ones whose buffer never looks like one (`nguyeen`
 * becomes `nguyên`, which is a syllable, though the buffer is not).
 */
class VietnameseStrictTonesTest {

    /** Runs [block] with the switch on, and always puts it back. */
    private fun strict(block: () -> Unit) {
        val was = VietnameseConfig.strictTones
        VietnameseConfig.strictTones = true
        try {
            block()
        } finally {
            VietnameseConfig.strictTones = was
        }
    }

    @Test
    fun offIsTheLooseBehaviourTheKeyboardHasAlwaysHad() {
        // The default, and the reason the switch has to be asked for: the
        // loose rule tones `fix` into `fĩ`, which is not a word at all.
        assertEquals(false, VietnameseConfig.strictTones)
        val c = VietnameseTelexComposer
        assertEquals("fá", c.composeBuffer("fas"))
        assertEquals("fĩ", c.composeBuffer("fix"))
    }

    @Test
    fun aToneKeyOnWhatIsNotASyllableIsTheLetter() {
        strict {
            val c = VietnameseTelexComposer
            // f is not an onset, so there is no syllable to tone.
            assertEquals("fas", c.composeBuffer("fas"))
            assertEquals("fax", c.composeBuffer("fax"))
            assertEquals("far", c.composeBuffer("far"))
            // The tone lands on the `x` here, on `e`, which is a syllable —
            // and the letters after it are what unmake it. Deciding at the
            // keystroke would keep the tilde and give `ẽpress`.
            assertEquals("express", c.composeBuffer("express"))
        }
    }

    @Test
    fun everyRealSyllableStillComposes() {
        strict {
            val c = VietnameseTelexComposer
            assertEquals("toán", c.composeBuffer("toans"))
            assertEquals("tiếng", c.composeBuffer("tieengs"))
            assertEquals("nước", c.composeBuffer("nuocsw"))
            assertEquals("quả", c.composeBuffer("quar"))
            assertEquals("khuỷu", c.composeBuffer("khuyur"))
            // The buffer is not a syllable here; what it composes to is, and
            // that is what the rule has to be asked about.
            assertEquals("nguyễn", c.composeBuffer("nguyeenx"))
            assertEquals("nguyên", c.composeBuffer("nguyeen"))
            // The rule is Vietnamese phonotactics, not "does it look English":
            // `fix` stays `fix` because f is not an onset, while `mix` is `mĩ`,
            // which is a word — m is an onset and ĩ is a tone it can carry.
            assertEquals("mĩ", c.composeBuffer("mix"))
        }
    }

    @Test
    fun wordsWithNoVowelRunAreUntouchedEitherWay() {
        strict {
            val c = VietnameseTelexComposer
            assertEquals("bananas", c.composeBuffer("bananas"))
            assertEquals("relax", c.composeBuffer("relax"))
            assertEquals("inbox", c.composeBuffer("inbox"))
        }
    }

    @Test
    fun theRepeatedToneKeyStillCancels() {
        strict {
            val c = VietnameseTelexComposer
            // The repeated key cancels its own tone, so the result carries no
            // tone and strict has nothing to take back: a tone key only ever
            // counts as a letter when the rules *marked* something that is not
            // a word. `as` on its own is `á` — a syllable — so this is not the
            // same spelling reached two ways.
            assertEquals("as", c.composeBuffer("ass"))
            assertEquals("af", c.composeBuffer("aff"))
            // Letter marks, not tones, so strict never had an opinion.
            assertEquals("dd", c.composeBuffer("ddd"))
            assertEquals("aa", c.composeBuffer("aaa"))
            // And the price of the cancellation, which is the keyboard's
            // already and not strict's: the first key became the tone and the
            // second undid it, so the two keys leave one letter. Same in both
            // modes — no tone survives for strict to have a view on.
            assertEquals("pres", c.composeBuffer("press"))
            assertEquals("stres", c.composeBuffer("stress"))
        }
    }

    @Test
    fun theFlickRingIsHeldToTheSameRule() {
        strict {
            val c = VietnameseTelexComposer
            // A ring press sends the mark itself. It is a tone either way, so
            // it is the same rule: nothing to tone on `fa`, a word on `chao`.
            assertEquals("fa", c.composeBuffer("fá"))
            assertEquals("chào", c.composeBuffer("chaò"))
        }
    }

    @Test
    fun vniDigitsFollowTheSameRule() {
        strict {
            val c = VietnameseVniComposer
            // A digit that cannot tone is a digit, exactly as the loose rule
            // already has it for a buffer with no vowel at all.
            assertEquals("fa1", c.composeBuffer("fa1"))
            assertEquals("toán", c.composeBuffer("toan1"))
        }
    }
}
