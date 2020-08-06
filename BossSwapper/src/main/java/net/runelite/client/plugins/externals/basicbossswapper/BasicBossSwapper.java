/*
 * Copyright (c) 2019-2020, ganom <https://github.com/Ganom>
 * Copyright (c) 2020 andrewterra <https://github.com/andrewterra>
 * All rights reserved.
 * Licensed under GPL3, see LICENSE for the full scope.
 */
package net.runelite.client.plugins.externals.basicbossswapper;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.util.Text;
import net.runelite.api.vars.InterfaceTab;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.externals.utils.ExtUtils;
import net.runelite.client.plugins.externals.utils.Tab;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import org.pf4j.Extension;


@Extension
@PluginDescriptor(
	name = "Basic Boss Swapper",
	description = "Automatically swaps prayers for Olm, Nylo, and Verzik",
	tags = {"prayer", "olm", "bot", "swap"},
	type = PluginType.UTILITY
)
@Slf4j
@SuppressWarnings("unused")
@PluginDependency(ExtUtils.class)
public class BasicBossSwapper extends Plugin
{
	private static final String MAGE = "the great olm fires a sphere of magical power your way";
	private static final String RANGE = "the great olm fires a sphere of accuracy and dexterity your way";
	private static final String MELEE = "the great olm fires a sphere of aggression your way";
	private static final int NIGHTMARE_MELEE_ATTACK = 8594;
	private static final int NIGHTMARE_RANGE_ATTACK = 8596;
	private static final int NIGHTMARE_MAGIC_ATTACK = 8595;
	private static final int NIGHTMARE_CURSE = 8599;

	@Inject
	private Client client;

	@Inject
	private BasicBossSwapperConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ExtUtils utils;

	@Inject
	private KeyManager keyManager;

	private ExecutorService executor;
	private boolean swapMage;
	private boolean swapRange;
	private boolean swapMelee;
	private Robot robot;
	private NPC nylo;
	private boolean run;
	private int prevNylo;
	private NPC nm;
	private boolean inFight;
	private boolean cursed;
	private int attacksSinceCurse;

