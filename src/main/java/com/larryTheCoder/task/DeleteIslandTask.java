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
package com.larryTheCoder.task;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.NukkitRunnable;
import cn.nukkit.utils.MainLogger;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.storage.IslandData;
import com.larryTheCoder.utils.Pair;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Author: larryTheCoder
 * <p>
 * The best solution for reset provider in island removal,
 * Using chunk instead of using set-block
 */
public class DeleteIslandTask implements Runnable {

    public final MainLogger deb = Server.getInstance().getLogger();
    private final IslandData pd;
    private final CommandSender player;
    private final ASkyBlock plugin;

    public DeleteIslandTask(ASkyBlock plugin, IslandData pd, CommandSender player) {
        this.plugin = plugin;
        this.pd = pd;
        this.player = player;
    }

    @Override
    public void run() {
        Server.getInstance().dispatchCommand(player, "is leave");
        Level level = plugin.getServer().getLevelByName(pd.getLevelName());

        if (level == null) {
            Utils.send("ERROR: Cannot find the level " + pd.getLevelName());
            Utils.send("The sender who execute this: " + pd.getOwner());
            return;
        }

        boolean cleanUpBlocks = false;
        if (Settings.islandDistance - pd.getProtectionSize() < 16) {
            cleanUpBlocks = true;
        }

        int range = pd.getProtectionSize() / 2 * +1;
        int minX = pd.getMinProtectedX();
        int minZ = pd.getMinProtectedZ();
        int maxX = pd.getMinProtectedX() + pd.getProtectionSize();
        int maxZ = pd.getMinProtectedZ() + pd.getProtectionSize();

        int islandSpacing = Settings.islandDistance - pd.getProtectionSize();
        int minxX = (pd.getCenter().getFloorX() - range - islandSpacing);
        int minzZ = (pd.getCenter().getFloorZ() - range - islandSpacing);
        int maxxX = (pd.getCenter().getFloorX() + range + islandSpacing);
        int maxzZ = (pd.getCenter().getFloorZ() + range + islandSpacing);

        final BaseFullChunk minChunk = level.getChunk(minX >> 4, minZ >> 4, true);
        final BaseFullChunk maxChunk = level.getChunk(maxX >> 4, maxZ >> 4, true);

        if (!minChunk.isGenerated() || !maxChunk.isGenerated()) {
            level.regenerateChunk(minChunk.getX(), minChunk.getZ());
            level.regenerateChunk(maxChunk.getX(), maxChunk.getZ());
        }

        List<Pair> chunksToClear = new ArrayList<>();
        List<BaseFullChunk> chunksToRemoved = new ArrayList<>();

        for (int x = minChunk.getX(); x <= maxChunk.getX(); x++) {
            for (int z = minChunk.getZ(); z <= maxChunk.getZ(); z++) {
                boolean regen = true;

                if ((level.getChunk(x, z, true).getX() << 4) < minxX) {
                    deb.debug("DEBUG: min x coord is less than absolute min! " + minxX);
                    regen = false;
                }
                if ((level.getChunk(x, z, true).getZ() << 4) < minzZ) {
                    deb.debug("DEBUG: min z coord is less than absolute min! " + minzZ);
                    regen = false;
                }
                if ((level.getChunk(x, z, true).getX() << 4) > maxxX) {
                    deb.debug("DEBUG: max x coord is more than absolute max! " + maxxX);
                    regen = false;
                }
                if ((level.getChunk(x, z, true).getZ() << 4) > maxzZ) {
                    deb.debug("DEBUG: max z coord in chunk is more than absolute max! " + maxzZ);
                    regen = false;
                }
                deb.debug("" + (level.getChunk(x, z).getX() << 4));
                deb.debug("" + (level.getChunk(x, z).getZ() << 4));
                Utils.loadChunkAt(new Position(x, 0, z, level));

                if (regen) {
                    chunksToRemoved.add(level.getChunk(x, z));
                } else {
                    if (cleanUpBlocks) {
                        chunksToClear.add(new Pair(x, z));
                    }
                }
            }
        }

        if (!chunksToRemoved.isEmpty()) {
            new NukkitRunnable() {

                @Override
                public void run() {
                    Iterator<BaseFullChunk> iChunk = chunksToRemoved.iterator();
                    int count = 0;
                    while (iChunk.hasNext() && count++ < Settings.cleanrate) {
                        BaseFullChunk chunk = iChunk.next();
                        for (int y = Settings.seaLevel; y < 255 - Settings.seaLevel; y++) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    chunk.setBlockIdAt(x, y, z, 0);
                                    BlockEntity entity = chunk.getTile(x, y, z);
                                    if (entity != null) {
                                        chunk.removeBlockEntity(entity);
                                    }
                                }
                            }
                        }
                        level.generateChunkCallback(chunk.getX(), chunk.getZ(), chunk);
                        iChunk.remove();
                    }
                    if (chunksToRemoved.isEmpty()) {
                        this.cancel();
                        return;
                    }
                }

            }.runTaskTimer(plugin, 0, 20);
        }

        if (!chunksToClear.isEmpty()) {
            new NukkitRunnable() {
                @Override
                public void run() {
                    Iterator<Pair> it = chunksToClear.iterator();
                    int count = 0;
                    while (it.hasNext() && count++ < Settings.cleanrate) {
                        Pair pair = it.next();
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int xCoord = pair.getLeft() * 16 + x;
                                int zCoord = pair.getRight() * 16 + z;
                                if (pd.inIslandSpace(xCoord, zCoord)) {
                                    for (int y = 0; y < 255 - Settings.seaLevel; y++) {
                                        Vector3 vec = new Vector3(xCoord, y + Settings.seaLevel, zCoord);
                                        level.setBlock(vec, Block.get(Block.AIR), true, true);
                                    }
                                }
                            }
                        }

                        it.remove();
                    }
                    if (chunksToClear.isEmpty()) {
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 0, 20);
        }

        ASkyBlock.get().getDatabase().deleteIsland(pd);
    }

}
