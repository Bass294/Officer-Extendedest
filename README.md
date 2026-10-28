# Officer Extendedest

- Respec officers using a new system called "mastery experience".
- Mastery experience is gained per-officer from combat while at max level.
- An additional 10% of mastery exp gained is gained as global mastery exp.
- A respec costs the exp needed to reach the officer's level from level 1.
- Global mastery exp can substitute for officer mastery exp if insufficient.
- All vanilla combat skills and elite skills may be changed (like AI cores!)
- Officer levels are never modified.

## Settings (LunaLib)
- **Mastery exp rate** (default 1): mastery exp gained per point of exp earned past max level.
- **Global mastery exp rate** (default 0.1): fraction of mastery exp earned that is additionally gained as global
  mastery exp.
- **Respec cost multiplier** (default 1).

## Console commands (Console Commands mod)
- `oee_AddOfficerXP [amount|levelup] <all|officer#|name>`: adds exp to one or all officers; exp past an officer's
  max level becomes mastery exp. Run it without arguments to list your officers and their mastery exp.
- `oee_AddGlobalMasteryXP [amount]`: adds to the global mastery exp pool, or shows it.

## Save compatibility
Safe to add to an ongoing save. Safe to remove: mastery exp is stored as plain numbers, which simply stay unused, and
respecced officers keep their new (vanilla) skills.

## (for nerd/modders) Patches (according to claude)
The game has no hooks for officer exp past max level or for the officer picker, so the mod uses PatchLib patches
(all in `data/scripts/patches`) where vanilla code has no hook. They only add to vanilla behaviour: none of them
skips a vanilla method or replaces its result. Obfuscated game classes are matched by the API interfaces they
implement and their readable method names, never by obfuscated names, so the patches can survive game updates.

**Mastery exp**
- `oee_OfficerXPPatch`: runs before vanilla adds exp to an officer (`OfficerDataAPI.addXP`), which caps an officer's
  exp at their max level and drops the rest. The part past the cap is kept as the officer's mastery exp, and its
  global share is added to the global pool. Officers already at max level get a "gained X mastery experience" line
  in the post-battle text, since vanilla says nothing for them.
- `oee_BattleXPReportPatch`: after vanilla pays the officers their battle exp (`FleetEncounterContext.gainOfficerXP`),
  adds one "Gained X global mastery experience" line for the whole battle, before the player's own exp line.

**Officer picker**
- `oee_OfficerRowPatch`: adds the Respec button to officer rows when they are built (`recreate`). The button sits in
  the slot of vanilla's "Mentor..." button, which vanilla hides at max level.
- `oee_CaptainPickerMasteryPatch`: adds the "Global mastery experience" line above the officer count when the picker
  lays itself out (`sizeChanged`). It keeps itself current, so a respec that spends global mastery exp shows at once.
- `oee_LevelBarPatch`: the officer level bar, used both in officer rows and in the officer hover tooltip. For officers
  at max level who can respec, it fills the bar in green with their progress toward the respec cost, using vanilla's
  own bonus-exp drawing, and in the officer picker replaces the bar's "Maximum level reached" hover with the mastery
  exp amount.

**Respec menu (not a patch)**
The respec menu is the mod's own overlay on the officer picker. Its skill tiles carry vanilla's own skill tooltips,
which the game creates only internally, so the menu reaches them with a little reflection (`ui/oee_Reflection`). If
that ever breaks after a game update, the menu still works without the tooltips, and the log says "couldn't attach
the skill tooltip".

## Requirements
- PatchLib
- LunaLib
