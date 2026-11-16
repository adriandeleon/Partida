# 3. Browse by game, with its ROMs under it, in one library browser

- Status: accepted; phase 1 (the name parser and the grouping) is built, phases 2 to 4 are planned
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
- **Play starts the ROM last played** of that game. A game never played starts its best ROM
  (`GameTitle.ORDER`: a clean release, verified, the original before an alternate, the latest revision, the
  first disc), and a region ranking in Settings will decide between regions.
- **One browser**, not a tab per system: systems down the left, the system's games in the middle as a list or a
  box-art grid (remembered per system), and on the right the selected game's box art, a Play button and its
  ROMs, each of which can be played.

The work goes in four phases: (1) `RomName` and `GameTitle`, pure and tested; (2) the ROM Play picks: the
last one played, read from RetroArch's history too, else the best; (3) the browser; (4) retiring the system
tabs and the Game tool window, then the commands, menus, docs and catalogs, and a new Pauta audit.

## Consequences

The library is browsed by what people look for, a game, and a set of a thousand files reads as a few hundred
games. Grouping is by name only: two games with the same name on one system (rare, and usually a re-release)
are one entry, and a game whose variants are named differently (`Pokemon Red` and `Pokemon - Red Version`)
is two. Matching by checksum against libretro-database, already in TODO.md for box art, would settle both.
The system tabs, the Game tool window and the rules they meet (the TABS and tool window pages) change in
phase 4, so the Pauta audit is run again then.
