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
package com.larryTheCoder.schematic;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.biome.Biome;
import cn.nukkit.math.Vector3;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.utils.Utils;
import org.jnbt.*;

import java.util.*;

import static com.larryTheCoder.utils.Utils.loadChunkAt;

/**
 * The package will rules every object in Schematic without this, the schematic
 * is useless
 *
 * @author Adam Matthew
 */
public class IslandBlock extends BlockMinecraftId {

    private final int x;
    private final int y;
    private final int z;
    // Current island id
    private final int islandId;
    // Chest contents
    private final HashMap<Integer, Item> chestContents;
    private short typeId;
    private int data;
    private List<String> signText;

    /**
     * @param x
     * @param y
     * @param z
     */
    public IslandBlock(int x, int y, int z, int islandId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.islandId = islandId;
        signText = null;
        chestContents = new HashMap<>();
    }

    /**
     * @return the type
     */
    public int getTypeId() {
        return typeId;
    }

    /**
     * @param type the type to set
     */
    public void setTypeId(short type) {
        this.typeId = type;
    }

    /**
     * @return the data
     */
    public int getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(byte data) {
        this.data = data;
    }

    /**
     * @return the signText
     */
    public List<String> getSignText() {
        return signText;
    }

    /**
     * @param signText the signText to set
     */
    public void setSignText(List<String> signText) {
    }

    /**
     * @param s
     * @param b
     */
    public void setBlock(int s, byte b) {
        this.typeId = (short) s;
        this.data = b;
    }

    public void setFlowerPot(Map<String, Tag> tileData) {
    }

    /**
     * Sets this block's sign data
     *
     * @param tileData
     */
    public void setSign(Map<String, Tag> tileData) {
    }

    public void setChest(Map<String, Tag> tileData) {
        try {
            ListTag chestItems = (ListTag) tileData.get("Items");
            if (chestItems != null) {
                //int number = 0;
                chestItems.getValue().stream().filter((item) -> (item instanceof CompoundTag)).forEach((item) -> {
                    try {
                        // Id is a number
                        short itemType = (short) ((CompoundTag) item).getValue().get("id").getValue();
                        short itemDamage = (short) ((CompoundTag) item).getValue().get("Damage").getValue();
                        byte itemAmount = (byte) ((CompoundTag) item).getValue().get("Count").getValue();
                        Item itemConfirm = Item.get(itemType, (int) itemDamage, itemAmount);
                        byte itemSlot = (byte) ((CompoundTag) item).getValue().get("Slot").getValue();
                        if (itemConfirm.getId() != 0 && !itemConfirm.getName().equalsIgnoreCase("Unknown")) {
                            chestContents.put((int) itemSlot, itemConfirm);
                        }
                    } catch (ClassCastException ex) {
                        // Id is a material
                        String itemType = (String) ((CompoundTag) item).getValue().get("id").getValue();
                        try {
                            // Get the material
                            if (itemType.startsWith("minecraft:")) {
                                String material = itemType.substring(10).toUpperCase();
                                // Special case for non-standard material names
                                int itemMaterial;

                                //Bukkit.getLogger().info("DEBUG: " + material);
                                if (WETOME.containsKey(material)) {
                                    itemMaterial = WETOME.get(material);
                                } else {
                                    itemMaterial = Item.fromString(material).getId();
                                }
                                byte itemAmount = (byte) ((CompoundTag) item).getValue().get("Count").getValue();
                                short itemDamage = (short) ((CompoundTag) item).getValue().get("Damage").getValue();
                                byte itemSlot = (byte) ((CompoundTag) item).getValue().get("Slot").getValue();
                                Item itemConfirm = Item.get(itemMaterial, (int) itemDamage, itemAmount);
                                if (itemConfirm.getId() != 0 && !itemConfirm.getName().equalsIgnoreCase("Unknown")) {
                                    chestContents.put((int) itemSlot, itemConfirm);
                                }
                            }
                        } catch (Exception exx) {
                            Utils.send("Could not parse item [" + itemType.substring(10).toUpperCase() + "] in schematic");
                            exx.printStackTrace();
                        }
                    }
                });
            }
        } catch (Exception e) {
            Utils.send("Could not parse schematic file item, skipping!");
            if (ASkyBlock.get().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Paste this block at blockLoc
     *
     * @param p
     * @param usePhysics
     * @param blockLoc
     */
    public void paste(Position blockLoc, boolean usePhysics, Biome biome) {
        Location loc = new Location(x, y, z, 0, 0, blockLoc.getLevel()).add(blockLoc);
        loadChunkAt(loc);
        blockLoc.getLevel().setBlock(loc, Block.get(typeId, data), true, true);
        blockLoc.getLevel().setBiomeId(loc.getFloorX(), loc.getFloorZ(), (byte) biome.getId());

        while (!blockLoc.getLevel().getChunk((int) blockLoc.getX() >> 4, (int) blockLoc.getZ() >> 4).isLoaded()) {
            loadChunkAt(loc);
        }

    }

    /**
     * This is the function where the Minecraft PC block bugs (Ex. vine)
     * Were placed and crapping the server
     * <p>
     * Revert function is multi-purposes cause
     */
    void revert(Position blockLoc) {
        try {
            Location loc = new Location(x, y, z, 0, 0, blockLoc.getLevel()).add(blockLoc);
            loadChunkAt(loc);
            blockLoc.getLevel().setBlock(loc, Block.get(Block.AIR), true, true);

            // Remove block entity
            BlockEntity entity = blockLoc.getLevel().getBlockEntity(loc);
            if (entity != null) {
                blockLoc.getLevel().removeBlockEntity(entity);
            }
        } catch (Exception ex) {
            // Nope do noting. This just avoiding a crap message on console
        }
    }

    /**
     * @return Vector for where this block is in the schematic
     */
    public Vector3 getVector() {
        return new Vector3(x, y, z);
    }

}
