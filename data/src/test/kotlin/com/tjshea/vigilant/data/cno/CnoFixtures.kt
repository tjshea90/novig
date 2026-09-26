package com.tjshea.vigilant.data.cno

/**
 * Made-up pages in the shape of CrazyNinjaOdds' (RESEARCH.md §18.1): the same ASP.NET WebForms
 * form, AJAX script manager, loader timer and delta replies, with invented rows. Nothing here is
 * CNO's data.
 */
object CnoFixtures {
    const val P = "ctl00\$ctl00\$ContentPlaceHolderMain\$ContentPlaceHolderRight\$"
    const val TIMER = "${P}TimerAjaxDelayedLoad"
    const val BUTTON = "${P}ButtonUpdate"
    const val GRID_PANEL = "${P}UpdatePanelGridView"
    const val SCRIPT_MANAGER = "${P}ScriptManager1"

    fun page(action: String = "./positive-ev.aspx?site_id=17&amp;books_min=3") = """
        <!DOCTYPE html><html><head><title>Positive EV - CrazyNinjaOdds</title></head><body>
        <form method="post" action="$action" id="form1">
        <div class="aspNetHidden">
        <input type="hidden" name="__EVENTTARGET" id="__EVENTTARGET" value="" />
        <input type="hidden" name="__EVENTARGUMENT" id="__EVENTARGUMENT" value="" />
        <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="state&#43;one==" />
        </div>
        <script type="text/javascript">
        //<![CDATA[
        Sys.WebForms.PageRequestManager._initialize('$SCRIPT_MANAGER', 'form1', ['t${P}UpdatePanelServerInfo','ContentPlaceHolderMain_ContentPlaceHolderRight_UpdatePanelServerInfo','t$GRID_PANEL','ContentPlaceHolderMain_ContentPlaceHolderRight_UpdatePanelGridView'], [], [], 90, 'ctl00');
        //]]>
        </script>
        <span id="LabelServerLastUpdated">Last Updated: Loading...</span>
        <input id="ButtonRefresh" type="button" value="Refresh" onclick="x.click();" />
        <select name="${P}ctl03${'$'}FilterSportsbookSite${'$'}DropDownListSportsbookSite_All" id="site">
            <option value="0">All</option>
            <option selected="selected" value="17">Novig</option>
            <option value="20">Pinnacle</option>
        </select>
        <select name="${P}ctl03${'$'}DropDownListSport" id="sport">
            <option value="0">All</option>
            <option value="2">Football</option>
        </select>
        <input name="${P}ctl03${'$'}TextBoxMinimumOdds" type="text" id="oddsmin" />
        <input id="live" type="checkbox" name="${P}ctl03${'$'}CheckBoxIsLive" />
        <input id="main" type="checkbox" name="${P}ctl03${'$'}CheckBoxIsMain" checked="checked" />
        <input name="${P}ctl03${'$'}TextBoxMinimumEVPercentage" type="text" value="0%" id="evmin" />
        <textarea id="clip" name="Text1" cols="40" rows="5" style="display: none">
        textbox_str</textarea>
        <input type="submit" name="$BUTTON" value="Update" onclick="before_updating();" id="upd" />
        <input type="submit" name="${BUTTON}_Faux" value="Update" id="faux" disabled="disabled" />
        <span id="ContentPlaceHolderMain_ContentPlaceHolderRight_TimerAjaxDelayedLoad" style="display:none;"></span>
        <input type="hidden" name="__VIEWSTATEGENERATOR" id="__VIEWSTATEGENERATOR" value="B6071BA7" />
        <input type="hidden" name="__EVENTVALIDATION" id="__EVENTVALIDATION" value="valid/one" />
        <script type="text/javascript">
        Sys.Application.add_init(function() {
            ${'$'}create(Sys.UI._Timer, {"enabled":true,"interval":1,"uniqueID":"$TIMER"}, null, null, ${'$'}get("t"));
        });
        </script>
        </form></body></html>
    """.trimIndent()

    const val HEADERS =
        """<th data-class="expand" scope="col">LW-WC EV%</th><th scope="col">Calc</th><th scope="col">Extra</th><th scope="col">Date</th>""" +
            """<th scope="col">Sport</th><th scope="col">League</th><th scope="col">Event</th><th scope="col">Market</th>""" +
            """<th scope="col">Bet Name</th><th scope="col">Odds</th><th scope="col">Sportsbook</th><th scope="col">Fair Odds</th><th scope="col">Books</th>"""

