# Double Max

A purely cosmetic RuneLite plugin. It displays every skill as if **level 99's
worth of XP (13,034,431) had been removed**, turning the post-max grind into a
fresh journey to 99.

Nothing is sent to the server and no real XP changes — this only rewrites what
the client draws.

## What it does

- **Rewrites the Skills tab.** Each skill shows its Double Max level instead of
  the real one. The skill total updates to match.
- **Overlay panel.** Shows your Double Max total level, a recalculated combat
  level, and (optionally) every skill.
- **Level-up pop-ups.** When a skill crosses a Double Max level boundary, a
  pop-up appears (and optionally a chat message fires).

## The maths

`offsetXp = XP for level 99 = 13,034,431`

For each skill: `displayLevel = levelForXp( max(0, realXp - offsetXp) )`

Consequences:
- A fully maxed account (all 99s) reads as **all level 1** — the counter resets.
- A skill needs **26,068,862 XP** (two 99s) to read 99 again. All skills at that
  point = Double Max.
- Any skill currently under 99 reads as level 1 until you pass 99.

## Config

| Setting | Default | Notes |
|---|---|---|
| Levels to subtract | 99 | Set higher for triple max, etc. |
| Allow virtual levels (>99) | off | If off, the fake level caps at 99 |
| Rewrite the Skills tab | on | Turn off to leave the in-game tab alone |
| Show overlay | on | |
| List every skill in overlay | off | Otherwise just total + combat |
| Show Double Max combat level | on | |
| Level-up pop-ups | on | |
| Level-up chat message | on | |
| Pop-up duration (s) | 5 | |

## Build / run

Standard external-plugin layout. Open in IntelliJ as a Gradle project, then:

- Run `DoubleMaxPluginTest.main()` to launch RuneLite with the plugin sideloaded.
- Or `./gradlew build` to produce a jar.

Requires JDK 11.

## Notes

- This is the same client hook the built-in *Virtual Levels* plugin uses
  (`skillTabBaseLevel` / `skillTabTotalLevel` script callbacks plus
  `queueChangedSkill` to redraw), so it is cosmetic-only and Plugin Hub
  eligible.
- For a Plugin Hub submission, reformat to RuneLite's checkstyle (tabs) and
  follow the steps at https://github.com/runelite/plugin-hub.
