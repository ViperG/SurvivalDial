# Developer notes

Players install the release ZIP. This page is only for building or maintaining the mod.

## Local build

Use the game's Java 17 JDK and libraries, which are not redistributed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 -StarsectorPath 'C:\Program Files (x86)\Fractal Softworks\Starsector' -Package
```

The script compiles, runs behavioral tests, generates the README comparison table from the actual preset functions, and packages `dist/SurvivalDial-0.1.3.zip`. JAR publication happens after tests. The ZIP allowlist includes only mod metadata, compiled mod code, its data, and player documentation. The standalone project is linked into the local game's mods directory by a Windows junction; players do not need a junction.

Use this directory as the GitHub repository root. Build outputs are ignored. After the [playtest checklist](playtest.md), create the release and attach the generated ZIP. GitHub's automatic source archive is not the installable package. Nothing has been published automatically.

## Menu and presets

The opening screen shows a preset, optional customization, Apply, and Cancel. Common controls are a short paginated list. All settings is a second, optional layer. Unsupported controls and unchanged/inactive vanilla settings are hidden; existing custom values remain visible and saved. Hard resets editable settings to vanilla, including those earlier custom values. Other presets preserve earlier changes to hidden neutral controls.

Easy and Medium interpolate curated price/availability factors 25% and 60% from Normal reference values toward Survival defaults. Insane increases selected costs by 50% and reduces selected availability by one third. Neutral array components stay unchanged. Retirement windows are Easy 45-50, Medium 35-40, Hard 25-30, and Insane 20-25 cycles for new campaigns. Story points stay at 2 per level. Other editable controls use Survival defaults; presets are not a promise of exhaustive balance changes. New-game-only settings are preserved when presets are applied in an existing campaign. Story points are per-level awards, not a grant to starting points.

The common controls provide verified Normal / Survival default / current comparisons. Detailed controls lacking a verified single-value Normal equivalent say so rather than inventing a number. The full 143-key inventory is retained for research. These are many underlying values, not 143 independent headline features.

## Hyperspace clouds

`sd_hyperspaceCloudCoverage` is a mod-owned, new-campaign-only percentage (0-125), separate from the 143 `ext_` settings. It is stored in the same plain JSON profile but never written into the game's global settings. Older profiles default to 100, which does nothing to terrain. Presets use 50 / 75 / 100 / 125; hazard damage remains 1.25 / 1.6 / 2 / 3 times Normal.

The `onNewGameAfterProcGen` hook runs after vanilla sector generation (confirmed in the installed CampaignGameManager bytecode). In Survival campaigns only, it adjusts vanilla hyperspace tile occupancy once. Thinning removes a deterministic, spatially smoothed selection of occupied tiles. Expansion fills eligible neighboring gaps within the original hyperspace PNG's cloud footprint, protecting the abyss, system entrances, and hyperspace landmarks. A local connectivity check prevents filling tiles that would disconnect clear routes. Expansion stops below its target if insufficient eligible space remains. The achieved tile count is recorded in the game log.

Changed tiles use native game terrain serialization; the completion marker is a Boolean and the profile is a JSON String. No custom terrain plugin or persistent script is added. Generated cloud changes persist if the mod is removed. Existing saves and live preset changes never rerun terrain generation. Map-generation mods, alternate hyperspace maps, and other mods editing terrain require compatibility testing; expansion requires matching PNG/grid dimensions.

The README generator marks creation-only controls with an asterisk using the same entry metadata as the menu. Commission income and story-point gains retain the existing conservative new-game-only classification.

## Compatibility and evidence

Targets 0.98.5a-RC7. The API and compiled implementation were inspected locally; automated tests use real API interfaces with mocked game services. They do not render the UI or simulate a full campaign. Version 0.1.0 was reported working in-game by the user; the revised UI and cloud-generation feature still need in-game verification.

Only the Extended character-creation rule `onNewGameCreationStartExt` is replaced. Normal character creation is untouched. Settings are plain JSON text stored per save. Existing loot, retirement dates, and other generated results are not retroactively rebuilt. Mods that replace character creation (including Nexerelin) or write the same global settings need compatibility testing. Removing the mod removes its overrides after restarting the game, but cannot undo generated campaign consequences.

To regenerate the inventory, extract the local `starfarer.api.zip` outside the repository and run `tools/catalog.ps1` with `-StarsectorPath` and `-ApiSourcePath`. Review its manually recorded bytecode findings when updating game versions. Menu choices live in `SettingsPresentation.java`.

Design reference: [Extended Mode development blog](https://fractalsoftworks.com/2026/06/14/extended-mode/) ([archived feed](https://starsector9.rssing.com/chan-10890146/all_p6.html)). It is a preview; shipped settings take precedence, and ordinary shared settings are excluded from the simple menu. Hyperspace cloud coverage is an explicitly added feature for Survival campaigns, not a vanilla Survival-mode difference.