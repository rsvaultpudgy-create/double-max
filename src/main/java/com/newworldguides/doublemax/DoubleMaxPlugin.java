package com.newworldguides.doublemax;

import com.google.inject.Provides;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Double Max",
    description = "Shows every skill as if a full max (level 99's worth of XP) were removed, so you can chase a second max.",
    tags = {"skills", "levels", "virtual", "xp", "total", "max", "double"}
)
public class DoubleMaxPlugin extends Plugin
{
    // Skill-tab build/refresh client scripts. We use these to also rewrite the top (boosted) number,
    // which the game draws straight from the real level with no script callback hook.
    private static final int SCRIPTID_STATS_INIT = 394;
    private static final int SCRIPTID_STATS_REFRESH = 393;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DoubleMaxConfig config;

    @Inject
    private DoubleMaxOverlay overlay;

    @Inject
    private DoubleMaxLevelUpOverlay levelUpOverlay;

    // Last Double Max level we saw per skill, used to detect level-ups.
    private final Map<Skill, Integer> lastDoubleMaxLevel = new EnumMap<>(Skill.class);

    // Becomes true only after we've seeded levels, so login XP loads don't fire pop-ups.
    private boolean ready;

    // The per-skill tile captured during the stats script, rewritten after the script runs.
    private Widget currentStatWidget;

    @Provides
    DoubleMaxConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DoubleMaxConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        overlayManager.add(levelUpOverlay);
        lastDoubleMaxLevel.clear();
        ready = false;

