# Officer Extendedest

Starsector mod (0.98a-RC8). Id `officer_extendedest`, prefix `oee_`. Java in `data/scripts` (package `data.scripts`),
compiled into `jars/OfficerExtendedest.jar` by `build.sh`. Depends on PatchLib (compiled against
`! PatchLib !-1.0.1/jars/PatchLibAPI.jar`) and LunaLib.

## What it does

- **Officer mastery experience**: `patches/oee_OfficerXPPatch` hooks `OfficerDataAPI.addXP(long, TextPanelAPI,
  boolean)` before it runs; experience a player-fleet officer would lose to the level cap is banked 1:1 (times the
  rate setting) in the officer's memory (`$oee_masteryXP`).
- **Global mastery experience**: a share (default 10%) of the mastery experience officers bank is added on top
  to sector memory (`$oee_globalMasteryXP`), by the same hook. It pays any shortfall in a respec.
- **Post-battle text**: "<officer> gained X mastery experience" per officer (from the XP hook), then one
  "Gained X global mastery experience" line (`patches/oee_BattleXPReportPatch`, after `gainOfficerXP`, before the
  player's XP). Vanilla's officer-line color (`tooltipTitleAndLightHighlightColor`), amounts in the story point color.
- **Respec**: `patches/oee_OfficerRowPatch` adds a "Respec..." button (`ui/oee_RespecButton`) to officer rows in the
  officer picker, in the slot of vanilla's "Mentor..." button, for non-mercenary human officers at max level.
  It opens `ui/oee_RespecDialog`, a modal overlay on the picker modelled on the AI core skill picker.
- Cost: experience from level 1 to the officer's level (or max level, if higher), times the cost setting.
  Exceptional cryopod officers are level 7 above a max level of 6, so their own level counts.
- The menu requires exactly as many skills and elite skills as the officer has now, counting only vanilla combat skills (ids in
  the game's own `skill_data.csv`). Other skills are kept and ignored. Level isn't consulted.

Logic: `data/scripts/mastery/oee_Mastery.java`. Settings: `data/config/LunaSettings.csv`, read by
`data/scripts/oee_Settings.java`. An earlier officer limit setting was removed; `oee_ModPlugin.onGameLoad` clears
any flat bonus it left on the player's officer number stat (modifier id `officer_extendedest`).

## Inferred targets to re-check after a game update

The officer row class is matched by package `com.fs.starfarer.coreui` plus its methods `updateStatusString`,
`isPastMax`, `getOfficerData`; the button hooks `recreate()` and reads the row's `CaptainPickerDialog` field.
The button position copies vanilla's row layout (Mentor slot: 203x20, 7 px from the right, 20 px from the bottom).

`patches/oee_CaptainPickerMasteryPatch` hooks `CaptainPickerDialog.sizeChanged` (2 params) and adds a "Global mastery
experience: X" line above vanilla's "Officers: x / y" label, found among `getInnerPanel().getChildrenCopy()` by its
text prefix; skipped when that label is hidden (AI core picking).

`patches/oee_LevelBarPatch` targets the officer level bar (0.98a-RC8: `com.fs.starfarer.coreui.null`, matched by
package + `UIPanelAPI` + `getBar`/`getLabel`), used by picker rows and the officer hover tooltip. After its
constructor (5 params) and every `advanceImpl`, for respec-eligible officers in the player's fleet it finds the child
with `setCurrWithFraction(float, Color, float)` and calls it with (0, story color, mastery / cost), and where vanilla
set a hover tooltip (`getTooltip` non-null: picker rows only) replaces it via `addTooltipTo`, re-replacing whenever
vanilla rebuilds its own.

The respec menu (`ui/oee_RespecDialog`) is a translation of vanilla's AI core skill picker (layout constants and
the skill-tile drawing of `com.fs.starfarer.coreui.voidsuper`). Its skill tooltips come from vanilla through
`ui/oee_Reflection` (MethodHandles over java.lang.reflect, names only as strings): the static
`com.fs.starfarer.ui.impl.StandardTooltipV2.createSkillTooltip` (8 parameters), `setTooltip` (2) on the tile,
`setBeforeShowing` (1) on the tooltip and `setTooltipPositionRelativeToAnchor` (3). If one breaks, the tiles still
work and the failure is logged as "couldn't attach the skill tooltip".

## Console commands

`data/console/commands.csv` (Console Commands is compile-only, not a mod dependency):
- `oee_AddOfficerXP [amount|levelup] <all|officer#|name>`: experience through vanilla `addXP`, so the overflow is
  banked as mastery by the XP patch; negative amounts remove mastery; no arguments lists officers.
- `oee_AddGlobalMasteryXP [amount]`: adds to or shows the global pool.

## Version checker

`officer_extendedest.version` (listed in `data/config/version/version_files.csv`) points at
`github.com/Bass294/Officer-Extendedest` (`main` branch; release asset `Officer_Extendedest.zip`), the same layout as
Riding Shotgun. Bump it together with `mod_info.json` `version` and `changelog.txt`.
