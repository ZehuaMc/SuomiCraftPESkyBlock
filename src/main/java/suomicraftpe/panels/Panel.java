/*
 * Copyright (C) 2017 Adam Matthew
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package suomicraftpe.panels;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.form.element.*;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.response.FormResponseData;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowModal;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.level.generator.biome.Biome;
import suomicraftpe.ASkyBlock;
import suomicraftpe.locales.ASlocales;
import suomicraftpe.schematic.SchematicHandler;
import suomicraftpe.storage.IslandData;
import suomicraftpe.storage.WorldSettings;
import suomicraftpe.utils.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plugin Panel controller class
 * <p>
 * Used to interface the player easier than before
 */
public class Panel implements Listener {

    private final ASkyBlock plugin;

    // Confirmation panels
    private Map<Integer, PanelType> panelDataId = new HashMap<>();
    private Map<Player, Integer> mapIslandId = new HashMap<>();

    public Panel(ASkyBlock plugin) {
        this.plugin = plugin;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerRespondForm(PlayerFormRespondedEvent event) {
        if (event.getResponse() == null) return;
        Player p = event.getPlayer();
        int formId = event.getFormID();
        PanelType type = panelDataId.get(formId);
        if (type == null) return;

        switch (type) {
            // island features
            case TYPE_ISLAND:
                // Check if the player closed this form
                if (event.getWindow().wasClosed()) {
                    break;
                }

                if (!(event.getWindow() instanceof FormWindowCustom)) return;

                FormWindowCustom panelIsland = (FormWindowCustom) event.getWindow();
                // Get the response form from player
                FormResponseCustom response = panelIsland.getResponse();

                // The input respond
                int responseId = 1;
                String islandName = response.getInputResponse(responseId++);

                // 6 - 5
                // The island schematic ID respond
                int id = 1; // Keep this 1 so they wont be inside of my UN-FINISHED island
                if (!ASkyBlock.schematics.isUseDefaultGeneration()) {
                    FormResponseData form = response.getDropdownResponse(responseId++); // Dropdown respond

                    String schematicType = form.getElementContent();

                    id = ASkyBlock.schematics.getSchemaId(schematicType);
                }
                // Nope it just a Label
                responseId++;

                plugin.getIsland().createIsland(p, id, "SkyBlock", islandName, true, Biome.getBiome("PLAINS"));
                panelDataId.remove(formId);
                break;
            // Challenges data
            case TYPE_CHALLENGES:
                // Check if the player closed this form
                if (event.getWindow().wasClosed()) {
                    break;
                }

                if (!(event.getWindow() instanceof FormWindowSimple)) return;

                if (!p.getLevel().getName().equals("SkyBlock")) return;

                FormWindowSimple panelChallenges = (FormWindowSimple) event.getWindow();

                FormResponseSimple responses = panelChallenges.getResponse();

                String responseType = responses.getClickedButton().getText();
                plugin.getServer().dispatchCommand(p, "chall complete " + responseType);
                break;
            case TYPE_HOMES:
                // Check if the player closed this form
                if (event.getWindow().wasClosed()) {
                    break;
                }

                if (!(event.getWindow() instanceof FormWindowSimple)) return;

                FormWindowSimple homePanel = (FormWindowSimple) event.getWindow();

                FormResponseSimple homeResponse = homePanel.getResponse();

                int responseHome = homeResponse.getClickedButtonId();
                p.sendMessage(plugin.getLocale(p).hangInThere);
                plugin.getGrid().homeTeleport(p, responseHome, false);
                break;
            case FIRST_TIME_SETTING:
                // Check if the player closed this form
                if (event.getWindow().wasClosed()) {
                    p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).panelCancelled);
                    break;
                }

                if (!(event.getWindow() instanceof FormWindowSimple)) return;

                FormWindowSimple firstSettingPanel = (FormWindowSimple) event.getWindow();

                FormResponseSimple firstSettingResponse = firstSettingPanel.getResponse();

                int islandId = firstSettingResponse.getClickedButtonId();
                addSettingFormOverlay(p, plugin.getDatabase().getIsland(p.getName(), islandId));
                break;
            case SECOND_TIME_SETTING:
                // Check if the player closed this form
                if (event.getWindow().wasClosed()) {
                    break;
                }

                if (!(event.getWindow() instanceof FormWindowCustom)) return;

                FormWindowCustom secondTime = (FormWindowCustom) event.getWindow();
                // Get the response form from player
                FormResponseCustom settingResponse = secondTime.getResponse();

                int idea = 1;
                IslandData pd;
                try { // Maybe figure out why this causes random exceptions later
                    pd = plugin.getDatabase().getIsland(p.getName(), mapIslandId.get(p));
                } catch(Exception e) {
                    break;
                }

                boolean lock = settingResponse.getToggleResponse(idea++);
                String nameIsland = settingResponse.getInputResponse(idea++);
                if (pd.isLocked() != lock) {
                    pd.setLocked(lock);
                }
                if (!pd.getName().equalsIgnoreCase(nameIsland)) {
                    pd.setName(nameIsland);
                }
                break;
            case FIRST_TIME_DELETE:
                // Check if the player closed this form
                if (event.getWindow().wasClosed()) {
                    break;
                }

                if (!(event.getWindow() instanceof FormWindowSimple)) return;

                if (!p.getLevel().getName().equals("SkyBlock")) return;

                FormWindowSimple firstTimeDelta = (FormWindowSimple) event.getWindow();

                FormResponseSimple delete = firstTimeDelta.getResponse();

                int islandUID = delete.getClickedButtonId();
                addDeleteFormOverlay(p, plugin.getDatabase().getIsland(p.getName(), islandUID));
                break;
            case SECOND_TIME_DELETE:
                // Check if the player closed this form
                if (event.getWindow().wasClosed()) {
                    break;
                }

                if (!(event.getWindow() instanceof FormWindowModal)) return;

                if (!p.getLevel().getName().equals("SkyBlock")) return;

                FormWindowModal modalForm = (FormWindowModal) event.getWindow();

                int idButton = mapIslandId.get(p);

                int buttonId = modalForm.getResponse().getClickedButtonId();
                if (buttonId == 0) {
                    plugin.getIsland().deleteIsland(p, plugin.getDatabase().getIsland(p.getName(), idButton));
                } else {
                    p.sendMessage(plugin.getLocale(p).deleteIslandCancelled);
                }
        }
    }

    public void addChallengesFormOverlay(Player player) {
        FormWindowSimple panelIsland = new FormWindowSimple("Challenges Menu", getLocale(player).panelChallengesHeader);

        for (String toButton : plugin.getChallenges().getChallengeConfig().getSection("challenges.challengeList").getKeys(false)) {
            panelIsland.addButton(new ElementButton(toButton));
        }

        int id = player.showFormWindow(panelIsland);
        panelDataId.put(id, PanelType.TYPE_CHALLENGES);
    }

    public void addIslandFormOverlay(Player player) {
        // First check the availability for worlds
        ArrayList<String> worldName = new ArrayList<>();
        for (String level : plugin.getLevels()) {
            List<IslandData> maxPlotsOfPlayers = plugin.getDatabase().getIslands(player.getName(), level);
            if (!maxPlotsOfPlayers.isEmpty() || Settings.maxHome >= 0 && maxPlotsOfPlayers.size() >= Settings.maxHome) {
            } else {
                worldName.add(level);
            }
        }

        // Second. Check the player permission
        // Have no permission to create island at this location
        for (String level : worldName) {
            WorldSettings settings = plugin.getSettings(level);
            if (!player.hasPermission(settings.getPermission())) {
                worldName.remove(level);
            }
        }

        if (worldName.isEmpty()) {
            player.sendMessage(plugin.getPrefix() + plugin.getLocale(player).errorMaxIsland.replace("[maxplot]", "" + Settings.maxHome));
            return;
        }

        int homes = plugin.getDatabase().getIslands(player.getName()).size();
        FormWindowCustom panelIsland = new FormWindowCustom("Island Menu");

        panelIsland.addElement(new ElementLabel(getLocale(player).panelIslandHeader));
        panelIsland.addElement(new ElementInput(getLocale(player).panelIslandHome, "", "My " + (homes + 1) + " home"));
        panelIsland.addElement(new ElementDropdown(getLocale(player).panelIslandWorld, worldName));

        SchematicHandler bindTo = ASkyBlock.schematics;
        if (!bindTo.isUseDefaultGeneration()) {
            panelIsland.addElement(new ElementDropdown(getLocale(player).panelIslandTemplate, bindTo.getSchemaList(), bindTo.getDefaultIsland()));
        }

        panelIsland.addElement(new ElementLabel(getLocale(player).panelIslandDefault));

        int id = player.showFormWindow(panelIsland);
        panelDataId.put(id, PanelType.TYPE_ISLAND);
    }

    public void addHomeFormOverlay(Player p) {
        ArrayList<IslandData> listHome = plugin.getDatabase().getIslands(p.getName());

        FormWindowSimple islandHome = new FormWindowSimple("Home list", getLocale(p).panelHomeHeader.replace("[function]", "teleport"));
        for (IslandData pd : listHome) {
            islandHome.addButton(new ElementButton(pd.getName()));
        }
        int id = p.showFormWindow(islandHome);
        panelDataId.put(id, PanelType.TYPE_HOMES);
    }

    public void addDeleteFormOverlay(Player p) {
        this.addDeleteFormOverlay(p, null);
    }

    public void addDeleteFormOverlay(Player p, IslandData pd) {
        if (pd == null) {
            ArrayList<IslandData> listHome = plugin.getDatabase().getIslands(p.getName());
            // Automatically show default island setting
            if (listHome.size() == 1) {
                addDeleteFormOverlay(p, plugin.getDatabase().getIsland(p.getName(), 1));
                return;
            }

            FormWindowSimple islandHome = new FormWindowSimple("Choose your home", getLocale(p).panelHomeHeader.replace("[function]", "set your island settings."));
            for (IslandData pda : listHome) {
                islandHome.addButton(new ElementButton(pda.getName()));
            }

            int id = p.showFormWindow(islandHome);
            panelDataId.put(id, PanelType.FIRST_TIME_DELETE);
            return;
        }
        mapIslandId.put(p, pd.getId());

        FormWindowModal confirm = new FormWindowModal("Delete", getLocale(p).deleteIslandSure, "\u00A7cDelete my island", "Cancel");

        int id = p.showFormWindow(confirm);
        panelDataId.put(id, PanelType.SECOND_TIME_DELETE);
    }

    public void addSettingFormOverlay(Player p) {
        this.addSettingFormOverlay(p, null);
    }

    public void addSettingFormOverlay(Player p, IslandData pd) {
        // This is the island Form
        if (pd == null) {
            ArrayList<IslandData> listHome = plugin.getDatabase().getIslands(p.getName());
            // Automatically show default island setting
            if (listHome.size() == 1) {
                addSettingFormOverlay(p, plugin.getDatabase().getIsland(p.getName(), 1));
                return;
            }

            FormWindowSimple islandHome = new FormWindowSimple("Choose your home", getLocale(p).panelHomeHeader.replace("[function]", "set your island settings."));
            for (IslandData pda : listHome) {
                islandHome.addButton(new ElementButton(pda.getName()));
            }

            int id = p.showFormWindow(islandHome);
            panelDataId.put(id, PanelType.FIRST_TIME_SETTING);
            return;
        }

        FormWindowCustom settingForm = new FormWindowCustom("" + pd.getName() + "'s Settings");

        settingForm.addElement(new ElementLabel(getLocale(p).panelSettingHeader));
        settingForm.addElement(new ElementToggle("Protection", pd.isLocked()));
        settingForm.addElement(new ElementInput("Island Name", "", pd.getName()));
        mapIslandId.put(p, pd.getId());

        int id = p.showFormWindow(settingForm);
        panelDataId.put(id, PanelType.SECOND_TIME_SETTING);
        return;
    }

    public ASlocales getLocale(Player p) {
        return plugin.getLocale(p);
    }

    enum PanelType {
        TYPE_ISLAND,
        TYPE_CHALLENGES,
        TYPE_HOMES,
        FIRST_TIME_SETTING,
        SECOND_TIME_SETTING,
        FIRST_TIME_DELETE,
        SECOND_TIME_DELETE
    }
}
