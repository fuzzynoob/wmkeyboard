package com.wasimaster.wmkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.wasimaster.wmkeyboard.core.input.composer.VietnameseTelexComposer
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.Trie
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.settings.HapticSettings
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * What happens to a finished Vietnamese word when the caret comes back to it.
 *
 * Telex's buffer is the keys and the field holds the word they spelled, so the
 * word has to be spelled back into keys before it can be typed into
 * (`Composer.resumeBuffer`). This is the end-to-end check of that: type, commit,
 * backspace the space away, and see whether the next tone key lands on the word
 * or is spelled into it.
 */
@RunWith(RobolectricTestRunner::class)
class VietnameseResumeServiceTest {

    /**
     * A plain text field. The editor info matters: with none, the service reads
     * the field as a null one and sends backspace out as a key event instead of
     * deleting text — which the first version of this test did, and which made
     * the space look as though the keyboard had refused to take it back.
     */
    private val textField = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private class ViKeyboard(
        private val editor: InputConnection,
        private val info: EditorInfo,
    ) : WMKeyboardService() {
        init { attachBaseContext(RuntimeEnvironment.getApplication()) }
        override fun getCurrentInputConnection(): InputConnection = editor
        override fun getCurrentInputEditorInfo(): EditorInfo = info
    }

    private fun keyboardOn(editor: RecordingEditor): ViKeyboard {
        val service = ViKeyboard(editor, textField)
        plantPersonalStores(service)
        val engine = SuggestionEngine(
            Trie().apply { insert("hello", 100) },
            BengaliPhoneticIndex(emptyList()),
            UserLexicon(null),
        )
        val field = WMKeyboardService::class.java.getDeclaredField("suggestionEngine")
        field.isAccessible = true
        field.set(service, engine)
        seedState(
            service,
            glideReadyState(
                settings = KeyboardSettings(
                    learnFromTyping = false,
                    haptics = HapticSettings(enabled = false),
                ),
                fieldNoSuggestions = false,
            ).copy(
                composer = VietnameseTelexComposer,
                language = LanguageRegistry.byId("vi"),
            ),
        )
        return service
    }

    private fun type(service: ViKeyboard, text: String) {
        for (ch in text) {
            service.onKey(
                if (ch == ' ') Key(label = " ", action = KeyAction.Space)
                else Key(label = ch.toString(), action = KeyAction.Text),
            )
        }
    }

    private fun pressBackspace(service: ViKeyboard) =
        service.onKey(Key(label = "⌫", action = KeyAction.Delete))

    /** The caret update a real editor sends once the text settles at its end. */
    private fun caretSettles(service: ViKeyboard, at: Int) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        service.onUpdateSelection(0, 0, at, at, -1, -1)
    }

    @Test
    fun `the word is composed and committed as usual`() {
        // The control: everything up to the caret coming back.
        val editor = RecordingEditor()
        val service = keyboardOn(editor)

        type(service, "tois")
        assertEquals("tói", editor.text.toString())

        type(service, " ")
        assertEquals("tói ", editor.text.toString())
    }

    @Test
    fun `a caret back at the word arms it as the composing region`() {
        val editor = RecordingEditor()
        val service = keyboardOn(editor)

        type(service, "tois ")
        pressBackspace(service)
        assertEquals("the space went", "tói", editor.text.toString())
        caretSettles(service, at = 3)

        assertEquals("tói", service.uiState.value.composingPreview)
    }

    @Test
    fun `the editor's own report of the armed region does not drop it`() {
        // A real editor echoes every edit back as an onUpdateSelection, the
        // composing region included. That echo is the one thing this harness
        // does not send on its own — and it is where a buffer that is not the
        // field's text (Telex's `tois` behind the field's `tói`) differs from
        // one that is.
        val editor = RecordingEditor()
        val service = keyboardOn(editor)

        type(service, "tois ")
        pressBackspace(service)
        caretSettles(service, at = 3)
        // The echo: caret after the region, the region spanning the word.
        service.onUpdateSelection(3, 3, 3, 3, 0, 3)

        assertEquals("tói", service.uiState.value.composingPreview)
        type(service, "f")
        assertEquals("tòi", editor.text.toString())
    }

    @Test
    fun `a tone key typed after coming back lands on the word`() {
        // The whole point: `f` after the caret returns to `tói` has to tone it
        // into `tòi`, not spell itself into `tóif`.
        val editor = RecordingEditor()
        val service = keyboardOn(editor)

        type(service, "tois ")
        pressBackspace(service)
        caretSettles(service, at = 3)
        type(service, "f")

        assertEquals("tòi", editor.text.toString())
    }
}
