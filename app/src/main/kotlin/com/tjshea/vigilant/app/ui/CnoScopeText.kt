package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoLeagues
import com.tjshea.vigilant.data.cno.CnoScope
import com.tjshea.vigilant.data.cno.CnoView

/** The words for the games the CNO scanner looks at (Tj, 2026-10-07; RESEARCH.md §85), free of Compose so they are testable. */
object CnoScopeText {

    const val TITLE = "Which games"

    const val INTRO =
        "Pick the leagues, kinds of bet and games the CrazyNinjaOdds list looks at, like the +EV tab's league chips (which are hidden while CrazyNinjaOdds is the only scanner). " +
            "Nothing picked means everything. It applies to the list, the widget, the alerts and the auto-bet alike."

    /** The choices for the fewest dollars available, and the props-per-game cap. */
    val LIQUIDITY_CHOICES = listOf(0, 10, 25, 50, 100)
    val PROPS_PER_GAME_CHOICES = listOf(0, 2, 4, 8, 16)

    fun liquidityLabel(dollars: Int): String = if (dollars <= 0) "Any" else "\$$dollars+"

    fun propsLabel(n: Int): String = if (n <= 0) "No cap" else "$n"

    /** What is asked of CNO, and what the app does itself, for the line under the pick (the plan is [CnoScope.plan]; ids are CNO's own, from the page, or this table). */
    fun readAs(f: CnoFilters, viewUrl: String = ""): String {
        val scope = f.scope
        val plan = scope.plan(f.rows, { CnoLeagues.byLabel(it)?.id }, { it.id })
        val core = when {
            scope.isDefault -> "Read as: every league, one read of CrazyNinjaOdds' best ${f.rows} bets."
            plan.leagueId != null -> "Read as: asked of CrazyNinjaOdds directly (${scope.pickedLabels().single()}), so its ${f.rows} rows are all that league's." +
                if (plan.appFilters) " Your other choices here are applied by the app to what it sends, from up to ${plan.askRows} rows." else ""
            plan.sportId != null -> "Read as: asked of CrazyNinjaOdds as ${CnoLeagues.byLabel(scope.pickedLabels().first())?.sport?.label}, then the app keeps the leagues you picked" +
                (if (plan.appFilters) ", from up to ${plan.askRows} rows" else "") + "."
            scope.leagues.isNotEmpty() -> "Read as: CrazyNinjaOdds can only be asked for one league or one sport at a time, so the app reads up to ${plan.askRows} rows and keeps the leagues you picked."
            else -> "Read as: the app drops what you left out from up to ${plan.askRows} rows (CrazyNinjaOdds has no such filter, and cuts its list at its row limit first)."
        }
        val link = CnoView.scopeOf(viewUrl).let { (league, sport) -> league ?: sport }
        return if (link != null && scope.leagues.isNotEmpty()) "$core Your view's link already limits the list to $link: that wins, so the leagues you pick must be inside it." else core
    }

    /** The scope in the filter line on the CNO tab: nothing for the default. */
    fun tabSuffix(scope: CnoScope): String? = scope.summary().takeIf { it.isNotEmpty() }
}
