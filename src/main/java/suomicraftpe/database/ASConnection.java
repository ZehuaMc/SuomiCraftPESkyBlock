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
package suomicraftpe.database;

import cn.nukkit.level.Position;
import suomicraftpe.ASkyBlock;
import suomicraftpe.database.variables.AbstractDatabase;
import suomicraftpe.database.variables.MySQLDatabase;
import suomicraftpe.player.PlayerData;
import suomicraftpe.storage.IslandData;
import suomicraftpe.task.TaskManager;
import suomicraftpe.utils.Settings;
import suomicraftpe.utils.Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Full sql database
 *
 * @author Adam Matthew
 */
public final class ASConnection {

    private final AbstractDatabase db;
    // Faster to search islands ~75%
    private final ArrayList<IslandData> islandCache = new ArrayList<>();
    private Connection con;
    private boolean closed = true;
    private IslandData islandSpawn;
    private ASkyBlock plugin;
    private boolean mySQL;

    public ASConnection(ASkyBlock plugin, AbstractDatabase database, boolean debug) throws SQLException, ClassNotFoundException, InterruptedException {
        // Performace upgrade: Cache
        this.plugin = plugin;
        this.db = database;
        this.con = database.openConnection();
        this.mySQL = database instanceof MySQLDatabase;
        this.createTables(true);
        // MySQL Support
        TaskManager.runTaskAsync(() -> {
            long last = System.currentTimeMillis();
            while (true) {
                if (closed) {
                    break;
                }
                if (mySQL && System.currentTimeMillis() - last > 550000 || !isValid()) {
                    last = System.currentTimeMillis();
                    reconnect();
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void createTables(boolean updateCheck) throws SQLException, ClassNotFoundException, InterruptedException {
        if (closed) {
            String[] tables = new String[]{"island", "worlds", "players"};
            DatabaseMetaData meta = this.con.getMetaData();
            int create = 0;
            for (String s : tables) {
                try (ResultSet set = meta.getTables(null, null, s, new String[]{"TABLE"})) {
                    if (!set.next()) {
                        create++;
                    }
                }
            }
            if (create == 0) {
                return;
            }
            // A lot of updates will comming
            try (Statement set = this.con.createStatement()) {
                //createdDate updatedDate votes
                set.addBatch("CREATE TABLE IF NOT EXISTS `island` (`id` INTEGER,"
                    + "`islandId` INTEGER NOT NULL,"
                    + "`x` INTEGER NOT NULL,"
                    + "`y` INTEGER NOT NULL,"
                    + "`z` INTEGER NOT NULL,"
                    + "`spawnX` INTEGER,"
                    + "`spawnY` INTEGER,"
                    + "`spawnZ` INTEGER,"
                    + "`isSpawn` BOOLEAN NOT NULL,"
                    + "`psize` INTEGER NOT NULL,"
                    + "`owner` VARCHAR NOT NULL,"
                    + "`name` VARCHAR NOT NULL,"
                    + "`world` VARCHAR NOT NULL,"
                    + "`protection` VARCHAR NOT NULL,"
                    + "`biome` VARCHAR NOT NULL,"
                    + "`locked` INTEGER NOT NULL)");
                //+ "`active` INTEGER NOT NULL)");
                set.addBatch("CREATE TABLE IF NOT EXISTS `worlds` (`world` VARCHAR)");
                set.addBatch("CREATE TABLE IF NOT EXISTS `players` (`player` VARCHAR NOT NULL,"
                    + "`homes` INTEGER NOT NULL,"
                    + "`resetleft` INTEGER NOT NULL,"
                    + "`banlist` VARCHAR,"
                    + "`teamleader` VARCHAR,"
                    + "`teamislandlocation` VARCHAR,"
                    + "`inteam` BOOLEAN,"
                    + "`islandlvl` INTEGER,"
                    + "`members` VARCHAR,"
                    + "`challengelist` VARCHAR,"
                    + "`challengelisttimes` VARCHAR,"
                    + "`name` VARCHAR,"
                    + "`locale` VARCHAR NOT NULL)");
                set.executeBatch();
                set.clearBatch();
            }
            if (updateCheck == false) {

            }
            closed = false;
        } else if (closed == true && db != null) {
            Utils.send("§cThis a problem. The SQLManager is closed but the Database is not...");
            Utils.send("§cYou might to stop server for this kind of problem to fix this error");
            Utils.send("&cError Code: 0x3f");
        } else if (closed == false && db == null) {
            Utils.send("§cThis a problem. The SQLManager is open but the Database is not...");
            Utils.send("§cYou might to stop server for this kind of problem to fix this error");
            Utils.send("&cError Code: 0x4f");
        }
    }

    public boolean isValid() {
        try {
            if (con.isClosed()) {
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
        try (PreparedStatement stmt = this.con.prepareStatement("SELECT 1")) {
            stmt.executeQuery();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public void reconnect() {
        try {
            close();
            closed = false;
            con = db.forceConnection();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public boolean setSpawnPosition(Position pos) {
        int x = pos.getFloorX();
        int y = pos.getFloorY();
        int z = pos.getFloorZ();
        try (PreparedStatement stmt = con.prepareStatement("UPDATE `island` SET `spawnX` = ?, `spawnY` = ?, `spawnZ` = ? WHERE `isSpawn` = '1'")) {
            stmt.setInt(1, x);
            stmt.setInt(2, y);
            stmt.setInt(3, z);
            stmt.addBatch();
            stmt.executeBatch();
            stmt.close();
            return true;
        } catch (SQLException ex) {
        }
        return false;
    }

    public IslandData getIslandLocation(String levelName, int X, int Z) {
        int id = plugin.getIsland().generateIslandKey(X, Z);
        IslandData database = new IslandData(levelName, X, Z, Settings.protectionrange);
        try (Statement stmt = con.createStatement()) {
            String l = levelName;
            ResultSet set = stmt.executeQuery("SELECT * FROM `island` WHERE(`world` = '" + l + "' AND `islandId` = '" + id + "')");
            if (set.isClosed()) {
                return database;
            }
            while (set.next()) {
                database = new IslandData(set.getString("world"), set.getInt("x"), set.getInt("y"), set.getInt("z"), set.getInt("spawnX"), set.getInt("spawnY"), set.getInt("spawnZ"), set.getInt("psize"), set.getString("name"), set.getString("owner"), set.getString("biome"), set.getInt("id"), set.getInt("islandId"), set.getBoolean("locked"), set.getString("protection"), set.getBoolean("isSpawn"));
                break;
            }
            stmt.close();
        } catch (SQLException ex) {
        }
        return database;
    }

    public ArrayList<IslandData> getIslands(String owner) {
        ArrayList<IslandData> pd = new ArrayList<>();
        try (Statement stmt = con.createStatement()) {
            ResultSet set = stmt.executeQuery("SELECT * FROM `island` WHERE `owner` = '" + owner + "'");
            if (set.isClosed()) {
                return pd;
            }
            while (set.next()) {
                pd.add(new IslandData(set.getString("world"), set.getInt("x"), set.getInt("y"), set.getInt("z"), set.getInt("spawnX"), set.getInt("spawnY"), set.getInt("spawnZ"), set.getInt("psize"), set.getString("name"), set.getString("owner"), set.getString("biome"), set.getInt("id"), set.getInt("islandId"), set.getBoolean("locked"), set.getString("protection"), set.getBoolean("isSpawn")));
            }
            stmt.close();
        } catch (SQLException ex) {}
        return pd;
    }

    public ArrayList<IslandData> getIslands(String owner, String levelName) {
        ArrayList<IslandData> pd = new ArrayList<>();
        try (Statement stmt = con.createStatement()) {
            ResultSet set = stmt.executeQuery("SELECT * FROM `island` WHERE `owner` = '" + owner + "' AND `world` = '" + levelName + "'");
            if (set.isClosed()) {
                return pd;
            }
            while (set.next()) {
                pd.add(new IslandData(set.getString("world"), set.getInt("x"), set.getInt("y"), set.getInt("z"), set.getInt("spawnX"), set.getInt("spawnY"), set.getInt("spawnZ"), set.getInt("psize"), set.getString("name"), set.getString("owner"), set.getString("biome"), set.getInt("id"), set.getInt("islandId"), set.getBoolean("locked"), set.getString("protection"), set.getBoolean("isSpawn")));
            }
            stmt.close();
        } catch (SQLException ex) {}
        return pd;
    }

    public IslandData getIsland(String name, int homes) {
        // safe block
        IslandData pd = null;
        try (Statement stmt = con.createStatement()) {
            ResultSet set = stmt.executeQuery("SELECT * FROM `island` WHERE(`owner` = '" + name + "' AND `id` = '" + homes + "')");
            if (set.isClosed()) {
                return pd;
            }
            pd = new IslandData(set.getString("world"), set.getInt("x"), set.getInt("y"), set.getInt("z"), set.getInt("spawnX"), set.getInt("spawnY"), set.getInt("spawnZ"), set.getInt("psize"), set.getString("name"), set.getString("owner"), set.getString("biome"), set.getInt("id"), set.getInt("islandId"), set.getBoolean("locked"), set.getString("protection"), set.getBoolean("isSpawn"));
            stmt.close();
        } catch (SQLException ex) {}
        return pd;
    }

    public boolean deleteIsland(IslandData pd) {
        // deprecated block
        boolean result = false;
        try {
            PreparedStatement set;
            if (pd.getId() >= 0) {
                set = con.prepareStatement("DELETE FROM `island` WHERE(`id` = ? AND `owner` = ?)");
                set.setInt(1, pd.getId());
                set.setString(2, pd.getOwner());
            } else {
                set = con.prepareStatement("DELETE FROM `island` WHERE(`world` = ? AND `x` = ? AND `z` = ?)");
                set.setString(1, pd.getLevelName());
                set.setInt(2, pd.getCenter().getFloorX());
                set.setInt(3, pd.getCenter().getFloorZ());
            }
            set.execute();
            result = true;
        } catch (SQLException ex) {}
        return result;
    }

    public IslandData getSpawn() {
        // safe block
        IslandData pd = null;
        if (islandSpawn != null) {
            return islandSpawn;
        }
        try (Statement stmt = con.createStatement()) {
            ResultSet set = stmt.executeQuery("SELECT * FROM `island` WHERE `isSpawn` = '1'");
            if (set.isClosed()) {
                return pd;
            }

            while (set.next()) {
                pd = new IslandData(set.getString("world"), set.getInt("x"), set.getInt("y"), set.getInt("z"), set.getInt("spawnX"), set.getInt("spawnY"), set.getInt("spawnZ"), set.getInt("psize"), set.getString("name"), set.getString("owner"), set.getString("biome"), set.getInt("id"), set.getInt("islandId"), set.getBoolean("locked"), set.getString("protection"), set.getBoolean("isSpawn"));
                break;
            }
            stmt.close();
        } catch (SQLException ex) {}
        islandSpawn = pd;
        return pd;
    }

    public IslandData getIslandById(int id) {
        // safe block
        IslandData pd = null;
        try (Statement stmt = con.createStatement()) {
            ResultSet set = stmt.executeQuery("SELECT * FROM `island` WHERE `islandId` = '" + id + "'");
            if (set.isClosed()) {
                return pd;
            }
            while (set.next()) {
                pd = new IslandData(set.getString("world"), set.getInt("x"), set.getInt("y"), set.getInt("z"), set.getInt("spawnX"), set.getInt("spawnY"), set.getInt("spawnZ"), set.getInt("psize"), set.getString("name"), set.getString("owner"), set.getString("biome"), set.getInt("id"), set.getInt("islandId"), set.getBoolean("locked"), set.getString("protection"), set.getBoolean("isSpawn"));
                break;
            }
            stmt.close();
        } catch (SQLException ex) {}
        return pd;
    }

    public void close() {
        try {
            this.closed = true;
            this.con.close();
            // Clear all variables
            islandCache.clear();
            islandSpawn = null;
        } catch (SQLException ex) {
        }
    }

    public boolean createIsland(IslandData pd) {
        try (PreparedStatement set = con.prepareStatement("INSERT INTO `island` (`id`, `islandId`, `x`, `y`, `z`, `isSpawn`, `psize`, `owner`, `name`, `world`, `biome`, `locked`, `protection`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);")) {
            set.setInt(1, pd.getId());
            set.setInt(2, pd.getIslandId());
            set.setInt(3, pd.getCenter().getFloorX());
            set.setInt(4, pd.getCenter().getFloorY());
            set.setInt(5, pd.getCenter().getFloorZ());
            set.setBoolean(6, pd.isSpawn());
            set.setInt(7, pd.getProtectionSize());
            set.setString(8, pd.getOwner());
            set.setString(9, pd.getName());
            set.setString(10, pd.getLevelName());
            set.setString(11, pd.getBiome());
            set.setBoolean(12, pd.isLocked());
            set.setString(13, pd.getSettings());
            set.addBatch();
            set.executeBatch();
            set.close();
            return true;
        } catch (BatchUpdateException b) {
            JDBCUtilities.printBatchUpdateException(b);
        } catch (SQLException ex) {
        }
        return false;
    }

    public boolean saveIsland(IslandData pd) {
        try (PreparedStatement stmt = con.prepareStatement("UPDATE `island` SET `name` = ?, `biome` = ?, `locked` = ?,`isSpawn` = ?, `protection` = ?, `spawnX` = ?, `spawnY` = ?, `spawnZ` = ? WHERE(`id` = '" + pd.getId() + "' AND `owner` = '" + pd.getOwner() + "')")) {
            stmt.setString(1, pd.getName());
            stmt.setString(2, pd.getBiome());
            stmt.setBoolean(3, pd.isLocked());
            stmt.setBoolean(4, pd.isSpawn());
            stmt.setString(5, pd.getSettings());
            stmt.setInt(6, pd.homeX);
            stmt.setInt(7, pd.homeY);
            stmt.setInt(8, pd.homeZ);
            stmt.addBatch();
            stmt.executeBatch();
            stmt.close();
            return true;
        } catch (SQLException ex) {
        }
        return false;
    }

    public ArrayList<String> getWorlds() {
        ArrayList<String> world = new ArrayList<>();
        try (Statement kt = con.createStatement()) {
            ResultSet set = kt.executeQuery("SELECT `world` FROM `worlds`");
            if (set.isClosed()) {
                return world;
            }
            while (set.next()) {
                world.add(set.getString("world"));
            }
        } catch (SQLException ex) {
        }
        return world;
    }

    public boolean saveWorlds(ArrayList<String> pd) {
        try (PreparedStatement set = con.prepareStatement("INSERT INTO `worlds` (`world`) VALUES (?);")) {
            ArrayList<String> second = getWorlds();
            for (String pd2 : pd) {
                if (!second.contains(pd2)) {
                    set.setString(1, pd2);
                    set.addBatch();
                    set.executeBatch();
                }
            }
            set.close();
            return true;
        } catch (SQLException ex) {
        }
        return false;
    }

    public List<String> getPlayersData() {
        List<String> playersData = new ArrayList<>();
        try (Statement kt = con.createStatement()) {
            ResultSet set = kt.executeQuery("SELECT * FROM `players` ");
            if (set.isClosed()) {
                return playersData;
            }
            while (set.next()) {
                playersData.add(set.getString("player"));
            }
        } catch (SQLException ex) {
        }
        return playersData;
    }

    @SuppressWarnings("unchecked")
    public PlayerData getPlayerData(String st) {
        // TESTED SUCCESS
        PlayerData pd = null;
        try (Statement kt = con.createStatement()) {
            ResultSet set = kt.executeQuery("SELECT * FROM `players` WHERE `player` = '" + st + "'");
            if (set.isClosed()) {
                return pd;
            }
            pd = new PlayerData(
                set.getString("player"),
                set.getInt("homes"),
                Utils.stringToArray(set.getString("members"), ", "),
                (HashMap<String, Boolean>) Utils.stringToMap(set.getString("challengelist")),
                (HashMap<String, Integer>) Utils.stringToMap(set.getString("challengelisttimes")),
                set.getInt("islandlvl"),
                set.getBoolean("inTeam"),
                set.getString("teamLeader"),
                set.getString("teamIslandLocation"),
                set.getInt("resetleft"),
                Utils.stringToArray(set.getString("banList"), ", "),
                set.getString("locale"));
        } catch (SQLException ex) {
        }
        return pd;
    }

    public boolean createPlayer(String p) {
        // TESTED SUCCESS
        try (PreparedStatement set = con.prepareStatement("INSERT INTO `players` ("
            + "`player`, "
            + "`homes`, "
            + "`resetleft`, "
            + "`banlist`, "
            + "`teamleader`, "
            + "`teamislandlocation`, "
            + "`inteam` , "
            + "`islandlvl`, "
            + "`members`,"
            + "`challengelist`, "
            + "`challengelisttimes`, "
            + "`name`, "
            + "`locale`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);")) {
            PlayerData pd = new PlayerData(p, 0, Settings.reset);
            set.setString(1, pd.playerName);
            set.setInt(2, pd.homes);
            set.setInt(3, pd.resetleft);
            set.setString(4, Utils.arrayToString(pd.banList));
            set.setString(5, pd.teamLeader);
            set.setString(6, pd.teamIslandLocation);
            set.setBoolean(7, pd.inTeam);
            set.setInt(8, pd.islandLevel);
            set.setString(9, Utils.arrayToString(pd.members));
            set.setString(10, Utils.hashToString(pd.challengeList));
            set.setString(11, Utils.hashToString(pd.challengeListTimes));
            set.setString(12, pd.name);
            set.setString(13, pd.pubLocale);
            set.addBatch();

            set.executeBatch();
            set.close();
            return true;
        } catch (BatchUpdateException b) {
            JDBCUtilities.printBatchUpdateException(b);
        } catch (SQLException ex) {
        }
        return false;
    }

    public boolean savePlayerData(PlayerData pd) {
        // TESTED SUCCESS
        try (PreparedStatement stmt = con.prepareStatement(
            "UPDATE `players` SET "
                + "`homes` = ?, "
                + "`resetleft` = ?, "
                + "`banlist` = ?, "
                + "`teamleader` = ?, "
                + "`teamislandlocation` = ?, "
                + "`inteam` = ?, "
                + "`islandlvl` = ?, "
                + "`members` = ?, "
                + "`challengelist` = ?, "
                + "`challengelisttimes` = ?, "
                + "`name` = ?, "
                + "`locale` = ?, "
                + "WHERE `player` = '" + pd.playerName + "'")) {
            stmt.setInt(1, pd.homes);
            stmt.setInt(2, pd.resetleft);
            stmt.setString(3, Utils.arrayToString(pd.banList));
            stmt.setString(4, pd.teamLeader);
            stmt.setString(5, pd.teamIslandLocation);
            stmt.setBoolean(6, pd.inTeam);
            stmt.setInt(7, pd.islandLevel);
            stmt.setString(8, Utils.arrayToString(pd.members));
            stmt.setString(9, Utils.hashToString(pd.challengeList));
            stmt.setString(10, Utils.hashToString(pd.challengeListTimes));
            stmt.setString(11, pd.name);
            stmt.setString(12, pd.pubLocale);
            stmt.addBatch();
            stmt.executeBatch();

            stmt.close();
            return true;
        } catch (SQLException ex) {
        }
        return false;
    }

    ///------------ FACTION DATA ------------

    public int getFactionCount() {
        int currentCount = 0;
        try (Statement stmt = con.createStatement()) {
            ResultSet set = stmt.executeQuery("SELECT * FROM `faction`");
            if (set.isClosed()) return 0;
            while (set.next()) currentCount++;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return currentCount;
    }
}