        clientThread.invokeLater(() ->
        {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                seedLevels();
            }
            refreshSkillTab();
        });
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        overlayManager.remove(levelUpOverlay);
        lastDoubleMaxLevel.clear();
        currentStatWidget = null;
        ready = false;

        // Restore the real numbers on the Skills tab.
        clientThread.invokeLater(this::refreshSkillTab);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(() ->
            {
                seedLevels();
                refreshSkillTab();
            });
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.HOPPING)
        {
            ready = false;
            lastDoubleMaxLevel.clear();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        final Skill skill = event.getSkill();
        final int newLevel = doubleMaxLevel(event.getXp());
        final Integer prev = lastDoubleMaxLevel.put(skill, newLevel);

        if (ready && prev != null && newLevel > prev)
        {
            onDoubleMaxLevelUp(skill, newLevel);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!DoubleMaxConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        // The offset / virtual toggles change every computed level, so re-seed and redraw.
        clientThread.invokeLater(() ->
        {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                seedLevels();
            }
            refreshSkillTab();
        });
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!config.overrideSkillTab())
        {
            return;
        }

        final int[] intStack = client.getIntStack();
        final int intStackSize = client.getIntStackSize();

        switch (event.getEventName())
        {
            case "skillTabBaseLevel":
            {
                // int stack: [ ..., skillId, levelToDisplay ]
                // size-2 holds the skill being drawn; size-1 is the level the tab will render.
                final int skillId = intStack[intStackSize - 2];
                final Skill skill = Skill.values()[skillId];
                intStack[intStackSize - 1] = doubleMaxLevel(client.getSkillExperience(skill));
                break;
            }
            case "skillTabMaxLevel":
            {
                // Raise the displayed level cap so Double Max levels above 99 can show.
                if (config.virtualLevels())
                {
                    intStack[intStackSize - 1] = Experience.MAX_VIRT_LEVEL;
                }
                break;
            }
            case "skillTabTotalLevel":
            {
                // The displayed total is a STRING on the object stack, not an int.
                final Object[] objectStack = client.getObjectStack();
                final int objectStackSize = client.getObjectStackSize();
                objectStack[objectStackSize - 1] = "Total level: " + doubleMaxTotalLevel();
                break;
            }
        }
    }

    // --- skill-tab top-number + tooltip rewrite ---
    // skillTabBaseLevel (above) only rewrites the BOTTOM (base) number. The TOP number is the boosted/current
    // level, and the hover tooltip's XP is read straight from the real XP -- neither has a script callback hook.
    // So we rewrite those directly: copy the corrected bottom text onto the top text after the tab script runs,
    // and rewrite the tooltip's value string before the tooltip script runs.

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (!config.overrideSkillTab())
        {
            return;
        }

        final int id = event.getScriptId();
        if (id == SCRIPTID_STATS_INIT || id == SCRIPTID_STATS_REFRESH)
        {
            // Capture the skill tile this script is drawing; we rewrite it once values are set (post-fire).
            currentStatWidget = event.getScriptEvent().getSource();
        }

        // The tooltip-builder script's id varies between client revisions, so instead of hard-coding it we
        // detect the skill tooltip by its argument signature on the object stack and rewrite the value string.
        rewriteSkillTooltipIfPresent();
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (!config.overrideSkillTab() || currentStatWidget == null)
        {
            return;
        }

        final int id = event.getScriptId();
        if (id == SCRIPTID_STATS_INIT || id == SCRIPTID_STATS_REFRESH)
        {
            mirrorDoubleMaxOntoTopNumber(currentStatWidget);
            currentStatWidget = null;
        }
    }

    private void mirrorDoubleMaxOntoTopNumber(Widget skillTile)
    {
        final Widget[] children = skillTile.getDynamicChildren();
        if (children == null)
        {
            return;
        }

        Widget firstText = null;
        Widget secondText = null;
        for (Widget child : children)
        {
            if (child == null || child.getType() != WidgetType.TEXT)
            {
                continue;
            }
            if (firstText == null)
            {
                firstText = child;
            }
            else
            {
                secondText = child;
                break;
            }
        }

        if (firstText == null || secondText == null)
        {
            return;
        }

        // Smaller Y is the top (boosted) number; larger Y is the bottom (base) number we already rewrote.
        final Widget top = firstText.getOriginalY() <= secondText.getOriginalY() ? firstText : secondText;
        final Widget bottom = top == firstText ? secondText : firstText;

        final String doubleMaxText = bottom.getText();
        if (doubleMaxText != null && !doubleMaxText.isEmpty())
        {
            top.setText(doubleMaxText);
        }
    }

    /**
     * Rewrites the skill-tab hover tooltip so it reads as if a full max of XP were removed.
     * The tooltip is built as two pipe-delimited strings on the object stack: labels (size-2) and values (size-1).
     * We leave the labels alone (their row layout already follows the Double Max level) and rewrite the values:
     * the skill XP line becomes the reduced XP, and "Next level at" / "Remaining XP" are recomputed from it.
     * The tooltip script id changes between client revisions, so we detect it by this argument signature
     * rather than a hard-coded id.
     */
    private void rewriteSkillTooltipIfPresent()
    {
        final Object[] objectStack = client.getObjectStack();
        final int size = client.getObjectStackSize();
        if (size < 2)
        {
            return;
        }
        if (!(objectStack[size - 2] instanceof String) || !(objectStack[size - 1] instanceof String))
        {
            return;
        }

        final String labels = (String) objectStack[size - 2];
        final String values = (String) objectStack[size - 1];

        final int xpLabelIdx = labels.indexOf(" XP:");
        if (xpLabelIdx <= 0)
        {
            return; // not the standard skill tooltip (e.g. a members-locked skill)
        }

        final Skill skill = skillByDisplayName(labels.substring(0, xpLabelIdx).replaceAll("<[^>]*>", "").trim());
        if (skill == null)
        {
            return;
        }

        final String[] labelParts = labels.split("\\|", -1);
        final String[] valueParts = values.split("\\|", -1);
        if (labelParts.length != valueParts.length || labelParts.length == 0)
        {
            return;
        }

        final int realXp = client.getSkillExperience(skill);
        final int doubleMaxXp = Math.max(0, realXp - offsetXp());
        final int dmLevel = doubleMaxLevel(realXp);
        final boolean hasNextLevel = dmLevel < Experience.MAX_VIRT_LEVEL;
        final int nextLevelXp = hasNextLevel ? Experience.getXpForLevel(dmLevel + 1) : 0;

        for (int i = 0; i < labelParts.length; i++)
        {
            final String label = labelParts[i];
            if (i == 0)
            {
                valueParts[i] = formatXp(doubleMaxXp);
            }
            else if (label.contains("Next level at") && hasNextLevel)
            {
                valueParts[i] = formatXp(nextLevelXp);
            }
            else if (label.contains("Remaining XP") && hasNextLevel)
            {
                valueParts[i] = formatXp(Math.max(0, nextLevelXp - doubleMaxXp));
            }
        }

        objectStack[size - 1] = String.join("|", valueParts);
    }

    private static String formatXp(int xp)
    {
        return String.format(Locale.US, "%,d", xp);
    }

    private static Skill skillByDisplayName(String name)
    {
        if (name.isEmpty())
        {
            return null;
        }
        for (Skill skill : Skill.values())
        {
            if (skillName(skill).equalsIgnoreCase(name))
            {
                return skill;
            }
        }
        return null;
    }

    // --- core maths ---

    /** XP value that gets subtracted from every skill. */
    int offsetXp()
    {
        final int level = Math.max(1, Math.min(Experience.MAX_VIRT_LEVEL, config.offsetLevel()));
        return Experience.getXpForLevel(level);
    }

    /** Double Max level for a raw total-XP value. */
    int doubleMaxLevel(int realXp)
    {
        final int fakeXp = Math.max(0, realXp - offsetXp());
        int level = Experience.getLevelForXp(fakeXp);
        if (!config.virtualLevels())
        {
            level = Math.min(level, Experience.MAX_REAL_LEVEL);
        }
        return level;
    }

    int doubleMaxLevel(Skill skill)
    {
        return doubleMaxLevel(client.getSkillExperience(skill));
    }

    int doubleMaxTotalLevel()
    {
        int total = 0;
        for (Skill skill : Skill.values())
        {
            if (isOverall(skill))
            {
                continue;
            }
            total += doubleMaxLevel(skill);
        }
        return total;
    }

    // --- internals ---

    private void seedLevels()
    {
        for (Skill skill : Skill.values())
        {
            if (isOverall(skill))
            {
                continue;
            }
            lastDoubleMaxLevel.put(skill, doubleMaxLevel(skill));
        }
        ready = true;
    }

    private void refreshSkillTab()
    {
        // Forces the client to rebuild the Skills tab so overrides apply (or are removed on shutdown).
        for (Skill skill : Skill.values())
        {
            if (isOverall(skill))
            {
                continue;
            }
            client.queueChangedSkill(skill);
        }
    }

    private void onDoubleMaxLevelUp(Skill skill, int level)
    {
        if (config.levelUpPopup())
        {
            levelUpOverlay.show(skill, level, config.popupSeconds());
        }
        if (config.levelUpChat())
        {
            final String message = "Double Max level up! Your " + skillName(skill)
                + " level is now " + level + ".";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
        }
    }

    // Skill.OVERALL was removed from the enum in newer clients; guard defensively across versions.
    private static boolean isOverall(Skill skill)
    {
        return "OVERALL".equals(skill.name());
    }

    static String skillName(Skill skill)
    {
        final String n = skill.name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
