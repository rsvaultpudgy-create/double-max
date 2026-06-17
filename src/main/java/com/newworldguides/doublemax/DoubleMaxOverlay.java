package com.newworldguides.doublemax;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class DoubleMaxOverlay extends OverlayPanel
{
	private final DoubleMaxPlugin plugin;
	private final DoubleMaxConfig config;
	private final Client client;

	@Inject
	private DoubleMaxOverlay(DoubleMaxPlugin plugin, DoubleMaxConfig config, Client client)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		setPosition(OverlayPosition.TOP_LEFT);
		getPanelComponent().setPreferredSize(new Dimension(150, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		getPanelComponent().getChildren().clear();

		getPanelComponent().getChildren().add(TitleComponent.builder()
			.text("Double Max")
			.color(Color.YELLOW)
			.build());

		getPanelComponent().getChildren().add(LineComponent.builder()
			.left("Total")
			.right(Integer.toString(plugin.doubleMaxTotalLevel()))
			.build());

		if (config.showCombat())
		{
			getPanelComponent().getChildren().add(LineComponent.builder()
				.left("Combat")
				.right(Integer.toString(doubleMaxCombatLevel()))
				.build());
		}

		if (config.showPerSkill())
		{
			for (Skill skill : Skill.values())
			{
				if ("OVERALL".equals(skill.name()))
				{
					continue;
				}
				getPanelComponent().getChildren().add(LineComponent.builder()
					.left(DoubleMaxPlugin.skillName(skill))
					.right(Integer.toString(plugin.doubleMaxLevel(skill)))
					.build());
			}
		}

		return super.render(graphics);
	}

	private int doubleMaxCombatLevel()
	{
		return Experience.getCombatLevel(
			plugin.doubleMaxLevel(Skill.ATTACK),
			plugin.doubleMaxLevel(Skill.STRENGTH),
			plugin.doubleMaxLevel(Skill.DEFENCE),
			plugin.doubleMaxLevel(Skill.HITPOINTS),
			plugin.doubleMaxLevel(Skill.MAGIC),
			plugin.doubleMaxLevel(Skill.RANGED),
			plugin.doubleMaxLevel(Skill.PRAYER)
		);
	}
}
