# TODO

Planned work, newest decisions first. A pull request that adds, finishes or drops an item here changes
this file in the same change ([docs/conventions.md](docs/conventions.md#docs-in-the-same-pr)).

## Next

- **Group a game's ROMs by checksum, not only by name** ([decision 3](docs/decisions/0003-browse-by-game-not-by-rom.md)).
  Two games of one name merge, and variants named differently (`Pokemon Red`, `Pokemon - Red Version`) split;
  libretro-database's CRCs would settle both, with the box-art matching below.
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
