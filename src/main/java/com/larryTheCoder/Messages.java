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
package com.larryTheCoder;

import cn.nukkit.Player;
import cn.nukkit.utils.Config;
import com.larryTheCoder.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Handles offline messaging to players and teams
 * <p>
 * Author: tastybento
 * <p>
 * ReAuthor: Adam Matthew
 */
public class Messages {

    private final HashMap<String, List<String>> messages = new HashMap<>();
    private ASkyBlock plugin;
    private Config messageStore;

    /**
     * @param plugin
     */
    public Messages(ASkyBlock plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns what messages are waiting for the player or null if none
     *
     * @param p
     * @return List of messages
     */
    public List<String> getMessages(String p) {
        List<String> playerMessages = messages.get(p);
        return playerMessages;
    }

    /**
     * Clears any messages for player
     *
     * @param p
     */
    public void clearMessages(String p) {
        messages.remove(p);
    }

    public void saveMessages() {
    }

    public boolean loadMessages() {
        return true;
    }

    /**
     * Provides the messages for the player
     *
     * @param p
     * @return List of messages
     */
    public List<String> get(String p) {
        return messages.get(p);
    }

    /**
     * Stores a message for player
     *
     * @param p
     * @param playerMessages
     */
    public void put(String p, List<String> playerMessages) {
        messages.put(p, playerMessages);

    }

    /**
     * Sends a message to every player in the team that is offline
     *
     * @param team
     * @param message
     */
    public void tellOfflineTeam(String team, String message) {
        if (!plugin.getTManager().inTeam(team)) {
            return;
        }
        String teamLeader = plugin.getTManager().getLeader(team);
        List<String> teamMembers = plugin.getTManager().getPlayerMembers(teamLeader);
        for (String member : teamMembers) {
            if (plugin.getServer().getPlayer(team) == null) {
                setMessage(member, message);
            }
        }
    }

    /**
     * Tells all online team members something happened
     *
     * @param p
     * @param message
     */
    public void tellTeam(String p, String message) {
        if (!plugin.getTManager().inTeam(p)) {
            return;
        }
        String teamLeader = plugin.getTManager().getLeader(p);
        List<String> teamMembers = plugin.getTManager().getPlayerMembers(teamLeader);
        for (String member : teamMembers) {
            if (!member.equals(p) && plugin.getServer().getPlayer(member) != null) {
                plugin.getServer().getPlayer(member).sendMessage(message);
            }
        }
    }

    /**
     * Sets a message for the player to receive next time they login
     *
     * @param p
     * @param message
     * @return true if player is offline, false if online
     */
    public boolean setMessage(String p, String message) {
        Player player = plugin.getServer().getPlayer(p);
        if (player != null) {
            if (player.isOnline()) {
                player.sendMessage(message);
                return false;
            }
        }
        List<String> playerMessages = get(p);
        if (playerMessages != null) {
            playerMessages.add(message);
        } else {
            playerMessages = new ArrayList<>(Arrays.asList(message));
        }
        put(p, playerMessages);
        return true;
    }
}
