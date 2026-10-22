package dev.ohmydrift.ui.settings

import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorMode
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.mirror.newMirrorId
import dev.ohmydrift.settings.AppSettings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MirrorUiLogicTest {

    @Test
    fun moveSwapsOneStepAndIgnoresEdges() {
        val list = listOf(
            MirrorEntry(id = "a", name = "A", address = "https://a.example/"),
            MirrorEntry(id = "b", name = "B", address = "https://b.example/"),
            MirrorEntry(id = "c", name = "C", address = "https://c.example/"),
        )
        assertEquals(listOf("b", "a", "c"), movedMirror(list, "a", 1).map { it.id })
        assertEquals(listOf("a", "c", "b"), movedMirror(list, "c", -1).map { it.id })
        assertEquals(list, movedMirror(list, "a", -1))
        assertEquals(list, movedMirror(list, "c", 1))
        assertEquals(list, movedMirror(list, "missing", 1))
    }

    @Test
    fun manualMovePersistsDisplayedOrderAndDisablesLatencySort() {
        val settings = AppSettings(
            mirrors = listOf(
                MirrorEntry("a", "A", "https://a.example/"),
                MirrorEntry("b", "B", "https://b.example/"),
                MirrorEntry("c", "C", "https://c.example/"),
            ),
            mirrorOrder = MirrorOrder.LatencyAscending,
        )
        val displayed = listOf(settings.mirrors[1], settings.mirrors[0], settings.mirrors[2])

        val moved = moveDisplayedMirror(settings, displayed, "a", 1)

        assertEquals(listOf("b", "c", "a"), moved.mirrors.map { it.id })
        assertEquals(MirrorOrder.Manual, moved.mirrorOrder)
        assertEquals(MirrorOrder.LatencyAscending, settings.mirrorOrder)
        assertEquals(settings, moveDisplayedMirror(settings, displayed, "b", -1))
    }


        val list = listOf(
            MirrorEntry(id = "a", name = "A", address = "https://a.example/"),
            MirrorEntry(id = "b", name = "B", address = "https://b.example/"),
        )
        assertEquals(listOf("b"), removedMirror(list, "a").map { it.id })
        assertEquals(list, removedMirror(list, "missing"))
    }

    @Test
    fun generatedIdsAvoidExistingAndRepeatWithSeed() {
        val seeded = Random(7)
        val id = newMirrorId(setOf("mirror-1"), seeded)
        assertTrue(id.startsWith("mirror-"))
        assertNotEquals("mirror-1", id)
        assertEquals(id, newMirrorId(setOf("mirror-1"), Random(7)))
    }

    @Test
    fun templatePreviewEncodesOriginalUrl() {
        val entry = MirrorEntry(
            id = "t",
            name = "T",
            address = "https://m.example/fetch?url={url}",
            mode = MirrorMode.Template,
        )
        val preview = mirrorUrlPreview(entry, "https://github.com/a/b?x=1")
        assertTrue(preview.startsWith("https://m.example/fetch?url=https%3A%2F%2Fgithub.com%2Fa%2Fb"))
        assertTrue("%3Fx%3D1" in preview)
    }

    @Test
    fun prefixPreviewKeepsOriginalSchemeIntact() {
        val entry = MirrorEntry(id = "p", name = "P", address = "https://m.example", mode = MirrorMode.Prefix)
        val preview = mirrorUrlPreview(entry, "https://github.com/a/b")
        assertTrue(preview.startsWith("https://m.example"))
        assertTrue(preview.endsWith("https://github.com/a/b"))
    }

    @Test
    fun duplicateDetectionIgnoresNameButRespectsMode() {
        val siblings = listOf(
            MirrorEntry(id = "a", name = "A", address = "https://m.example/", mode = MirrorMode.Prefix),
        )
        val same = MirrorEntry(id = "b", name = "Other", address = "https://m.example", mode = MirrorMode.Prefix)
        val editingSelf = MirrorEntry(id = "a", name = "A", address = "https://m.example", mode = MirrorMode.Prefix)
        val template = same.copy(mode = MirrorMode.Template)
        assertTrue(duplicateError(same, siblings) != null)
        assertNull(duplicateError(editingSelf, siblings))
        assertNull(duplicateError(template, siblings))
    }

    @Test
    fun blankAddressDerivesHostName() {
        assertEquals("m.example", hostOf("https://m.example/path?x=1"))
        assertEquals("新镜像", hostOf(""))
    }
}
