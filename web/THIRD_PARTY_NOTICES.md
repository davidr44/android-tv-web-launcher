# Third-party notices

Copied from [CleanFrame](https://github.com/nthnclrk/CleanFrame) (commit fa537d1) with paths adapted to this repository. The MIT licence beside this file (`web/LICENSE`) is CleanFrame's and covers its code; the rest of this launcher is covered by the root `LICENSE`.

The root MIT licence covers CleanFrame's original code and documentation. Third-party material retains the terms below. Keep these notices and the adjacent licence texts when sharing source or a build.

## Fonts

| Material | Terms and source record |
| --- | --- |
| Manrope | SIL Open Font License 1.1; `assets/manrope-OFL.txt` |
| Geist and Newsreader | SIL Open Font License 1.1; licences beside the files in `assets/fonts/`, with exact sources and hashes in `assets/fonts/SOURCES.json` |
| Optional PP Neue Montreal, PP Mori and PP Pangram Sans | Proprietary. Binaries stay in ignored `.local/` storage and are excluded from Git and public exports. The optional local overlay requires the user's own permission to use and embed those files. |

The public app uses Manrope, Geist and Newsreader. A personal widget containing proprietary fonts must stay private. See [Pangram's licensing information](https://pangrampangram.com/pages/eula).

## Icons and trademarks

| Material | Provenance and terms |
| --- | --- |
| Simple Icons service marks (including DAZN and Twitch, added by this project) | CC0; exact revisions and adaptations in `assets/icons/SOURCES.json` and `ADDITIONAL-SOURCES.json`. The CC0 text is retained beside the icons. |
| Disney+, ABC and SBS marks | [Custom Brand Icons](https://github.com/elax46/custom-brand-icons), MIT. The retained licence is `assets/icons/CUSTOM-BRAND-ICONS-LICENSE.txt`. ABC and SBS sources were replaced during the public-source review. Exact revisions, source files and extraction/recolour notes are in `SOURCES.json`. |
| Art, apps, settings and home outlines | Original CleanFrame drawings, covered by CleanFrame's MIT licence. |
| TV, PS5 and PS5 tile marks | Original drawings for this project, covered by the root MIT licence. The PS5 mark is a generic controller, not the PlayStation logo. |

Brand marks identify services. Their owners retain trademark rights; this project claims no endorsement or affiliation. The older Australian TV icon archive's GPL text is retained for historical provenance only; no current icon uses that archive.

## Photographs and derived materials

The fifteen photo pages were individually checked on 20 October 2026. Each displayed its credited author and free use under the [Unsplash License](https://unsplash.com/license). [Photo credits](../docs/photo-credits.md) links every page. Runtime paths are in `assets/wallpapers/SOURCES.json`.

Photographers retain their rights. Wallpapers, thumbnails, panel backgrounds and material fields derive from the credited photographs and retain their terms. The Unsplash licence permits copying, modification and distribution subject to its restrictions, including on selling unmodified images and building a competing image service.

## Development dependencies and exports

Development dependencies retain the licences in their npm packages. They are not copied into the TV JavaScript runtime.

This project does not ship the optional proprietary fonts. Preserve the included font and icon licence texts when sharing source or a build.

## Bible verses

The verse of the day (`assets/verses.js`) is from the [Berean Standard Bible](https://berean.bible), dedicated to the public domain (CC0), via [scrollmapper/bible_databases](https://github.com/scrollmapper/bible_databases).
