# Graph Report - visualization  (2026-10-21)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 883 nodes · 2356 edges · 58 communities (35 shown, 23 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 272 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `73ce9bc3`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Community 0
- Community 1
- Community 2
- Community 3
- Community 4
- Community 5
- Community 6
- Community 7
- Community 8
- Community 9
- Community 10
- Community 11
- Community 12
- Community 13
- Community 14
- Community 15
- Community 16
- Community 17
- Community 18
- Community 19
- Community 20
- Community 21
- Community 22
- Community 23
- Community 24
- Community 25
- Community 26
- Community 27
- Community 28
- Community 29
- Community 30
- Community 31
- Community 32
- Community 33
- Community 34
- Community 35
- Community 36
- Community 37
- Community 38
- Community 39
- Community 40
- Community 41
- Community 42
- Community 43
- Community 44
- Community 45
- Community 46
- Community 47
- Community 48
- Community 49
- Community 50
- Community 52
- Community 53
- Community 54
- Community 55
- Community 56

## God Nodes (most connected - your core abstractions)
1. `t()` - 75 edges
2. `r()` - 58 edges
3. `mi()` - 42 edges
4. `kc()` - 36 edges
5. `e()` - 35 edges
6. `u()` - 32 edges
7. `n()` - 28 edges
8. `c()` - 24 edges
9. `cc()` - 22 edges
10. `i()` - 20 edges

## Surprising Connections (you probably didn't know these)
- `ke()` --indirect_call--> `Ke()`  [INFERRED]
  src/main/resources/static/calculator/js/lodash.min.js → src/main/resources/static/calculator/js/jquery.min.js
- `ia()` --indirect_call--> `mt()`  [INFERRED]
  src/main/resources/static/calculator/js/lodash.min.js → src/main/resources/static/calculator/js/bootstrap.bundle.min.js
- `ia()` --indirect_call--> `ye()`  [INFERRED]
  src/main/resources/static/calculator/js/lodash.min.js → src/main/resources/static/calculator/js/jquery.min.js
- `wi()` --indirect_call--> `Z()`  [INFERRED]
  src/main/resources/static/calculator/js/lodash.min.js → src/main/resources/static/calculator/js/jquery.min.js
- `wa()` --indirect_call--> `U()`  [INFERRED]
  src/main/resources/static/calculator/js/lodash.min.js → src/main/resources/static/calculator/js/jquery.min.js

## Import Cycles
- None detected.

## Communities (58 total, 23 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.04
Nodes (18): absFloor(), addParseToken(), addRegexToken(), addWeekParseToken(), duration_add_subtract__add(), duration_add_subtract__addSubtract(), duration_add_subtract__subtract(), duration_as__valueOf() (+10 more)

### Community 1 - "Community 1"
Cohesion: 0.09
Nodes (47): a(), Ae(), da(), du(), e(), ec(), ei(), i() (+39 more)

### Community 2 - "Community 2"
Cohesion: 0.07
Nodes (47): aa(), af(), ao(), au(), b(), ba(), bc(), bf() (+39 more)

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (37): addTimeToArrayFromToken(), checkOverflow(), configFromInput(), configFromISO(), configFromString(), configFromStringAndArray(), configFromStringAndFormat(), copyConfig() (+29 more)

### Community 4 - "Community 4"
Cohesion: 0.16
Nodes (34): a(), at(), bt(), Ct(), Dt(), et(), Ft(), G() (+26 more)

### Community 5 - "Community 5"
Cohesion: 0.19
Nodes (35): ai(), d(), g(), gu(), h(), ho(), hr(), i() (+27 more)

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (32): addFormatToken(), addWeekYearFormatToken(), create__createDuration(), duration_humanize__relativeTime(), expandFormat(), replaceLongDateFormatTokens(), format(), formatMoment() (+24 more)

### Community 10 - "Community 10"
Cohesion: 0.08
Nodes (10): ci(), ef(), jc(), je(), kr(), nc(), q(), rf() (+2 more)

### Community 11 - "Community 11"
Cohesion: 0.12
Nodes (28): Be(), c(), cc(), dc(), ea(), fc(), fe(), gc() (+20 more)

### Community 12 - "Community 12"
Cohesion: 0.19
Nodes (7): LoanBO, StockLoanDTO, TotalLoan, LoanController, PrepaymentCalculator, LoanCalculator, LoanCalculatorTest

### Community 13 - "Community 13"
Cohesion: 0.10
Nodes (7): at(), Ke(), lt(), U(), ut(), X(), Z()

### Community 14 - "Community 14"
Cohesion: 0.18
Nodes (23): clone(), cloneWithOffset(), diff(), duration_get__get(), endOf(), getSet(), getSetDayOfYear(), hasAlignedHourOffset() (+15 more)

### Community 15 - "Community 15"
Cohesion: 0.13
Nodes (4): WorkCostDataDTO, WorkCostController, WorkCostServiceImpl, WorkCostService

### Community 17 - "Community 17"
Cohesion: 0.15
Nodes (12): LoanException, ResultEnum, DATE_FORMAT_ERR, FAILURE, JSON_ERR, NOT_EXIST, PREPAYMENT_MONTH_TOO_BIG, REPAYMENT_TOO_BIG (+4 more)

