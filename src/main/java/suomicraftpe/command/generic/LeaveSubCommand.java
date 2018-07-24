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
package suomicraftpe.command.generic;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import suomicraftpe.ASkyBlock;
import suomicraftpe.command.SubCommand;
import suomicraftpe.storage.WorldSettings;
import suomicraftpe.utils.Utils;

/**
 * @author Adam Matthew
 */
public class LeaveSubCommand extends SubCommand {

    public LeaveSubCommand(ASkyBlock plugin) {
        super(plugin);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("is.command.leave") && sender.isPlayer();
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String getDescription() {
        return "Return to world's spawn";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"lobby", "exit"};
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player pt = getPlugin().getServer().getPlayer(sender.getName());
        for (WorldSettings levelSetting : getPlugin().level) {
            String level = levelSetting.getLevel().getName();
            if (!pt.getLevel().getName().equalsIgnoreCase(level)) {
                return true;
            }
        }
        // Check if sender is in gamemode 1
        if (!pt.isOp()) {
            if (pt.getGamemode() != 0) {
                pt.setGamemode(0);
            }
        }
        getPlugin().getInventory().loadPlayerInventory(pt);
        // default spawn world
        if (getPlugin().getDatabase().getSpawn() != null) {
            pt.teleport(getPlugin().getDatabase().getSpawn().getCenter());
        } else {
            pt.teleport(getPlugin().getServer().getDefaultLevel().getSafeSpawn());
        }
        return true;
    }

}
