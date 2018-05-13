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
package com.larryTheCoder.command.island;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.command.SubCommand;

/**
 * @author Adam Matthew
 */
public class DeleteSubCommand extends SubCommand {

    public DeleteSubCommand(ASkyBlock plugin) {
        super(plugin);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("is.command.reset") && sender.isPlayer();
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public String getDescription() {
        return "Delete your where you standing at";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"reset", "del"};
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player p = getPlugin().getServer().getPlayer(sender.getName());
        if (p.getLevel().getName().equals("SkyBlock")) {
            getPlugin().getPanel().addDeleteFormOverlay(p);
        } else {
            p.sendMessage("\u00A7cThis command works only in SkyBlock world");
        }
        return true;
    }
}
