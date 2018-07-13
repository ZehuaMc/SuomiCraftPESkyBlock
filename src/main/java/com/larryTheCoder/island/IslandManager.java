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
 */
package com.larryTheCoder.island;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.generator.biome.Biome;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.TextFormat;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.events.IslandCreateEvent;
import com.larryTheCoder.player.PlayerData;
import com.larryTheCoder.storage.IslandData;
import com.larryTheCoder.task.DeleteIslandTask;
import com.larryTheCoder.task.TaskManager;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Adam Matthew
 */
public class IslandManager {

    private final ASkyBlock plugin;

    public IslandManager(ASkyBlock plugin) {
        this.plugin = plugin;
    }

    public void handleIslandCommand(Player p, boolean reset) {
        if (!reset) {
            boolean message = false;
            if (!checkIsland(p)) {
                plugin.getPanel().addIslandFormOverlay(p);
                return;
            }
            IslandData pd = ASkyBlock.get().getDatabase().getIsland(p.getName(), 1);
            if (pd == null || pd.getOwner() == null) {
                if (!message) {
                    p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorFailedCritical);
                }
                return;
            }
            p.sendMessage(plugin.getLocale(p).hangInThere);
            plugin.getGrid().homeTeleport(p, 1, false);
        } else {
            createIsland(p);
        }
    }

    public void kickPlayerByName(final Player pOwner, final String victimName) {
        final Location loc = pOwner.getLocation();
        final IslandData pd = GetIslandAt(loc);
        if (pd == null || pd.getOwner() == null || !pd.getOwner().equals(pOwner.getName())) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorNotOnIsland);
            return;
        }
        final int orgKey = generateIslandKey(loc);
        final Player pVictim = Server.getInstance().getPlayer(victimName);
        if (pVictim == null || !pVictim.isOnline()) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorOfflinePlayer);
            return;
        }
        if (!(pOwner.isOp())) {
            if (pVictim.isOp()) {
                pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorAdminOnly);
                return;
            }
        }
        if (victimName.equalsIgnoreCase(pOwner.getName())) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorKickOwner);
            return;
        }
        final Location locVict = pVictim.getLocation();
        final int tgtKey = generateIslandKey(locVict);
        if (tgtKey != orgKey) {
            pOwner.sendMessage(plugin.getPrefix() + plugin.getLocale(pOwner).errorOfflinePlayer);
            return;
        }
        Utils.send("&cAn island owner, " + pOwner.getName() + " attempt to "
            + "execute kick command to " + pVictim.getName() + " At "
            + Utils.locationShorted(locVict));
        pOwner.sendMessage(plugin.getPrefix() + TextFormat.GREEN + "Success! You send " + TextFormat.YELLOW + victimName + TextFormat.GREEN + " to spawn!");
        pVictim.sendMessage(plugin.getPrefix() + plugin.getLocale(pVictim).kickedFromOwner.replace("[name]", pOwner.getName()));
        if (plugin.getDatabase().getSpawn() != null) {
            pVictim.teleport(plugin.getDatabase().getSpawn().getCenter());
        } else {
            pVictim.teleport(plugin.getServer().getDefaultLevel().getSafeSpawn());
        }
    }

    public void kickPlayerByAdmin(CommandSender sender, String arg) {
        Player p = Server.getInstance().getPlayer(arg);
        Player kicker = sender.isPlayer() ? Server.getInstance().getPlayer(sender.getName()) : null;
        if (p == null) {
            sender.sendMessage(plugin.getPrefix() + plugin.getLocale(kicker).errorOfflinePlayer.replace("[player]", arg));
            return;
        }
        Location locVict = p.getLocation();
        for (String lvl : plugin.getLevels()) {
            if (!locVict.getLevel().getName().equalsIgnoreCase(lvl)) {
                sender.sendMessage(plugin.getPrefix() + plugin.getLocale(kicker).errorOfflinePlayer.replace("[player]", arg));
                return;
            }
        }
        sender.sendMessage(plugin.getPrefix() + plugin.getLocale(kicker).kickSuccess.replace("[player]", arg));
        p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).kickedFromAdmin);
        if (plugin.getDatabase().getSpawn() != null) {
            p.teleport(plugin.getDatabase().getSpawn().getCenter());
        } else {
            p.teleport(plugin.getServer().getDefaultLevel().getSafeSpawn());
        }
    }

    public boolean checkIsland(Player p) {
        return checkIsland(p, 1);
    }

    public boolean checkIsland(Player p, int homes) {
        return plugin.getDatabase().getIsland(p.getName(), homes) != null;
    }

    public boolean createIsland(Player p) {
        return this.createIsland(p, 1, "My Island");
    }

    public boolean createIsland(Player p, int templateId, String home) {
        return this.createIsland(p, templateId, home, plugin.getDefaultWorld(), false, Biome.getBiome(Biome.PLAINS));
    }

    public boolean createIsland(Player p, int templateId, String levelName, String home, boolean locked, Biome biome) {
        if (Settings.useEconomy) {
            double money = ASkyBlock.econ.getMoney(p);
            if (Settings.islandCost < money || Settings.islandCost == money) {
            } else {
                if (plugin.getIslandInfo(p) == null && Settings.firstIslandFree) {
                    p.sendMessage(plugin.getLocale(p).firstIslandFree);
                    p.sendMessage(plugin.getLocale(p).nextIslandPrice.replace("[price]", Double.toString(Settings.islandCost)));
                } else {
                    p.sendMessage(plugin.getLocale(p).errorNotEnoughMoney.replace("[price]", Double.toString(Settings.islandCost)));
                    return true;
                }
            }
        }

        for (int i = 0; i < Integer.MAX_VALUE; ++i) {
            int width = i * Settings.islandDistance * 2;
            int wx = (int) (Math.random() * width);
            int wz = (int) (Math.random() * width);
            int wy = Settings.islandHieght;
            wx = wx - wx % Settings.islandDistance + Settings.islandDistance / 2;
            wz = wz - wz % Settings.islandDistance + Settings.islandDistance / 2;
            IslandData pd = plugin.getDatabase().getIslandById(generateIslandKey(wx, wz));
            if (pd == null) {
                Level world = Server.getInstance().getLevelByName(levelName);
                Location locIsland = new Location(wx, wy, wz, world);
                pd = claim(p, locIsland, home, locked);
                IslandCreateEvent event = new IslandCreateEvent(p, templateId, pd);
                plugin.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    p.sendMessage(plugin.getLocale(p).errorBlockedByAPI);
                    return true;
                }

                ASkyBlock.schematics.pasteSchematic(p, locIsland, templateId, biome);
                boolean result = plugin.getDatabase().createIsland(pd);
                if (result) {
                    p.sendMessage(plugin.getLocale(p).createSuccess);
                    plugin.getGrid().homeTeleport(p, 1, true);
                    return true;
                } else {
                    p.sendMessage(plugin.getLocale(p).errorFailedCritical);
                    return false;
                }
            }
        }
        return false;
    }

    private IslandData claim(Player p, Location loc, String home, boolean locked) {
        int x = loc.getFloorX();
        int z = loc.getFloorZ();
        if (!checkIslandAt(loc.getLevel())) {
            return null;
        }

        int iKey = generateIslandKey(loc);
        List<IslandData> number = plugin.getDatabase().getIslands(p.getName());
        IslandData pd = plugin.getDatabase().getIslandLocation(loc.getLevel().getName(), x, z);
        pd.setId(number.size() + 1);
        pd.setIslandId(iKey);
        pd.setOwner(p.getName());
        pd.setCenter(x, loc.getFloorY(), z);
        pd.setLevelName(loc.getLevel().getName());
        pd.setLocked(locked);
        pd.setBiome(Settings.defaultBiome.getName());
        pd.setName(home);
        return pd;
    }

    public int generateIslandKey(Location loc) {
        int x = loc.getFloorX();
        int z = loc.getFloorZ();
        return generateIslandKey(x, z);
    }

    public int generateIslandKey(int x, int z) {
        return x / Settings.islandDistance + z / Settings.islandDistance * Integer.MAX_VALUE;
    }

    public boolean isPlayerIsland(Player p, Location loc) {
        if (!plugin.level.contains(loc.getLevel().getName())) {
            return false;
        }

        return plugin.getIslandInfo(loc).getOwner().equalsIgnoreCase(p.getName());
    }

    public void deleteIsland(Player p, IslandData pd) {
        if (pd == null || pd.getOwner() == null) {
            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorNoIsland);
            return;
        }
        if (!Utils.canBypassTimer(p, p.getName() + pd.getIslandId(), Settings.resetTime)) {
            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorTooSoon.replace("[secs]", Utils.getPlayerRTime(p, p.getName() + pd.getIslandId(), 0)).replace("[cmds]", "delete"));
            return;
        }

        if (p.getLevel().getName().equals("SkyBlock")) {
            p.getInventory().clearAll();
            p.setHealth(20);
            p.getFoodData().setFoodLevel(20);
        }

        TaskManager.runTask(new DeleteIslandTask(plugin, pd, p));

        p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).resetSuccess.replace("[mili]", "" + Settings.resetTime));
    }

    public boolean checkIslandAt(Level level) {
        return plugin.getLevels().contains(level.getName());
    }

    public boolean CanPlayerAccess(Player p, Location loc) {
        String pName = p.getName();
        if (p.isOp()) {
            return true;
        }
        if (!checkIslandAt(loc.getLevel())) {
            return true;
        }
        IslandData pd = GetIslandAt(loc);
        if (pd == null) {
            return false;
        }
        if (pd.getOwner() == null) {
            return false;
        }
        if (pd.getOwner().equals(pName)) {
            return true;
        }
        PlayerData pd2 = plugin.getPlayerInfo(p);
        return pd2.members.contains(pName);
    }

    public IslandData GetIslandAt(Location loc) {
        if (!checkIslandAt(loc.getLevel())) {
            return null;
        }
        int iKey = generateIslandKey(loc);
        IslandData res = ASkyBlock.get().getDatabase().getIslandById(iKey);
        if (res == null) {
            return null;
        }
        if (res.getOwner() == null) {
            return null;
        }
        return res;
    }

    public void islandInfo(Player p, Location loc) {
        if (!checkIslandAt(loc.getLevel())) {
            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorWrongWorld);
            return;
        }
        final IslandData pd = GetIslandAt(loc);
        PlayerData pd2 = plugin.getPlayerInfo(p);
        if (pd == null) {
            p.sendMessage(TextFormat.LIGHT_PURPLE + plugin.getLocale(p).errorNotOnIsland);
            return;
        }
        p.sendMessage(TextFormat.LIGHT_PURPLE + "- Island Owner: " + TextFormat.YELLOW + pd.getOwner());
        String strMembers = Utils.arrayToString(pd2.members);
        if (pd2.members.size() <= 0) {
            strMembers = "none";
        }
        p.sendMessage(TextFormat.LIGHT_PURPLE + "- Members: " + TextFormat.AQUA + strMembers);

        p.sendMessage(TextFormat.LIGHT_PURPLE + "- Flags: " + TextFormat.GOLD + "Allow Teleport: " + pd.isLocked());
    }

    public void teleportPlayer(Player p, String arg) {
        IslandData pd = ASkyBlock.get().getDatabase().getIsland(arg, 1);
        if (pd.getOwner() != null) {
            p.sendMessage(plugin.getPrefix() + plugin.getLocale(p).errorOfflinePlayer.replace("[player]", arg));
            return;
        }
        Location home = plugin.getGrid().getSafeHomeLocation(pd.getOwner(), pd.getId());
        if (home == null) {
            p.sendMessage(plugin.getPrefix() + TextFormat.RED + "Failed to find your island safe spawn");
            return;
        }
        plugin.getTeleportLogic().safeTeleport(p, home, false, pd.getId());
    }

    public boolean locationIsOnIsland(Player player, Vector3 loc) {
        if (player == null) {
            return false;
        }
        Location local = new Location(loc.x, loc.y, loc.z, player.getLevel());
        IslandData island = GetIslandAt(local);
        if (island != null) {
            return island.onIsland(local) || island.getMembers().contains(player.getName());
        } else {
        }
        Set<Location> islandTestLocations = new HashSet<>();
        if (checkIsland(player)) {
            islandTestLocations.add(plugin.getIslandInfo(player).getHome());
        } else if (plugin.getTManager().hasTeam(player)) {
            islandTestLocations.add(plugin.getPlayerInfo(player).getTeamIslandLocation());
        }
        return islandTestLocations.stream().filter((islandTestLocation) -> (local.getLevel().getName().equalsIgnoreCase(islandTestLocation.level.getName())))
            .anyMatch((islandTestLocation) -> (loc.getX() >= islandTestLocation.getX() - Settings.protectionrange / 2
                && loc.getX() < islandTestLocation.getX() + Settings.protectionrange / 2
                && loc.getZ() >= islandTestLocation.getZ() - Settings.protectionrange / 2
                && loc.getZ() < islandTestLocation.getZ() + Settings.protectionrange / 2));
    }
}
