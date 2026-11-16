# 3. Browse by game, with its ROMs under it, in one library browser

- Status: accepted; built in four phases: the name parser and the grouping, the ROM Play starts, the game
  view, and one browser in place of the system tabs
- Date: 2026-11-16

## Context

A system opened as a tab with one row per file. Collections hold many files per game: regional releases,
revisions, alternate and bad dumps, hacks, translations, betas. GoodTools sets name them
`Super Mario Bros. (E) (REVA) [!]`, `(U) [!]`, `(JU) [b1]`; No-Intro and Redump sets
`Super Mario Bros. (Europe) (Rev A)`, `(USA, Europe)`, `(Beta)`, `(Disc 1)`. A system of a few hundred games
became a list of a thousand rows, most of them variants nobody browses for.

## Decision

- **One entry per game, per system.** `RomName` reads a name in either scheme: its title (a trailing
  article moved to the front, so `Legend of Zelda, The` is `The Legend of Zelda`), a grouping key, regions,
  languages, revision, disc and what the tags say about the dump and the release. `GameTitle.group` puts every
  ROM of a game in one entry, best first. A tag it does not know is kept as written.
- **Hidden by default:** bad dumps and overdumps; hacks, trainers, fixes and translations; betas, protos and
  demos; unlicensed and pirate releases (`RomName.Flag.HIDDEN`). A hidden ROM stays listed under its game; a
  game whose ROMs are all hidden drops out of browsing. A filter shows them again.
- **Play starts the ROM last played** of that game (`PlayChoice`): the one of its ROMs nearest the top of
  RetroArch's play history, which every launch puts there, from Partida or from RetroArch; else the ROM
  Partida last started for it (`lastPlayed` in `session.json`, for when the history is off or has dropped
  it). A game never played starts its best ROM (`GameTitle.order`: a clean release, the region ranked first,
  verified, the original before an alternate, the latest revision, the first disc). The ranking is the
  user's (Settings → Library → Preferred regions) or, by default, one from the system's country (`Regions`):
  its own region, then Europe's or the USA's as its TV standard favors, the world release, Japan last.
- **One browser**, not a tab per system: systems down the left, the system's games in the middle as a list or a
  box-art grid (remembered per system), and on the right the selected game's box art, a Play button and its
  ROMs, each of which can be played.

Phase 3 built the game view inside each system's tab, beside the Systems tool window, so the three panes
are on screen before phase 4 retires the tabs: the games as a list or a box-art grid (`SystemView`; Toggle
Grid View, kept per system in `library.gridSystems`), type-ahead by title, and the selected game's details
(`GameDetails`): its box art, Play with the ROM it starts, and its ROMs, the hidden ones dimmed, any of them
played with Play This ROM. Show hidden ROMs is a setting with its command. The Systems window counts games.

Phase 4 retired the tabs: `Browser` shows one system at a time, chosen in the Systems tool window (open from
the first run, its selection showing each system as the user walks it), and the Game tool window's core
choice and BIOS warnings moved into the details. The Welcome page became the browser's empty state, with the
ways in and their chords (WELC-02); the tab commands, the tab switcher and Toggle Tab Bar went with the tabs.

The work went in four phases: (1) `RomName` and `GameTitle`, pure and tested; (2) the ROM Play picks: the
last one played, read from RetroArch's history too, else the best; (3) the browser; (4) retiring the system
tabs and the Game tool window, then the commands, menus, docs and catalogs, and a new Pauta audit.

## Consequences

The library is browsed by what people look for, a game, and a set of a thousand files reads as a few hundred
games. Grouping is by name only: two games with the same name on one system (rare, and usually a re-release)
are one entry, and a game whose variants are named differently (`Pokemon Red` and `Pokemon - Red Version`)
is two. Matching by checksum against libretro-database, already in TODO.md for box art, would settle both.
With no tabs the user opens and closes, Pauta's Tabs rules (TABS-01 to TABS-12) and the Welcome page's
(WELC-01, WELC-03, WELC-04, WELC-11) no longer apply; WELC-02, the empty state, does. The Pauta audit was run
again after phase 4.
