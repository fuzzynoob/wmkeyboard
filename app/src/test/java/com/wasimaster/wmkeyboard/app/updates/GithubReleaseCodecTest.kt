package com.wasimaster.wmkeyboard.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding is tested against a trimmed copy of a real response rather than a
 * hand-written one, because the fields that matter here are the ones GitHub
 * chooses, not the ones this app would have chosen.
 */
class GithubReleaseCodecTest {

    private fun fixture(): String = checkNotNull(
        javaClass.getResourceAsStream("/updates/releases.json"),
    ) { "the release fixture is missing" }.bufferedReader().use { it.readText() }

    @Test
    fun `decodes a real response`() {
        val releases = GithubReleaseCodec.decode(fixture())
        assertNotNull(releases)
        assertEquals(2, releases?.size)
        val newest = releases!!.first()
        assertEquals("v0.5.3", newest.tagName)
        assertEquals(4, newest.assets.size)
        assertTrue(newest.assets.any { it.name == "SHA256SUMS.txt" })
    }

    @Test
    fun `keys this app has never heard of do not stop it decoding`() {
        // The fixture carries `body` and `author`, neither of which is modelled.
        assertNotNull(GithubReleaseCodec.decode(fixture()))
    }

    @Test
    fun `an asset with no digest decodes, and reports no checksum`() {
        val releases = GithubReleaseCodec.decode(fixture())!!
        val older = releases[1].assets.single()
        assertNull(older.digest)
        assertNull(ReleaseAssets.sha256Of(older.digest))
    }

    @Test
    fun `the newest installable release is chosen out of the real response`() {
        val releases = GithubReleaseCodec.decode(fixture())!!
        val candidate = ReleaseAssets.chooseCandidate(
            releases = releases,
            installedVersionCode = 13,
            flavor = "full",
            supportedAbis = listOf("arm64-v8a"),
            includePrereleases = false,
        )
        assertEquals(14, candidate?.versionCode)
        assertEquals("0.5.3", candidate?.versionName)
        assertEquals(69235943L, candidate?.sizeBytes)
        assertEquals(
            "7f4919af1b2e4dccf66ee8e993ed6c3e5677c7cf711ac9390b7a0b71ba3573ff",
            candidate?.sha256,
        )
        assertEquals(
            "https://raw.githubusercontent.com/fuzzynoob/wmkeyboard/v0.5.3/" +
                "fastlane/metadata/android/en-US/changelogs/14.txt",
            candidate?.changelogUrl,
        )
    }

    @Test
    fun `anything that is not a release list decodes to nothing`() {
        assertNull(GithubReleaseCodec.decode(""))
        assertNull(GithubReleaseCodec.decode("not json"))
        assertNull(GithubReleaseCodec.decode("""{"message":"Not Found"}"""))
    }

    @Test
    fun `GitHub's own error wording is read from the top level`() {
        val body = """{"message":"API rate limit exceeded","documentation_url":"https://x"}"""
        assertEquals("API rate limit exceeded", GithubReleaseCodec.errorMessage(body))
        assertNull(GithubReleaseCodec.errorMessage(null))
        assertNull(GithubReleaseCodec.errorMessage("not json"))
        // The shape the AI providers use, which is not GitHub's.
        assertNull(GithubReleaseCodec.errorMessage("""{"error":{"message":"nope"}}"""))
    }
}