	@Provides
	BasicBossSwapperConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BasicBossSwapperConfig.class);
	}

	private final HotkeyListener toggle = new HotkeyListener(() -> config.toggle())
	{
		@Override
		public void hotkeyPressed()
		{
			run = !run;
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				if (run)
				{
					sendMessage("Boss Swapper Activated");
				}
				else
				{
					sendMessage("Boss Swapper De-Activated");
				}
			}
		}
	};

	@Override
	protected void startUp() throws AWTException
	{
		executor = Executors.newSingleThreadExecutor();
		keyManager.registerKeyListener(toggle);
		robot = new Robot();
		reset();
	}

	@Override
	protected void shutDown()
	{
		executor.shutdown();
		keyManager.unregisterKeyListener(toggle);
		robot = null;
		reset();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (swapMage)
		{
			clickPrayer(Prayer.PROTECT_FROM_MAGIC);
			swapMage = false;
		}
		else if (swapRange)
		{
			clickPrayer(Prayer.PROTECT_FROM_MISSILES);
			swapRange = false;
		}
		else if (swapMelee)
		{
			clickPrayer(Prayer.PROTECT_FROM_MELEE);
			swapMelee = false;
		}

		if (nylo != null && run)
		{
			if (nylo.getId() == prevNylo)
			{
				return;
			}

			switch (nylo.getId())
			{
				case 8355:
				{
					prevNylo = nylo.getId();
					clickPrayer(Prayer.PROTECT_FROM_MELEE);
					clickPrayer(Prayer.PIETY);
					clickItem(config.melee());
				}
				return;
				case 8356:
				{
					prevNylo = nylo.getId();
					clickPrayer(Prayer.PROTECT_FROM_MAGIC);
					clickPrayer(Prayer.AUGURY);
					clickItem(config.mage());
				}
				return;
				case 8357:
				{
					prevNylo = nylo.getId();
					clickPrayer(Prayer.PROTECT_FROM_MISSILES);
					clickPrayer(Prayer.RIGOUR);
					clickItem(config.range());
				}
				return;
				default:
					break;
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!run || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String msg = Text.standardize(event.getMessage());

		if (msg.startsWith(MAGE))
		{
			swapMage = true;
		}
		else if (msg.startsWith(RANGE))
		{
			swapRange = true;
		}
		else if (msg.startsWith(MELEE))
		{
			swapMelee = true;
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (run)
		{
			return;
		}

		int id = event.getProjectile().getId();

		switch (id)
		{
			case 1339:
				if (config.swapAutos() && !client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
				{
					swapMage = true;
				}
				break;
			case 1594:
				if (!client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
				{
					swapMage = true;
				}
				break;
			case 1340:
				if (config.swapAutos() && !client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
				{
					swapRange = true;
				}
				break;
			case 1593:
				if (!client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
				{
					swapRange = true;
				}
				break;
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!inFight || nm == null)
		{
			return;
		}

		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		int id = npc.getId();
		int animationId = npc.getAnimation();

		switch (animationId) {
			case NIGHTMARE_MAGIC_ATTACK:
				attacksSinceCurse++;
				if (cursed) {
					if (config.swapNightmareAutos() && !client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))
						swapMelee = true;
				} else {
					if (config.swapNightmareAutos() && !client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
						swapMage = true;
				}
				break;
			case NIGHTMARE_MELEE_ATTACK:
				attacksSinceCurse++;
				if (cursed) {
					if (config.swapNightmareMelee() && !client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
						swapRange = true;
				} else {
					if (config.swapNightmareMelee() && !client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))
						swapMelee = true;
				}
				break;
			case NIGHTMARE_RANGE_ATTACK:
				attacksSinceCurse++;
				if (cursed) {
					if (config.swapNightmareAutos() && !client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
						swapMage = true;
				} else {
					if (config.swapNightmareAutos() && !client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
						swapRange = true;
				}
				break;
			case NIGHTMARE_CURSE:
				cursed = true;
				attacksSinceCurse = 0;
				break;
		}

		if (cursed && attacksSinceCurse == 5)
		{
			//curse is removed when she phases, or does 5 attacks
			cursed = false;
			attacksSinceCurse = -1;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		final NPC npc = event.getNpc();

		switch (npc.getId())
		{
			case NpcID.NYLOCAS_VASILIAS:
			case NpcID.NYLOCAS_VASILIAS_8355:
			case NpcID.NYLOCAS_VASILIAS_8356:
			case NpcID.NYLOCAS_VASILIAS_8357:
				nylo = npc;
				break;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		final NPC npc = event.getNpc();

		if (npc.getId() == NpcID.NYLOCAS_VASILIAS)
		{
			nylo = null;
		}
	}

	@Subscribe
	public void onNpcDefinitionChanged(NpcDefinitionChanged event)
	{
		final NPC npc = event.getNpc();

		if (npc == null)
		{
			return;
		}

		//this will trigger once when the fight begins
		if (npc.getId() == 9432)
		{
			//reset everything
			reset();
			nm = npc;
			inFight = true;
		}

		//if ID changes to 9431 (3rd phase) and is cursed, remove the curse
		if (cursed && npc.getId() == 9431)
		{
			cursed = false;
			attacksSinceCurse = -1;
		}

	}

	private void reset()
	{
		inFight = false;
		nm = null;
		cursed = false;
		attacksSinceCurse = 0;
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		GameState gamestate = event.getGameState();

		//if loading happens while inFight, the user has left the area (either via death or teleporting).
		if (gamestate == GameState.LOADING && inFight)
		{
			reset();
		}
	}

	private void clickPrayer(Prayer prayer)
	{
		if (client.isPrayerActive(prayer) || client.getBoostedSkillLevel(Skill.PRAYER) < 1)
		{
			return;
		}

		final Widget widget = client.getWidget(prayer.getWidgetInfo());

		if (widget == null)
		{
			return;
		}

		final Rectangle bounds = widget.getBounds();

		executor.submit(() ->
		{
			if (client.getVar(VarClientInt.INTERFACE_TAB) != InterfaceTab.PRAYER.getId())
			{
				robot.keyPress(utils.getTabHotkey(Tab.PRAYER));
				try
				{
					Thread.sleep(20);
				}
				catch (InterruptedException e)
				{
					return;
				}
			}

			utils.click(bounds);

			try
			{
				Thread.sleep(getMillis());
			}
			catch (InterruptedException e)
			{
				return;
			}

			if (client.isPrayerActive(prayer))
			{
				robot.keyPress(utils.getTabHotkey(Tab.INVENTORY));
			}

			try
			{
				Thread.sleep(getMillis());
			}
			catch (InterruptedException ignored)
			{
			}
		});
	}

	private void clickItem(String itemList)
	{
		if (itemList.isEmpty())
		{
			return;
		}

		List<WidgetItem> inv = utils.getItems(utils.stringToIntArray(itemList));

		if (client.getVar(VarClientInt.INTERFACE_TAB) != InterfaceTab.INVENTORY.getId())
		{
			executor.submit(() -> robot.keyPress(utils.getTabHotkey(Tab.INVENTORY)));
		}

		List<Rectangle> rectangles = new ArrayList<>();

		for (WidgetItem item : inv)
		{
			rectangles.add(item.getCanvasBounds());
		}

		executor.submit(() ->
		{
			for (Rectangle item : rectangles)
			{
				if (item != null)
				{
					utils.click(item);
					try
					{
						Thread.sleep(getMillis());
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}
			}
		});
	}

	public int getMillis()
	{
		return (int) (Math.random() * config.randLow() + config.randHigh());
	}

	private void sendMessage(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", ColorUtil.wrapWithColorTag(message, Color.MAGENTA), "");
	}
}
