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

import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

class MotherlodeRocksOverlay2 extends Overlay
{
	private static final int MAX_DISTANCE = 2350;

	private final Client client;
	private final Motherlode2Plugin plugin;
	private final Motherlode2Config config;
	private BufferedImage miningIcon;

	@Inject
	MotherlodeRocksOverlay2(Client client, Motherlode2Plugin plugin, Motherlode2Config config, SkillIconManager iconManager, SpriteManager spriteManager)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		spriteManager.getSpriteAsync(1456, 0, img -> this.miningIcon=img);
	}

	private void renderTiles(Graphics2D graphics, Player local)
	{
		LocalPoint localLocation = local.getLocalLocation();

		if (config.showVeins())
		{
			for (WallObject vein : plugin.getVeins())
			{
				LocalPoint location = vein.getLocalLocation();
				if (localLocation.distanceTo(location) <= MAX_DISTANCE)
				{
					// Only draw veins on the same level
					if (plugin.isUpstairs(localLocation) == plugin.isUpstairs(vein.getLocalLocation()))
					{
						renderVein(graphics, vein);
					}
				}
			}
		}


	}

	private void renderVein(Graphics2D graphics, WallObject vein)
	{
		Point canvasLoc = Perspective.getCanvasImageLocation(client, vein.getLocalLocation(), miningIcon, 150);

		if (canvasLoc != null)
		{
			graphics.drawImage(miningIcon, canvasLoc.getX(), canvasLoc.getY(), null);
		}
	}

	private void renderRock(Graphics2D graphics, GameObject rock)
	{
		Polygon poly = Perspective.getCanvasTilePoly(client, rock.getLocalLocation());

		if (poly != null)
		{
			OverlayUtil.renderPolygon(graphics, poly, Color.red);
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if ((!config.showVeins()|| !plugin.isInMlm()))
		{
			return null;
		}

		Player local = client.getLocalPlayer();

		renderTiles(graphics, local);

		return null;
	}
}
