/*
 * Copyright (c) 2018, Seth <Sethtroll3@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Lars <lars.oernlo@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.mlmalternateoverlays;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.motherlode.MotherlodeSession;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static net.runelite.api.ObjectID.*;

@PluginDescriptor(
	name = "MLM Alt Overlay",
	description = "Alternate icon overlay inside Motherlode Mine",
	tags = {"pay", "dirt", "mining", "mlm", "skilling", "overlay"},
	enabledByDefault = false
)
public class Motherlode2Plugin extends Plugin
{
	private static final Set<Integer> MOTHERLODE_MAP_REGIONS = ImmutableSet.of(14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193);
	private static final Set<Integer> MINE_SPOTS = ImmutableSet.of(ORE_VEIN_26661, ORE_VEIN_26662, ORE_VEIN_26663, ORE_VEIN_26664);
	private static final Set<Integer> MLM_ORE_TYPES = ImmutableSet.of(ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE,
		ItemID.MITHRIL_ORE, ItemID.GOLD_ORE, ItemID.COAL, ItemID.GOLDEN_NUGGET);
	private static final Set<Integer> ROCK_OBSTACLES = ImmutableSet.of(ROCKFALL, ROCKFALL_26680);

	private static final int MAX_INVENTORY_SIZE = 28;

	private static final int SACK_LARGE_SIZE = 162;
	private static final int SACK_SIZE = 81;

	private static final int UPPER_FLOOR_HEIGHT = -500;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MotherlodeOverlay2 overlay;

	@Inject
	private MotherlodeRocksOverlay2 rocksOverlay;

	@Inject
	private Motherlode2Config config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Getter(AccessLevel.PACKAGE)
	private boolean inMlm;

	@Getter(AccessLevel.PACKAGE)
	private int curSackSize;
	@Getter(AccessLevel.PACKAGE)
	private int maxSackSize;
	@Getter(AccessLevel.PACKAGE)
	private Integer depositsLeft;

	@Inject
	private MotherlodeSession session;
	private boolean shouldUpdateOres;
	private Multiset<Integer> inventorySnapshot;

	@Getter(AccessLevel.PACKAGE)
	private final Set<WallObject> veins = new HashSet<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<GameObject> rocks = new HashSet<>();

	@Provides
	Motherlode2Config getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(Motherlode2Config.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(rocksOverlay);

		inMlm = checkInMlm();

		if (inMlm)
		{
			clientThread.invokeLater(this::refreshSackValues);
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(rocksOverlay);
		veins.clear();
		rocks.clear();

		Widget sack = client.getWidget(WidgetInfo.MOTHERLODE_MINE);

		clientThread.invokeLater(() ->
		{
			if (sack != null && sack.isHidden())
			{
				sack.setHidden(false);
			}
		});
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
	{
		OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
		if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY
			&& overlayMenuClicked.getEntry().getOption().equals(MotherlodeOverlay2.MINING_RESET)
			&& overlayMenuClicked.getOverlay() == overlay)
		{
			session.resetRecent();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (inMlm)
		{
			int lastSackValue = curSackSize;
			refreshSackValues();
			shouldUpdateOres = curSackSize < lastSackValue;
			if (shouldUpdateOres)
			{
				// Take a snapshot of the inventory before the new ore is added.
				ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
				if (itemContainer != null)
				{
					inventorySnapshot = HashMultiset.create();
					Arrays.stream(itemContainer.getItems())
						.filter(item -> MLM_ORE_TYPES.contains(item.getId()))
						.forEach(item -> inventorySnapshot.add(item.getId(), item.getQuantity()));
				}
			}
		}
	}


	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		if (!inMlm)
		{
			return;
		}

		WallObject wallObject = event.getWallObject();
		if (MINE_SPOTS.contains(wallObject.getId()))
		{
			veins.add(wallObject);
		}
	}

	@Subscribe
	public void onWallObjectChanged(WallObjectChanged event)
	{
		if (!inMlm)
		{
			return;
		}

		WallObject previous = event.getPrevious();
		WallObject wallObject = event.getWallObject();

		veins.remove(previous);
		if (MINE_SPOTS.contains(wallObject.getId()))
		{
			veins.add(wallObject);
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		if (!inMlm)
		{
			return;
		}

		WallObject wallObject = event.getWallObject();
		veins.remove(wallObject);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (!inMlm)
		{
			return;
		}

		GameObject gameObject = event.getGameObject();
		if (ROCK_OBSTACLES.contains(gameObject.getId()))
		{
			rocks.add(gameObject);
		}
	}

	@Subscribe
	public void onGameObjectChanged(GameObjectChanged event)
	{
		if (!inMlm)
		{
			return;
		}

		GameObject previous = event.getPrevious();
		GameObject gameObject = event.getGameObject();

		rocks.remove(previous);
		if (ROCK_OBSTACLES.contains(gameObject.getId()))
		{
			rocks.add(gameObject);
		}

	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (!inMlm)
		{
			return;
		}

		GameObject gameObject = event.getGameObject();
		rocks.remove(gameObject);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			// on region changes the tiles get set to null
			veins.clear();
			rocks.clear();

			inMlm = checkInMlm();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Prevent code from running while logged out.
			inMlm = false;
		}
	}

	private Integer calculateDepositsLeft()
	{
		if (maxSackSize == 0) // check if maxSackSize has been initialized
		{
			refreshSackValues();
		}

		double depositsLeft = 0;
		int nonPayDirtItems = 0;

		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			return null;
		}

		Item[] result = inventory.getItems();
		assert result != null;

		for (Item item : result)
		{
			// Assume that MLM ores are being banked and exclude them from the check,
			// so the user doesn't see the Overlay switch between deposits left and N/A.
			//
			// Count other items at nonPayDirtItems so depositsLeft is calculated accordingly.
			if (item.getId() != ItemID.PAYDIRT && item.getId() != -1 && !MLM_ORE_TYPES.contains(item.getId()))
			{
				nonPayDirtItems += 1;
			}
		}

		double inventorySpace = MAX_INVENTORY_SIZE - nonPayDirtItems;
		double sackSizeRemaining = maxSackSize - curSackSize;

		if (inventorySpace > 0 && sackSizeRemaining > 0)
		{
			depositsLeft = Math.ceil(sackSizeRemaining / inventorySpace);
		}
		else if (inventorySpace == 0)
		{
			return null;
		}

		return (int) depositsLeft;
	}

	private boolean checkInMlm()
	{
		GameState gameState = client.getGameState();
		if (gameState != GameState.LOGGED_IN
			&& gameState != GameState.LOADING)
		{
			return false;
		}

		int[] currentMapRegions = client.getMapRegions();

		// Verify that all regions exist in MOTHERLODE_MAP_REGIONS
		for (int region : currentMapRegions)
		{
			if (!MOTHERLODE_MAP_REGIONS.contains(region))
			{
				return false;
			}
		}

		return true;
	}

	private void refreshSackValues()
	{
		curSackSize = client.getVar(Varbits.SACK_NUMBER);
		boolean sackUpgraded = client.getVar(Varbits.SACK_UPGRADED) == 1;
		maxSackSize = sackUpgraded ? SACK_LARGE_SIZE : SACK_SIZE;
	}

	/**
	 * Checks if the given point is "upstairs" in the mlm.
	 * The upper floor is actually on z=0.
	 *
	 * @param localPoint
	 * @return
	 */
	boolean isUpstairs(LocalPoint localPoint)
	{
		return Perspective.getTileHeight(client, localPoint, 0) < UPPER_FLOOR_HEIGHT;
	}
}
