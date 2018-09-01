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
package suomicraftpe;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.biome.Biome;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

import java.util.Map;

/**
 * @author Adam Matthew
 */
public class SkyBlockGenerator extends Generator {

    public static final int TYPE_SKYBLOCK = 0x7eabbff;
    private final Map<String, Object> options;
    private ChunkManager level;
    private NukkitRandom random;

    public SkyBlockGenerator(Map<String, Object> options) {
        this.options = options;
    }

    @Override
    public int getId() {
        return TYPE_SKYBLOCK;
    }

    @Override
    public void init(ChunkManager cm, NukkitRandom nr) {
        this.level = cm;
        this.random = nr;
    }

    @Override
    public void generateChunk(int chunkX, int chunkZ) {
        BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBiomeId(x, z, Biome.PLAINS);
            }
        }
    }

    @Override
    public void populateChunk(int chunkX, int chunkZ) {
    }

    @Override
    public Map<String, Object> getSettings() {
        return options;
    }

    @Override
    public String getName() {
        return "island";
    }

    @Override
    public Vector3 getSpawn() {
        return new Vector3(15, 60, 15);
    }

    @Override
    public ChunkManager getChunkManager() {
        return level;
    }

    @Override
    public int getDimension() {
        return Level.DIMENSION_OVERWORLD;
    }
}
