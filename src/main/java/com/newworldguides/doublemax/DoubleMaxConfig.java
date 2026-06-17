package com.newworldguides.doublemax;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DoubleMaxConfig.GROUP)
public interface DoubleMaxConfig extends Config
{
	String GROUP = "doublemax";

	@Range(min = 1, max = 126)
	@ConfigItem(
		keyName = "offsetLevel",
		name = "Levels to subtract",
		description = "How many levels' worth of XP to remove from every skill. 99 = one full max (13,034,431 XP). Set higher for triple max, etc.",
		position = 0
	)
	default int offsetLevel()
	{
		return 99;
	}

	@ConfigItem(
		keyName = "virtualLevels",
		name = "Allow virtual levels (>99)",
		description = "If on, a skill with more than two maxes' worth of XP can read above 99. If off, the Double Max level caps at 99.",
		position = 1
	)
	default boolean virtualLevels()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overrideSkillTab",
		name = "Rewrite the Skills tab",
		description = "Replace the real numbers shown in the in-game Skills tab with Double Max levels.",
		position = 2
	)
	default boolean overrideSkillTab()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the Double Max overlay panel.",
		position = 3
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPerSkill",
		name = "List every skill in overlay",
		description = "Show all skills in the overlay. If off, only the total (and combat) is shown.",
		position = 4
	)
	default boolean showPerSkill()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCombat",
		name = "Show Double Max combat level",
		description = "Include a recalculated combat level based on Double Max levels.",
		position = 5
	)
	default boolean showCombat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "levelUpPopup",
		name = "Level-up pop-ups",
		description = "Show an on-screen pop-up when a skill gains a Double Max level.",
		position = 6
	)
	default boolean levelUpPopup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "levelUpChat",
		name = "Level-up chat message",
		description = "Also send a game chat message on a Double Max level-up.",
		position = 7
	)
	default boolean levelUpChat()
	{
		return true;
	}

	@Range(min = 1, max = 15)
	@ConfigItem(
		keyName = "popupSeconds",
		name = "Pop-up duration (s)",
		description = "How long a level-up pop-up stays on screen.",
		position = 8
	)
	default int popupSeconds()
	{
		return 5;
	}
}
