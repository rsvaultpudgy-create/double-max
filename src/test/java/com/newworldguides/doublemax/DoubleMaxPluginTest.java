package com.newworldguides.doublemax;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DoubleMaxPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(DoubleMaxPlugin.class);
		RuneLite.main(args);
	}
}
