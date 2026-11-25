# Release prep — rulings and deferred items

Decisions made on the owner's behalf while executing `docs/superpowers/plans/2026-11-07-release-prep.md` (2026-11-07..08), and the minor findings left for later. Copied from the execution ledger.

## Owner decisions
- 2026-11-07: kinds = musicians, bands, visual artists, writers & poets; players load on click + links always; artists without a Ukrainian birth place sit at their Ukrainian residence/work place, otherwise off the map.
- 2026-11-08: artists linked to Russia are not imported — Russian citizenship or Russian STATE honours (orders, medals, honorary titles, state prizes of the RF or RSFSR); admin `/hide` and `/show` commands.
- 2026-11-09: titles of the RSFSR do not count — the full dry run of 2026-11-08 left out 157 people for them alone (Dovzhenko, Prokofiev, Oistrakh, Gilels, Dunayevsky, Utyosov, Shulzhenko); only state honours of the Russian Federation count.

## Rulings (in order)
- Preflight: Ruling: env var NASHI_WIKIIMPORT_LIMITPERCATEGORY — Spring relaxed binding drops dashes — cost if wrong: one doc line.
- Preflight: Ruling: assemble takes CandidateGroup for the empty-occupation fallback — keeps OccupationCategories pure — cost: one parameter.
- Preflight: Ruling: telegram.enabled, not nashi.telegram.enabled — reuses TelegramProperties record — cost: a rename; spec updated.
- Preflight: Ruling: ArtistWork reused in ArtistViewDTO instead of a separate DTO — same fields, ImageCredit is already shared — cost: a DTO later if they diverge; spec updated.
- Preflight: Ruling: backend and frontend tracks run in parallel after T3 — different repos, no shared files; the "never parallel implementers" rule is about file conflicts — cost: a merge-free coordination slip at worst.
- Ruling: F5's CLAUDE.md step moves to the end of F4 — F5 runs now in parallel and CLAUDE.md must describe the filters F4 actually builds — cost: none
- Task F5: implementer DONE_WITH_CONCERNS 22a4ed7 (64/64, lint 16). Ruling: MobileNav about label — plan says «Про проєкт» but also 'matching the header nav', header says «Про нас»; intent is consistency → «Про нас» — cost: one label
- Task T1: Ruling: .gitignore uses /aws/ and /config/ (anchored) — bare config/ would ignore com/nashi/config — cost: none
- Task T1: Ruling: local profile requires OpenAI/S3 keys to equal "local-disabled" (stricter than "looks real") — safer, local file sets exactly that — cost: a custom local key needs a guard tweak
- Task T1: Ruling: extra TelegramService gate (audio download from live bucket) kept — closes a live-service path the brief missed — cost: none
- Ruling: T3 runs before T2 — T3 is the API contract that unblocks the frontend track; T2 has no dependants until task V — cost: none
- Ruling: plan T5 rewritten — occupation trees + chunked VALUES queries with halving (WikiQueries shape times out) — cost: more queries per run (~50)
- Ruling: visual tree excludes filmmaker Q1414443, stand-up comedian, make-up artist, cartographer; writer tree excludes columnist, correspondent — research showed 883 film people in the visual tree — cost: edge people misfiled
- Ruling: T6 adds a Commons-category fallback ("Paintings by …" subcategories) — Wikidata works cover only 4.9% of painters — cost: ~1.3k extra requests per run
- Ruling: written works also accept a uk.wikisource text (articleUrl falls back to it) — only 8% of P50 works have an article — cost: none
- Ruling: T8 MusicBrainz kept — 25% of sampled gaps filled — cost: ~17 min per full run
- Ruling: platforms ESU (P4613), CHTYVO (P13437), WIKIART (P6002) added (sent to T3 implementer mid-task) — high coverage for Ukrainian sources — cost: three enum values
- Ruling: the owner's "born in Ukraine" rule stays even though it brings 2.4k visual / 3.4k writers of other citizenships (Repin, Lem, Conrad) — owner's explicit decision; surfaced in the customer doc — cost: one rule tweak if the owner objects
- Task T3: implementer DONE_WITH_CONCERNS 347d844 (98/98). Ruling: event pins must carry region/city for the region filter — added to T9 brief — cost: none. Ruling: build.gradle test inputs include swagger.json — needed for the contract test to re-run — cost: none
- Ruling: backend order after T3 is T4 → T5 → T6 → T2 → T7 → T8 → T9 → T10 — the data chain is the critical path; the local stack is first needed in T7 step 4 — cost: none
- Task T4: Ruling: Julian-calendar day-precision dates convert to Gregorian (Shevchenko's preferred P569 is Julian 1814-02-25 = Gregorian 1814-03-09) — sent to implementer before review — cost: none; otherwise 19th-century dates are 12 days off
- Ruling: T7 adds P4613/P13437/P6002 to the parser's socialIds — T4's list predates those platforms — cost: none
- Ruling: T6 previewUrl uses /500px- not /400px- — Wikimedia rejects non-standard widths (curl-verified: 400 → HTTP 400; 330/500/960/1280 → 200); the API already rounds portraits to 500px — cost: slightly larger previews
- Task F2: Ruling: player note renders «Плеєр завантажиться з YouTube …» / «… з Spotify …» per card — the plan's "YouTube/Spotify" denoted the alternatives, per-card wording is clearer — cost: one string
- Task T5: Ruling: musicians also listed through the occupation tree in chunks — the 7 old music queries now time out at ~60 s and WDQS caches the broken answer, so retries fail; measured chunked: 20 queries, 0 failures, same 3847 — cost: none (fix round 1 sent before review)
- Task T5: Ruling: no second split dimension for the single-occupation writer×born query (4280 items, 59.1 s) — the report counts failed chunks and the import is new-only, so a re-run of /wiki_import picks missed writers up; RELEASE.md (T10) must say so — cost: one rerun on a slow night
- Task T6: Ruling: a person who is both VISUAL_ARTIST and WRITER gets paintings and writings chosen separately and interleaved up to 12 — otherwise Shevchenko's writings push out all paintings — cost: none
- Task T6: Ruling: visual works without an image (Wikidata P18 missing from imageinfo) are dropped in T7; writers' works keep no image — a gallery tile without an image is useless — cost: a few works lost
- Final: Ruling: two fixers in parallel (one per repo) instead of one — different repos, no shared files — cost: none
- Final: Ruling: /hide refuses PENDING artists (instead of storing the previous status) — /show can then only restore moderated artists — cost: the admin rejects a pending suggestion with the button instead
- Final: Ruling: Telegram links use t.me/%s (spec) not P1630's t.me/s/%s — the preview URL breaks personal accounts — cost: channel links open the app instead of the web preview
- Final: Ruling: frontend generated types are not regenerated for the swagger Region fix — no runtime effect; regen on the next contract change — cost: two stale enum values in types.ts
- Task T7: Ruling (implementer's, accepted): an award counts as Russian only when every country it states is Russia/RSFSR (RSFSR matched via P131 too) — keeps CIS jubilee medals and «Шахтарська слава» — cost: a dual-tagged Russian award slips through
- Task T7: Ruling (implementer's, accepted): an order grade/class is judged by its parent award; Russian Orthodox Church orders do not count as state honours — cost: one constant
- Final (backend fix wave): `/hide` also refuses REJECTED artists (accepted) — otherwise /hide + /show would publish a rejected suggestion
- After merge (2026-11-09): Ruling: titles of federal subjects of Russia (Q4375517, such as Tatarstan) and departmental awards (Q6866472, such as the Ushinsky Medal) are no state honours — the full dry run left out Sukhomlynskyi, two Crimean Tatar artists and a Zakarpattia singer for them; recheck on live data: 161 of 225 are back, 64 still out — cost if wrong: a few holders of regional titles imported.
- After merge (2026-11-09): Ruling: titles kept by the rule for want of data — Wikidata tags no class on «Народний артист Дагестану» (Q28665674), «заслужений художник Республіки Крим» (Q132041532, from the occupation authorities) or «Почесний громадянин Казані» (Q4375531); they still count (3 people) — cost if wrong: hide or add these by hand.

## Deferred minors
- Task F3: minor (deferred): stress-mark regex uses a literal invisible U+0301 — write as ́
- Task F3: minor (deferred): a selected low-count genre sorts to the bottom of topGenres — F4 should show selected chips first
- Task F3: minor (deferred): region/genre URL values not validated against known values; comma inside a genre would not round-trip
- Task F3: minor (deferred): tests miss genre-facet self-exclusion and the backtick mapping; 50 ms wall-clock assert may flake on slow CI
- Task F5: minor (deferred): 6-item mobile nav with 10px labels not checked at 360px — check in task V
- Task F5: minor (deferred): MapTiler key remains in public git history — owner must rotate/restrict (RELEASE.md)
- Task T1: minor (deferred, RECOMMEND FIX in final pass): no test pins META-INF/spring.factories registration and getOrder() > ConfigDataEnvironmentPostProcessor.ORDER — the guard could silently switch off
- Task T1: minor (deferred): local guard lacks a test for a non-placeholder aws.s3.secret-access-key and for the ConnectionString catch branch
- Task T1: minor (deferred): blank/missing OpenAI/S3 keys pass under local; CLAUDE.md wording says "anything but local-disabled" — align doc or reject missing
- Task T1: minor (deferred): AbstractBot has an ungated execute(SendMessage, TelegramClient) helper and public 2-arg overloads — move the gate into AbstractBot or narrow them
- Task T3: minor (deferred): ArtistViewDTO exposes the domain ArtistWork (plan-mandated) — future domain-only fields leak into the API
- Task T3: minor (deferred): fully qualified java.util names in toViewDto works line
- Task F1: minor (deferred): PLATFORM_INFO plain-object lookup — inherited keys (constructor) would throw; use Object.hasOwn
- Task F1: minor (deferred): formatPartialDate lenient on out-of-range day/month; media tests miss m.youtube.com and trailing-slash cases
- Task T4: minor (deferred): ukTitle keeps an unused sitelinks local; Julian JDN uses int and checks only day >= 1
- Task F2: minor (deferred, RECOMMEND FIX in final pass): listen links appear twice (Listen section buttons + Links «Слухати» group) and reading links twice for writers — omit those groups from ArtistLinks when the kind section renders them
- Task F2: minor (deferred): hero photo load failure leaves an empty gradient band (no initials fallback)
- Task F2: minor (deferred): gallery thumbnails carry no licence credit (lightbox only, as briefed); index-fallback React keys
- Task T5: minor (deferred): filmmaker exclusion also drops animators (~57)
- Task F4: minor (deferred): ?artist focus on a pin hidden by active filters flies but the pin is not drawn — ignore filters for the focused pin
- Task F4: minor (deferred): region fitBounds and artist flyTo on the same tick; aria-live on results heading; no component tests; URL region/genre not validated
- Task T5: minor (deferred): ensemble query bypasses the lister (cut-off answer fails the run); «N запитів» without plural forms
- Task T5: minor (deferred): empty/non-SPARQL JSON body stops the run with a technical NPE message; breaker counts failures inside a split subtree
- Task T6: minor (deferred): duplicate-work image choice depends on SPARQL row order (add imageFile tiebreak); works() is ~65 lines doing five jobs; skipped creators not counted in the report
- Task T2: minor (deferred): if docker compose port returns empty for a running mongo the port may drift; backend runs as root without JVM cap (local only); fixed container_names
- Task T7: minor (deferred): missing-image filter after top-12 selection; per-group limit counted before the manual-name check
- Task T7: minor (deferred): honour type rule misses titles typed only «нагорода»/«премія» (e.g. заслужений тренер Росії, regional prizes); wikiDryRun breaks if classes are rebuilt mid-run
- Task T8: minor (deferred): Retry-After uncapped/integer-only; youtube /watch or /playlist links accepted from MB; ended relations not filtered; Spotify /intl-xx/ paths ignored
- Task T8: minor (deferred): breaker count resets before parse (a parse error still counts); breaker reset path untested; 404 stale mbids count as failures
- Task T9: minor (deferred): /hide on a PENDING artist leaves its approval message live; DOTALL multi-line arg; CachingConfigTest reflection-only; slug_unique must be checked in the production startup log
- Task T9: minor (deferred): /show on an artist hidden while still PENDING makes it APPROVED (skips moderation); city cache unbounded; frontend still sends the status param
- Task T10: minor (deferred): CI key check needs VPS_USER read access to the 600 secrets file — fails safely with a misleading "Missing key" message; RELEASE.md doesn't say it; grep ^key= is strict (no spaces)
- Final: minor (deferred): swagger documents POST /api/v1/artists as 200 but it returns 201
- Final: minor (deferred): Wikipedia article link appears in both the licence credit and «Посилання → Інше»; photo credit still shown when the hero photo fails to load

## Notes
- Task T1: note: review package built by controller with application.properties values and aws/ CSV redacted
- Task T1: note: production needs /etc/nashi/nashi-backend.properties before any deploy of this branch (RELEASE.md, T10); old secret values remain in git history — rotation pending (owner)
- Note: controller committed docs 6f1dab8 on B while T3 was running (docs/ only); T3 review package must exclude docs/superpowers
- Task F2: note: unrequested additions — Чтиво button, «Стаття про роботу», blurred-backdrop hero, English musicTags hidden for imports
- Task F2: note (controller visual check): on 390px the floating orb widget may overlap the last MobileNav item — check in V
- Note: the floating palm orb in screenshots is the TanStack Query devtools toggle (dev only, tree-shaken from production) — not a product issue
- Task F4: note: deviations accepted — panel floats with 12 px inset; «Фільтри (n)» excludes search; mobile «Знайдено: N» is the list sheet title
- Task T5: note: between T5 and T7 the import would save visual artists/writers without categories — do not run or merge before T7 (T7 closes it)
- Task T7: note: full dry run crashed on recompile; restarted from a frozen class snapshot, PID 63884, build/full-dry-run.log (crashed log kept). Local stack still holds the old-rule seed.
- V: local reseed with final rule — «знайдено 15106, додано 592 (без місця 89), помилок 0, відсіяно через зв'язок з РФ: 8»; 503 pins (M115 B128 V126 W161); ДахаБраха present; pins gzip 30646 B for 503 pins (~61 B/pin → ~0.9 MB gzip at 15k pins: post-release optimisation candidate).
- V: full dry run 5400/15106 after 1h32m (~0.9 s/artist after listing) → total ≈ 4 h.
