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
    fun ddAbbreviationsSurviveTheStrictRule() {
        // `dd` is how `đc`, `đt` and `đh` are typed: abbreviations, not words,
        // and no one of them a syllable — so the strict rule spelled every one
        // of them back as its keys. bamboo keeps a `đ` with no vowel in it
        // standing for exactly this reason (`IBddFreeStyle`, on by default
        // there), and this is that exception.
        strict {
            val c = VietnameseTelexComposer
            assertEquals("đc", c.composeBuffer("ddc"))
            assertEquals("đt", c.composeBuffer("ddt"))
            assertEquals("đh", c.composeBuffer("ddh"))
            assertEquals("đcd", c.composeBuffer("ddcd"))
            assertEquals("dđ", c.composeBuffer("dddd"))
        }
    }

    @Test
    fun aVowelPutsTheWordBackUnderTheStrictRule() {
        // The exception turns on there being no vowel: `đi` and `đo` are words,
        // and a word the rules cannot spell is still given back as its keys.
        strict {
            val c = VietnameseTelexComposer
            assertEquals("đi", c.composeBuffer("ddi"))
            assertEquals("đo", c.composeBuffer("ddo"))
            assertEquals("đong", c.composeBuffer("ddong"))
            assertEquals("đường", c.composeBuffer("dduowngf"))
            assertEquals("web", c.composeBuffer("web"))
            assertEquals("banana", c.composeBuffer("banana"))
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
            // A doubled tone key at the end of a Latin word: the first is
            // refused for the same reason, so the second has no tone to cancel
            // and both keys stand as letters. The loose rule gives `pres` here.
            assertEquals("press", c.composeBuffer("press"))
            assertEquals("stress", c.composeBuffer("stress"))
            // The tone lands on the `x` here, on `e`, which is a syllable —
            // and the letters after it are what unmake it. See the whole-word
            // rule below for what happens to that.
            assertEquals("express", c.composeBuffer("express"))
        }
    }

    @Test
    fun aWordThatCanNeverBeVietnameseIsGivenBackAsKeys() {
        strict {
            val c = VietnameseTelexComposer
            // `ee` makes an ê, and ê is a mark the keyboard put there. The word
            // it made is not Vietnamese and never could be, so the keyboard
            // gives back the keys instead of the letters it invented: `rhees`,
            // not `rhês`. Removing the mark would leave `rhees` only by luck —
            // it is not the same operation.
            assertEquals("rhees", c.composeBuffer("rhees"))
            assertEquals("rhees", c.composeBuffer("rhees"))
            // Same shape, different key: the ê is right and the stray `x` is
            // not, and the whole word goes back to keys rather than keeping a
            // half-Vietnamese spelling nobody typed.
            assertEquals("nguyeenxx", c.composeBuffer("nguyeenxx"))
            // A word that *is* Vietnamese keeps everything, marks included.
            assertEquals("nguyễn", c.composeBuffer("nguyeenx"))
        }
    }

    @Test
    fun aToneAStopCodaCannotCarryIsRefused() {
        strict {
            val c = VietnameseTelexComposer
            // `c` is a stop, and only sắc or nặng may sit on one. The prefix
            // rule has to know that as well as the syllable rule, or `x` would
            // put a tilde on `nươc` and leave `nưỡc` standing — not a syllable,
            // and not the start of one either.
            assertEquals("nuocswx", c.composeBuffer("nuocswx"))
            // The two tones a stop can carry still land.
            assertEquals("nước", c.composeBuffer("nuocsw"))
            assertEquals("việc", c.composeBuffer("vieejc"))
        }
    }

    @Test
    fun aCancelledToneKeyIsNotResurrectedByWhatFollows() {
        strict {
            val c = VietnameseTelexComposer
            // The trap: `hass` is `has` — the second `s` cancelled the first
            // and typed itself — and adding `r` must not bring that `s` back.
            // The old two-pass retry recomposed the whole buffer with every
            // tone key read as a letter, so `hassr` came out `hassr` and `hasr`
            // was unreachable.
            assertEquals("has", c.composeBuffer("hass"))
            assertEquals("hasr", c.composeBuffer("hassr"))
        }
    }

    /**
     * A mark key is spent only when the mark it makes is one the word could
     * carry.
     *
     * Every expectation here is UniKey's own answer for the same keys — read
     * off the engine itself (fcitx5-unikey's `unikey/`, `UkTelex`,
     * `spellCheckEnabled` + `autoNonVnRestore` + `freeMarking`), not reasoned
     * out — because the two questions this test turns on are ones reading the
     * code gets wrong. A key whose mark is *refused* stays a letter, so the
     * `oo` of `daboo` is two letters and not the one `ô` would have spent; and
     * a key whose mark *lived* stays spent even after the mark is taken back
     * off, so `daaabooo` is one letter shorter than the keys that spelled it.
     */
    @Test
    fun aMarkKeyIsSpentOnlyWhenItsMarkCouldStand() {
        strict {
            val c = VietnameseTelexComposer
            // `dabô` is not a word, so the second `o` is a letter and nothing
            // is spent — one more `o` is one more letter, never a jump.
            assertEquals("dabo", c.composeBuffer("dabo"))
            assertEquals("daboo", c.composeBuffer("daboo"))
            assertEquals("dabooo", c.composeBuffer("dabooo"))
            assertEquals("daboooo", c.composeBuffer("daboooo"))
            // `dâ` *is* the beginning of a word, so the third `a` of `daaa`
            // cancels a mark that had lived: that key stays spent.
            assertEquals("daabo", c.composeBuffer("daaabo"))
            assertEquals("daaboo", c.composeBuffer("daaaboo"))
            assertEquals("daabooo", c.composeBuffer("daaabooo"))
            assertEquals("daaboooo", c.composeBuffer("daaaboooo"))
            // The same two rules with a second run after them.
            assertEquals("daapoooo", c.composeBuffer("daaapoooo"))
            // A run on its own: `nô` and `ô` are words and their keys are
            // spent; `nooo` and `ooooo` are not, so one key is spent and the
            // rest stand as letters.
            assertEquals("nô", c.composeBuffer("noo"))
            assertEquals("noo", c.composeBuffer("nooo"))
            assertEquals("ô", c.composeBuffer("oo"))
            assertEquals("ooo", c.composeBuffer("oooo"))
            assertEquals("oooo", c.composeBuffer("ooooo"))
            assertEquals("aaa", c.composeBuffer("aaaa"))
            assertEquals("aaaa", c.composeBuffer("aaaaa"))
            // `oâ` is not a word's beginning, so the third `a` of `oaa` is a
            // letter — and the `w` after it has an `a` to horn, which is why
            // `oaaw` is the keys and `oaw` is `oă`.
            assertEquals("oaa", c.composeBuffer("oaa"))
            assertEquals("oaaw", c.composeBuffer("oaaw"))
            assertEquals("oă", c.composeBuffer("oaw"))
            // The stroke is exempt: `đ` is a letter, and `dđ` stands.
            assertEquals("daa", c.composeBuffer("daaa"))
            assertEquals("dd", c.composeBuffer("ddd"))
            assertEquals("dđ", c.composeBuffer("dddd"))
            assertEquals("ddaa", c.composeBuffer("dddaa"))
            // Not UniKey's answers, these: a word the rules cannot spell is
            // given back as its keys, which is this keyboard's own reading and
            // not UniKey's — `banana` is `bânna` there. Left as they are; the
            // fallback is a separate question from the one this test asks.
            assertEquals("banana", c.composeBuffer("banana"))
            assertEquals("rhees", c.composeBuffer("rhees"))
            assertEquals("dodod", c.composeBuffer("dodod"))
            assertEquals("nanan", c.composeBuffer("nanan"))
            // A bare `w` is its own case: the ư it stands for was never typed,
            // so taking the letter back takes the key with it. `ww` is `w`, and
            // `wwaa` is the `waa` those keys spell rather than a word that keeps
            // the `w` the ư was taken from.
            assertEquals("w", c.composeBuffer("ww"))
            assertEquals("waa", c.composeBuffer("wwaa"))
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
        }
    }

    @Test
    fun theToneRemovalKeyIsHeldToTheSameRule() {
        strict {
            val c = VietnameseTelexComposer
            // `z` takes a tone off, and clearing a tone is not marking one, so
            // the strict rule has nothing to say about the key itself: the word
            // it leaves is passed to the rule like any other.
            assertEquals("toan", c.composeBuffer("toansz"))
            assertEquals("nươc", c.composeBuffer("nuocswz"))
            // A `z` with no tone to take is the letter, and that letter makes
            // the word one Vietnamese does not spell — so the whole buffer goes
            // back to keys, the `z` included.
            assertEquals("toanz", c.composeBuffer("toanz"))
            assertEquals("aaz", c.composeBuffer("aaz"))
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
