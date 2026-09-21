package com.gothwad.launcher.data

/**
 * Pure category/visibility rules for the app grid.
 *
 * Extracted from `TvLauncherFragment` (issue #38): this logic decides what the user sees
 * on their home screen - which app lands in which section and whether a hidden (vault)
 * app is rendered at all - and it was previously untestable inside a fragment. It has no
 * Android dependencies, so it is covered by JVM unit tests in `app/src/test`.
 */
object CategoryAssigner {

    /** Apps that should be rendered for [config] (vault apps only when "show hidden" is on). */
    fun visibleApps(apps: List<AppEntry>, config: LauncherConfig): List<AppEntry> =
        if (config.showHidden) apps else apps.filter { it.pkg !in config.hidden }

    /** Sections a single app belongs to, honouring explicit assignment first. */
    fun sectionsFor(
        app: AppEntry,
        config: LauncherConfig,
        fallbackCategoryId: String,
    ): Set<String> {
        config.sections[app.pkg]?.let { return it }
        if (!config.autoCategoryOnInstall) return setOf(fallbackCategoryId)

        val auto = app.autoCategory
        val target = if (config.categories.any { it.id == auto }) auto else fallbackCategoryId
        return setOf(target)
    }

    /**
     * Builds the ordered rows for the grid: one row per category, apps ordered by the
     * user's saved order first and then by first-install time.
     * Categories with no visible apps are skipped.
     */
    fun rows(apps: List<AppEntry>, config: LauncherConfig): List<Pair<CategoryCfg, List<AppEntry>>> {
        if (apps.isEmpty()) return emptyList()

        val categories = config.categories.ifEmpty { listOf(CategoryCfg("apps", "Apps")) }
        val fallbackCategoryId = categories.first().id
        val visible = visibleApps(apps, config)

        return categories.mapNotNull { cat ->
            val inCategory = if (cat.id == AppRepository.ALL_APPS_ID) {
                visible
            } else {
                visible.filter { app -> cat.id in sectionsFor(app, config, fallbackCategoryId) }
            }

            val explicit = config.order[cat.id]
            val ordered = if (explicit.isNullOrEmpty()) {
                inCategory.sortedWith(compareBy({ it.firstInstall }, { it.label.lowercase() }))
            } else {
                val byPkg = inCategory.associateBy { it.pkg }
                val orderedFirst = explicit.mapNotNull { byPkg[it] }
                val remaining = inCategory
                    .filter { it.pkg !in explicit.toSet() }
                    .sortedWith(compareBy({ it.firstInstall }, { it.label.lowercase() }))
                orderedFirst + remaining
            }

            if (ordered.isEmpty()) null else cat to ordered
        }
    }
}
