package com.gothwad.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the grid rules (issue #38).
 *
 * These cover the bug from issue #15: every unassigned app used to be dumped into the
 * *first* section, so the home screen collapsed into a single "Streaming" row.
 */
class CategoryAssignerTest {

    private fun app(
        pkg: String,
        label: String = pkg,
        auto: String = "apps",
        firstInstall: Long = 0L,
    ) = AppEntry(
        pkg = pkg,
        label = label,
        banner = null,
        icon = null,
        autoCategory = auto,
        tile = 0,
        stamp = 0L,
        firstInstall = firstInstall,
    )

    private fun config(
        categories: List<CategoryCfg> = listOf(
            CategoryCfg("streaming", "Streaming"),
            CategoryCfg("games", "Games"),
            CategoryCfg("apps", "Apps"),
        ),
        sections: Map<String, Set<String>> = emptyMap(),
        order: Map<String, List<String>> = emptyMap(),
        hidden: Set<String> = emptySet(),
        showHidden: Boolean = false,
        autoCategory: Boolean = true,
    ) = LauncherConfig(
        categories = categories,
        sections = sections,
        order = order,
        hidden = hidden,
        showHidden = showHidden,
        autoCategoryOnInstall = autoCategory,
    )

    @Test
    fun `unassigned app goes to its real auto category, not the first section`() {
        // YouTube is auto-categorised as streaming by AppRepository.
        val yt = app("com.google.android.youtube", auto = "streaming")
        val rows = CategoryAssigner.rows(listOf(yt), config())

        assertEquals(1, rows.size)
        assertEquals("streaming", rows.first().first.id)
    }

    @Test
    fun `auto category off falls back to the first declared section`() {
        val yt = app("com.google.android.youtube", auto = "streaming")
        val rows = CategoryAssigner.rows(
            listOf(yt),
            config(
                categories = listOf(CategoryCfg("apps", "Apps"), CategoryCfg("games", "Games")),
                autoCategory = false,
            ),
        )

        assertEquals("apps", rows.single().first.id)
        assertEquals(1, rows.single().second.size)
    }

    @Test
    fun `explicit assignment wins over auto category`() {
        val yt = app("com.google.android.youtube", auto = "streaming")
        val rows = CategoryAssigner.rows(
            listOf(yt),
            config(sections = mapOf(yt.pkg to setOf("games"))),
        )

        assertEquals("games", rows.single().first.id)
    }

    @Test
    fun `an app can live in more than one section`() {
        val yt = app("com.google.android.youtube", auto = "streaming")
        val rows = CategoryAssigner.rows(
            listOf(yt),
            config(sections = mapOf(yt.pkg to setOf("streaming", "apps"))),
        )

        assertEquals(listOf("streaming", "apps"), rows.map { it.first.id })
    }

    @Test
    fun `hidden apps are not rendered unless show hidden is on`() {
        val vaulted = app("com.example.secret")
        val hidden = setOf(vaulted.pkg)

        assertTrue(CategoryAssigner.rows(listOf(vaulted), config(hidden = hidden)).isEmpty())
        assertEquals(
            1,
            CategoryAssigner.rows(listOf(vaulted), config(hidden = hidden, showHidden = true)).size,
        )
    }

    @Test
    fun `all-apps section shows everything visible`() {
        val a = app("a", auto = "apps")
        val b = app("b", auto = "games")
        val rows = CategoryAssigner.rows(
            listOf(a, b),
            config(categories = listOf(CategoryCfg(AppRepository.ALL_APPS_ID, "All"))),
        )

        assertEquals(2, rows.single().second.size)
    }

    @Test
    fun `saved order is applied first and unknown apps follow`() {
        val a = app("a", firstInstall = 1L)
        val b = app("b", firstInstall = 2L)
        val rows = CategoryAssigner.rows(
            listOf(a, b),
            config(
                categories = listOf(CategoryCfg("apps", "Apps")),
                order = mapOf("apps" to listOf("b")),
            ),
        )

        assertEquals(listOf("b", "a"), rows.single().second.map { it.pkg })
    }

    @Test
    fun `empty app list produces no rows`() {
        assertTrue(CategoryAssigner.rows(emptyList(), config()).isEmpty())
    }
}
