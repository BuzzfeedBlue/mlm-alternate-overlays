package com.mlmalternateoverlays;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TestPlugin
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MotherlodeAltPlugin.class);
		RuneLite.main(args);
	}
}
