package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {

    @Test
    fun `confirm before open when malicious is above zero`() {
        assertTrue(confirmBeforeOpen(1, 0))
        assertTrue(confirmBeforeOpen(99, 0))
    }

    @Test
    fun `confirm before open when suspicious is above zero`() {
        assertTrue(confirmBeforeOpen(0, 1))
        assertTrue(confirmBeforeOpen(0, 7))
    }

    @Test
    fun `no confirmation needed when both are zero`() {
        assertFalse(confirmBeforeOpen(0, 0))
    }

    @Test
    fun `gallery import mirrors the setting`() {
        assertTrue(galleryImportAllowed(true))
        assertFalse(galleryImportAllowed(false))
    }
}
