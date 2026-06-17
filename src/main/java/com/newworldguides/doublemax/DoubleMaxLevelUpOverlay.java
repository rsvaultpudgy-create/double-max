package com.newworldguides.doublemax;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class DoubleMaxLevelUpOverlay extends OverlayPanel
{
	private String message;
	private long hideAt;

	@Inject
	private DoubleMaxLevelUpOverlay()
	{
		setPosition(OverlayPosition.TOP_CENTER);
		getPanelComponent().setPreferredSize(new Dimension(220, 0));
	}

	void show(Skill skill, int level, int seconds)
	{
		this.message = "Your " + DoubleMaxPlugin.skillName(skill) + " level is now " + level + "!";
		this.hideAt = System.currentTimeMillis() + seconds * 1000L;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (message == null || System.currentTimeMillis() > hideAt)
		{
			return null;
		}

		getPanelComponent().getChildren().clear();
		getPanelComponent().getChildren().add(TitleComponent.builder()
			.text("Double Max Level Up!")
			.color(Color.YELLOW)
			.build());
		getPanelComponent().getChildren().add(LineComponent.builder()
			.left(message)
			.build());

		return super.render(graphics);
	}
}