### Community 22 - "Community 22"
Cohesion: 0.25
Nodes (11): addPrepayment(), drawPicture1(), drawPicture2(), drawPicture345(), drawPicture6(), drawPicture7(), getMonthDifference(), initDatePicker() (+3 more)

### Community 23 - "Community 23"
Cohesion: 0.18
Nodes (14): cf(), ee(), fa(), go(), hf(), m(), oo(), pf() (+6 more)

### Community 24 - "Community 24"
Cohesion: 0.20
Nodes (12): ac(), Ce(), de(), di(), ji(), lc(), oe(), qe() (+4 more)

### Community 25 - "Community 25"
Cohesion: 0.30
Nodes (10): bindEvents(), clearMap(), displayedRoutes, geocodeAddress(), MAP_CENTER, saveCurrentRoute(), savedRoutes, searchAndDisplayRoute() (+2 more)

### Community 27 - "Community 27"
Cohesion: 0.18
Nodes (11): fr(), gr(), ir(), mr(), oa(), pr(), tu(), vr() (+3 more)

### Community 28 - "Community 28"
Cohesion: 0.22
Nodes (11): chooseLocale(), compareArrays(), configFromObject(), defineLocale(), Duration(), loadLocale(), Locale(), locale_locales__getLocale() (+3 more)

### Community 29 - "Community 29"
Cohesion: 0.20
Nodes (10): A(), b(), ce(), he(), je(), le(), qe(), Te() (+2 more)

### Community 30 - "Community 30"
Cohesion: 0.24
Nodes (10): add_subtract__addSubtract(), createAdder(), deprecate(), deprecateSimple(), get_set__get(), get_set__set(), getSetMonth(), makeGetSet() (+2 more)

### Community 32 - "Community 32"
Cohesion: 0.28
Nodes (9): be(), Ee(), Oe(), p(), se(), w(), we(), xe() (+1 more)

### Community 33 - "Community 33"
Cohesion: 0.22
Nodes (9): cu(), gf(), iu(), l(), o(), ou(), ra(), wo() (+1 more)

### Community 34 - "Community 34"
Cohesion: 0.31
Nodes (9): configFromArray(), createUTCDate(), currentDateArray(), dayOfYearFromWeekInfo(), dayOfYearFromWeeks(), daysInYear(), defaults(), getIsLeapYear() (+1 more)

### Community 36 - "Community 36"
Cohesion: 0.25
Nodes (8): getISOWeeksInYear(), getSetISOWeek(), getSetISOWeekYear(), getSetWeekYear(), getWeeksInYear(), localeWeek(), weekOfYear(), weeksInYear()

### Community 38 - "Community 38"
Cohesion: 0.48
Nodes (5): archive_gc_logs(), check_process(), set_java_opts(), start.sh script, start_process()

### Community 40 - "Community 40"
Cohesion: 0.43
Nodes (7): e(), fe(), l(), R(), t(), Wt(), l()

### Community 41 - "Community 41"
Cohesion: 0.29
Nodes (7): df(), eo(), He(), of(), Pe(), xe(), z()

### Community 44 - "Community 44"
Cohesion: 0.33
Nodes (6): el(), k(), nl(), ul(), w(), xa()

### Community 45 - "Community 45"
Cohesion: 0.40
Nodes (6): absCeil(), as(), bubble(), daysToMonths(), makeAs(), monthsToDays()

### Community 46 - "Community 46"
Cohesion: 0.83
Nodes (3): hard_stop(), stop.sh script, soft_stop()

### Community 48 - "Community 48"
Cohesion: 0.50
Nodes (4): Ct(), ff(), nf(), Yt()

### Community 49 - "Community 49"
Cohesion: 0.67
Nodes (3): o(), M(), O()

### Community 50 - "Community 50"
Cohesion: 0.67
Nodes (3): me(), qa(), ya()

## Knowledge Gaps
- **11 isolated node(s):** `DATE_FORMAT_ERR`, `FAILURE`, `JSON_ERR`, `NOT_EXIST`, `PREPAYMENT_MONTH_TOO_BIG` (+6 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 159 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **23 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Z()` connect `Community 13` to `Community 1`, `Community 10`, `Community 4`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **Are the 35 inferred relationships involving `t()` (e.g. with `a()` and `ac()`) actually correct?**
  _`t()` has 35 INFERRED edges - model-reasoned connections that need verification._
- **What connects `DATE_FORMAT_ERR`, `FAILURE`, `JSON_ERR` to the rest of the system?**
  _11 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.036635006784260515 - nodes in this community are weakly interconnected._
- **Why does `R()` connect `Community 40` to `Community 18`, `Community 4`, `Community 13`?**
  _High betweenness centrality (0.015) - this node is a cross-community bridge._
- **Are the 37 inferred relationships involving `r()` (e.g. with `a()` and `af()`) actually correct?**
  _`r()` has 37 INFERRED edges - model-reasoned connections that need verification._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.09343200740055504 - nodes in this community are weakly interconnected._