# TODO

Planned work, newest decisions first. A pull request that adds, finishes or drops an item here changes
this file in the same change ([docs/conventions.md](docs/conventions.md#docs-in-the-same-pr)).

## Next

- **Browse by game, with its ROMs under it** ([decision 3](docs/decisions/0003-browse-by-game-not-by-rom.md)).
  Done: (1) the name parser (`RomName`) and the grouping (`GameTitle`); (2) the ROM Play starts
  (`PlayChoice`: the one played last, else the best for the region ranking in Settings); (3) the game view in
  each system's tab: games as a list or a box-art grid, type-ahead, and the selected game's details with its
  ROMs. Next: (4) one browser instead of a tab per system (the Systems list as its left pane), the Game tool
  window's core choice and BIOS warnings moved into the details, then the commands, menus, docs and catalogs,
  and audit Pauta again.
- **A system for a games folder.** A folder can be given a core; let it be given a system name too, for
  box art, when its core covers several (the scanner already takes one).
- **RetroArch's own settings.** A curated set of `retroarch.cfg` options, written only while RetroArch is
  closed (it rewrites the file when it exits), with a one-time backup.
- **Box art for scanned folders.** Match each file's CRC32 against libretro-database so a file named
  "Game (U) [!].smc" finds "Game (USA).png"; then offer to fetch missing art from thumbnails.libretro.com.
- **Nothing of Pauta 6.1.0 is pending in the template.** It meets every MUST and SHOULD a template can
  (Complete); what is left is `app-only`: each app's own website, screenshots and repository settings.
- **What an app made from the template inherits.** After `scripts/rename.sh`, `./mvnw verify` lists each
  `app-only` entry and each pending entry that links one of the template's issues; the release build
  (`-Pdist`) fails on them. Re-run the `pauta-audit` skill, meet what you can, open the app's own issues
  for what is still pending and point the entries at them. The `app-only` entries below are the app's to
  meet, not the template's.

- **The user documentation website** (`app-only` in `pauta.json`). `AppInfo.WEBSITE` (`https://partida-project.dev`) and
  Help → Documentation point at `/docs/v-<version>/`, which the `partida-website` repository serves:
  getting started, one page per feature area, the keyboard reference, Settings and an FAQ, each page's
  frontmatter giving its `category` and `order`, and a What's new generated from `CHANGELOG.md`.
- **Required checks on `master`** (`app-only`). Make **Format** and **Build & Test** required status checks in the
  repository's branch rules (a GitHub setting, not a file).
