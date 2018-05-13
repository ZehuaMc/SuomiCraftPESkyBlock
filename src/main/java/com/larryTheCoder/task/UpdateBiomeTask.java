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
package com.larryTheCoder.task;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.biome.Biome;
import cn.nukkit.scheduler.NukkitRunnable;
import cn.nukkit.scheduler.Task;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.storage.IslandData;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UpdateBiomeTask extends Task {

    private final IslandData pd;
    private final CommandSender player;
    private final ASkyBlock plugin;

    public UpdateBiomeTask(ASkyBlock plugin, IslandData pd, CommandSender player) {
        this.plugin = plugin;
        this.pd = pd;
        this.player = player;
    }

    @Override
    public void onRun(int currentTick) {
        Player p = player.isPlayer() ? (Player) player : null;
        Level level = plugin.getServer().getLevelByName(pd.getLevelName());

        int minX = pd.getMinProtectedX();
        int minZ = pd.getMinProtectedZ();
        int maxX = pd.getMinProtectedX() + pd.getProtectionSize();
        int maxZ = pd.getMinProtectedZ() + pd.getProtectionSize();

        final BaseFullChunk minChunk = level.getChunk(minX >> 4, minZ >> 4, true);
        final BaseFullChunk maxChunk = level.getChunk(maxX >> 4, maxZ >> 4, true);

        if (!minChunk.isGenerated() || !maxChunk.isGenerated()) {
            level.regenerateChunk(minChunk.getX(), minChunk.getZ());
            level.regenerateChunk(maxChunk.getX(), maxChunk.getZ());
        }

        List<BaseFullChunk> biomeToChanged = new ArrayList<>();

        for (int x = minChunk.getX(); x <= maxChunk.getX(); x++) {
            for (int z = minChunk.getZ(); z <= maxChunk.getZ(); z++) {
                Utils.loadChunkAt(new Position(x, 0, z, level));
                biomeToChanged.add(level.getChunk(x, z));
            }
        }

        if (!biomeToChanged.isEmpty()) {
            new NukkitRunnable() {

                @Override
                public void run() {
                    Iterator<BaseFullChunk> iChunk = biomeToChanged.iterator();
                    int count = 0;
                    while (iChunk.hasNext() && count++ < Settings.cleanrate) {
                        BaseFullChunk chunk = iChunk.next();
                        for (int y = Settings.seaLevel; y < 255 - Settings.seaLevel; y++) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    chunk.setBiomeId(x, z, Biome.getBiome(pd.getBiome()).getId());
                                }
                            }
                        }
                        level.generateChunkCallback(chunk.getX(), chunk.getZ(), chunk);
                        iChunk.remove();
                    }
                    if (biomeToChanged.isEmpty()) {
                        player.sendMessage(plugin.getPrefix() + plugin.getLocale(p).biomeChangeComplete.replace("[biome]", pd.getBiome()));
                        this.cancel();
                        return;
                    }
                }

            }.runTaskTimer(plugin, 0, 20);
        }
    }
}