    fun row(
        ev: String, utc: String, sport: String, league: String, event: String, market: String, bet: String,
        odds: String, book: String, fair: String, books: String, fairPct: String = "0.5", side: Int = 1,
    ) = """<tr data-fairpercentage="$fairPct" style="background-color:#EFF3FB;">
        <td><b>$ev</b></td><td><input type='button' value='Calc' onclick='OpenPopupCalc(this);'></td><td></td>
        <td><datetime utc='$utc' options_id='1'>...</datetime></td><td>$sport</td><td>$league</td>
        <td><a href='/site/browse/game.aspx?game_id=9&amp;market_id=8&amp;side_id=$side&amp;devig_method=0' target=_blank>$event</a></td>
        <td>$market</td><td>$bet</td><td>$odds</td>
        <td><a href='/site/redirect/deeplink.aspx?line_id=$side' target='_blank'>$book</a></td><td>$fair</td><td>$books</td>
        </tr>"""

    val rows = listOf(
        row("11.59%", "9/27/2026 8:05:00 PM", "Football", "NFL", "Away Team @ Home Team", "Player Receptions", "Joe Receiver Over 4.5", "+135 ($12)", "Novig", "+118", "5", "0.4587", side = 1),
        row("4.05%", "9/28/2026 12:15:00 AM", "Baseball", "MLB", "José Ramírez's Club @ Other Club", "Player Hits", "José Ramírez Under 0.5", "-110 ($1,250)", "Novig", "-118", "13", "0.5412", side = 2),
        row("2.10%", "9/27/2026 5:00:00 PM", "Football", "NFL", "Away Team @ Home Team", "Moneyline", "Home Team", "EVEN", "Pinnacle", "-104", "8", side = 3),
    )

    fun grid(rows: List<String> = this.rows, headers: String = HEADERS, note: String = "") = """
        <script type="text/javascript">Sys.Application.add_load(after_updating);</script>
        <input type="submit" name="$BUTTON" value="Update" id="upd" />
        +ev bets: <span style="color: red"><span id="ContentPlaceHolderMain_ContentPlaceHolderRight_LabelRedMessage">$note</span></span>
        <div><table class="footable" id="ContentPlaceHolderMain_ContentPlaceHolderRight_GridView1">
        <thead><tr style="color:White;">$headers</tr></thead><tbody>
        ${rows.joinToString("")}
        </tbody></table></div>"""

    fun info(ago: String = "27 seconds ago") =
        "\r\n<style type=\"text/css\">.serverMetrics { border: 1px }</style>\r\n<span id=\"x\">Last Updated: $ago</span> &nbsp;\r\n"

    /** An ASP.NET delta reply: `length|type|id|content|` records, lengths in UTF-16 units. */
    fun delta(vararg records: Triple<String, String, String>): String =
        records.joinToString("") { (type, id, content) -> "${content.length}|$type|$id|$content|" }

    fun reply(
        grid: String = grid(),
        info: String = info(),
        viewState: String = "state+two==",
        validation: String = "valid/two",
        timerOff: Boolean = true,
    ) = delta(
        Triple("#", "", "4"),
        Triple("updatePanel", "ContentPlaceHolderMain_ContentPlaceHolderRight_UpdatePanelServerInfo", info),
        Triple("updatePanel", "ContentPlaceHolderMain_ContentPlaceHolderRight_UpdatePanelGridView", grid),
        Triple("hiddenField", "__EVENTTARGET", ""),
        Triple("hiddenField", "__EVENTARGUMENT", ""),
        Triple("hiddenField", "__VIEWSTATE", viewState),
        Triple("hiddenField", "__EVENTVALIDATION", validation),
        Triple("formAction", "", "./positive-ev.aspx?site_id=17&books_min=3"),
        Triple("pageTitle", "", "Positive EV - CrazyNinjaOdds"),
        Triple("dataItemJson", "ContentPlaceHolderMain_ContentPlaceHolderRight_TimerAjaxDelayedLoad", if (timerOff) "[false,1]" else "[true,1]"),
    )
}
