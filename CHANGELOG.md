# Changelog

## Unreleased

- Add **Enter exact value...** for numeric settings, with support for single values, per-hull-size values, and the retirement range.
- Add reusable custom presets saved in `saves/common/survival_dial_custom_presets.json.data`, shared across campaigns.
- Add validation for custom preset names and values, plus save/load/delete tests.

## 0.1.2 - clouds and hazards

- Add new-campaign hyperspace cloud coverage targets: Easy 50%, Medium 75%, Hard 100%, Insane 125%, with an optional 0-125% slider.
- Keep Hard, Normal campaigns, and existing maps unchanged. Increased coverage is limited by protected clear areas.
- Expose hazard damage in common controls and the preset table: 1.25x / 1.6x / 2x / 3x Normal damage.
- Mark new-game-only table rows with an asterisk, including commission income, story points, retirement, and cloud coverage.
- Correct the preset explanation to reflect retirement and cloud adjustments.

## 0.1.1 - simpler menu

- Show presets first, with optional common controls and a separate full-settings menu.
- Add plain names, before/after review, and Normal comparisons for verified values.
- Hide unchanged or inactive defaults, preserving access to older custom values.
- Keep neutral fuel-use components unchanged in presets.
- Edit the retirement window together and validate it before accepting changes. New-campaign presets use 45-50 cycles on Easy, 35-40 on Medium, 25-30 on Hard, and 20-25 on Insane.
- Put a generated preset comparison immediately after the README intro; move build instructions into developer documentation.

## 0.1.0 - release candidate

- Catalog all 143 Extended settings from 0.98.5a-RC7.
- Add native configuration during vanilla character creation and via Ctrl+F8 during an Extended campaign.
- Add Easy, Medium, Hard, and Insane preset selection, custom sliders, explanatory tooltips, and boolean switches.
- Preserve per-save settings with validation, Apply/Cancel, cache invalidation, and restoration between campaigns.
- Include a reproducible local build, behavioral tests, and an installable ZIP packager without game libraries.
