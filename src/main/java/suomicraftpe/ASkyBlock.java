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
package suomicraftpe;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.TextFormat;
import suomicraftpe.command.AdminCMD;
import suomicraftpe.command.ChallangesCMD;
import suomicraftpe.database.ASConnection;
import suomicraftpe.database.JDBCUtilities;
import suomicraftpe.database.variables.MySQLDatabase;
import suomicraftpe.database.variables.SQLiteDatabase;
import suomicraftpe.economy.Economy;
import suomicraftpe.island.GridManager;
import suomicraftpe.island.IslandManager;
import suomicraftpe.listener.ChatHandler;
import suomicraftpe.listener.IslandGuard;
import suomicraftpe.listener.invitation.InvitationHandler;
import suomicraftpe.locales.ASlocales;
import suomicraftpe.panels.Panel;
import suomicraftpe.player.PlayerData;
import suomicraftpe.player.TeamManager;
import suomicraftpe.player.TeleportLogic;
import suomicraftpe.schematic.SchematicHandler;
import suomicraftpe.storage.InventorySave;
import suomicraftpe.storage.IslandData;
import suomicraftpe.storage.WorldSettings;
import suomicraftpe.task.TaskManager;
import suomicraftpe.utils.ConfigManager;
import suomicraftpe.utils.Settings;
import suomicraftpe.utils.Utils;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Author: Adam Matthew
 * <p>
 * Main class of SkyBlock Framework! Complete with API and Events. May contains
 * Nuts!
 */
public class ASkyBlock extends PluginBase implements ASkyBlockAPI {

    public static SchematicHandler schematics;
    public static Economy econ;
    public static String moduleVersion = "0eb81f61";
    private static ASkyBlock object;

    public int[] version;
    public ArrayList<WorldSettings> level = new ArrayList<>();

    private Config cfg;
    private ASConnection db = null;
    private ChatHandler chatHandler;
    private InvitationHandler invitationHandler;
    private IslandManager manager;
    private GridManager grid;
    private InventorySave inventory;
    private TeamManager managers;
    private TeleportLogic teleportLogic;
    private ChallangesCMD cmds;
    private Messages msgs;
    private Panel panel;

    private HashMap<String, ASlocales> availableLocales = new HashMap<>();

    /**
     * Return of ASkyBlock plug-in
     *
     * @return ASkyBlock
     */
    public static ASkyBlock get() {
        return object;
    }

    public ASConnection getDatabase() {
        return db;
    }

    public ChatHandler getChatHandlers() {
        return chatHandler;
    }

    public InvitationHandler getInvitationHandler() {
        return invitationHandler;
    }

    public IslandManager getIsland() {
        return manager;
    }

    public GridManager getGrid() {
        return grid;
    }

    public InventorySave getInventory() {
        return inventory;
    }

    public TeamManager getTManager() {
        return managers;
    }

    public Panel getPanel() {
        return panel;
    }

    public IslandData getIslandInfo(Player player, int homes) {
        return getDatabase().getIsland(player.getName(), homes);
    }

    public IslandData getIslandInfo(String player) {
        return getDatabase().getIsland(player, 1);
    }

    public IslandData getIslandInfo(String player, int homes) {
        return getDatabase().getIsland(player, homes);
    }

    public ChallangesCMD getChallenges() {
        return cmds;
    }

    public boolean inIslandWorld(Player p) {
        return level.contains(p.getLevel().getName());
    }

    public PlayerData getPlayerInfo(Player player) {
        return getDatabase().getPlayerData(player.getName());
    }

    public IslandData getIslandInfo(Location location) {
        return getDatabase().getIslandLocation(location.getLevel().getName(), location.getFloorX(), location.getFloorZ());
    }

    public TeleportLogic getTeleportLogic() {
        return teleportLogic;
    }

    public Integer getIslandLevel(Player player) {
        PlayerData pd = getPlayerInfo(player);
        return pd == null ? 0 : pd.getIslandLevel();
    }

    public String getDefaultWorld() {
        return "SkyBlock";
    }

    public IslandData getIslandInfo(Player player) {
        return getIslandInfo(player.getName());
    }

    public String getPluginVersionString() {
        return getDescription().getVersion();
    }

    public int[] getPluginVersion() {
        String ver = getDescription().getVersion();
        if (ver.contains("-")) {
            ver = ver.split("-")[0];
        }
        String[] split = ver.split("\\.");
        return new int[]{Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2])};
    }

    public boolean checkVersion(int[] version, int... version2) {
        return version[0] > version2[0] || version[0] == version2[0] && version[1] > version2[1] || version[0] == version2[0]
            && version[1] == version2[1] && version[2] >= version2[2];
    }

    public int[] getVersion() {
        return version;
    }

    /**
     * Get if debugging enabled
     *
     * @return true if enabled
     */
    public boolean isDebug() {
        return ASkyBlock.object.cfg.getBoolean("debug");
    }

    @Override
    public void onLoad() {
        if (!(object instanceof ASkyBlock)) {
            object = this;
        }
        initConfig();
        Generator.addGenerator(SkyBlockGenerator.class, "island", SkyBlockGenerator.TYPE_SKYBLOCK);
        TaskManager.IMP = new TaskManager();
    }

    @Override
    public void onEnable() {
        initDatabase();
        generateLevel();
        if (cfg.getBoolean("fastLoad")) {
            TaskManager.runTaskLater(() -> start(), 100);
        } else {
            start();
        }
    }

    public ArrayList<String> getLevels() {
        ArrayList<String> level = new ArrayList<>();
        for (WorldSettings settings : this.level) {
            level.add(settings.getLevel().getName());
        }
        return level;
    }

    private void start() {
        initIslands();
        registerObject();
        test();
    }

    public WorldSettings getSettings(String level) {
        return this.level.stream().filter(settings -> settings.getLevel().getName().equalsIgnoreCase(level)).findFirst().orElse(null);
    }

    @Override
    public void onDisable() {
        saveLevel(true);
        this.db.close();
        msgs.saveMessages();
    }

    @Override
    public Config getConfig() {
        return cfg;
    }

    private void initDatabase() {
        if (cfg.getString("database.connection").equalsIgnoreCase("mysql")) {
            try {
                db = new ASConnection(this, new MySQLDatabase(cfg.getString("database.MySQL.host"), cfg.getInt("database.MySQL.port"), cfg.getString("database.MySQL.database"), cfg.getString("database.MySQL.username"), cfg.getString("database.MySQL.password")), true);
            } catch (SQLException ex) {
                ////JDBCUtilities.printSQLException(ex);
            } catch (ClassNotFoundException | InterruptedException ex) {
                Utils.send("Unable to create MySql database");
            }
        } else {
            try {
                db = new ASConnection(this, new SQLiteDatabase(new File(getDataFolder(), cfg.getString("database.SQLite.file-name") + ".db")), true);
            } catch (SQLException ex) {
                ////JDBCUtilities.printSQLException(ex);
            } catch (ClassNotFoundException | InterruptedException ex) {
                Utils.send("Unable to create MySql database");
            }
        }
    }

    /**
     * Load every islands Components
     */
    private void initIslands() {
        getServer().getCommandMap().register("ASkyBlock", new Commands(this));
        getServer().getCommandMap().register("ASkyBlock", this.cmds = new ChallangesCMD(this));
        getServer().getCommandMap().register("ASkyBlock", new AdminCMD(this));
        PluginManager pm = getServer().getPluginManager();
        chatHandler = new ChatHandler(this);
        teleportLogic = new TeleportLogic(this);
        invitationHandler = new InvitationHandler(this);
        panel = new Panel(this);
        msgs = new Messages(this);
        msgs.loadMessages();
        getServer().getPluginManager().registerEvents(chatHandler, this);
        pm.registerEvents(new IslandGuard(this), this);
        ServerScheduler pd = getServer().getScheduler();
        pd.scheduleRepeatingTask(new PluginTask(this), 20);
    }

    public void saveLevel(boolean showEnd) {
        ArrayList<String> level = new ArrayList<>();
        for (WorldSettings settings : this.level) {
            level.add(settings.getLevel().getName());
        }
        this.db.saveWorlds(level);
    }

    private void registerObject() {
        loadV2Schematic();
        manager = new IslandManager(this);
        grid = new GridManager(this);
        managers = new TeamManager(this);
        inventory = new InventorySave(this);
    }

    public String getPrefix() {
        return cfg.getString("Prefix").replace("&", "§");
    }

    public ASlocales getLocale(Player p) {
        if (p == null) {
            return getAvailableLocales().get(Settings.defaultLanguage);
        }
        PlayerData pd = this.getPlayerInfo(p);
        if (!this.getAvailableLocales().containsKey(pd.pubLocale)) {
            return getAvailableLocales().get(Settings.defaultLanguage);
        }
        return getAvailableLocales().get(pd.pubLocale);
    }

    public final void initConfig() {
        Utils.EnsureDirectory(Utils.DIRECTORY);
        Utils.EnsureDirectory(Utils.LOCALES_DIRECTORY);
        if (getResource("config.yml") != null) {
            saveResource("config.yml");
        }
        if (getResource("challenges.yml") != null) {
            saveResource("challenges.yml");
        }
        cfg = new Config(new File(getDataFolder(), "config.yml"), Config.YAML);
        recheck();
        ConfigManager.load();
    }

    public void recheck() {
        boolean update = false;
        File file;
        Config cfgg = new Config(file = new File(ASkyBlock.get().getDataFolder(), "config.yml"), Config.YAML);
        if (!cfgg.getString("version").equalsIgnoreCase(ConfigManager.CONFIG_VERSION)) {
            update = true;
        }
        if (update) {
            file.renameTo(new File(ASkyBlock.get().getDataFolder(), "config.old"));
            ASkyBlock.get().saveResource("config.yml");
        }
        cfg.reload();
    }

    private void generateLevel() {
        if (!Server.getInstance().isLevelGenerated("SkyBlock")) {
            Server.getInstance().generateLevel("SkyBlock", 0, SkyBlockGenerator.class);
        }
        if (!Server.getInstance().isLevelLoaded("SkyBlock")) {
            Server.getInstance().loadLevel("SkyBlock");
        }
        List<String> levels = db.getWorlds();
        if (!levels.contains("SkyBlock")) {
            levels.add("SkyBlock");
        }

        ArrayList<WorldSettings> settings = new ArrayList<>();
        for (String levelName : levels) {
            if (!Server.getInstance().isLevelGenerated(levelName)) {
                Server.getInstance().generateLevel(levelName, 0, SkyBlockGenerator.class);
            }
            if (!Server.getInstance().isLevelLoaded(levelName)) {
                Server.getInstance().loadLevel(levelName);
            }
            if (Settings.stopTime) {
                Level world = getServer().getLevelByName(levelName);
                world.setTime(1600);
                world.stopTime();
            }

            Level level = getServer().getLevelByName(levelName);
            WorldSettings worldSettings = new WorldSettings(level);
            ConfigSection section = cfg.getSections("world." + levelName);
            if (!section.getKeys(false).isEmpty()) {
                Utils.send("Settings: ");
                String permission = section.getString("permission");
                int plotSize = section.getInt("plotSize");
                boolean stopTime = section.getBoolean("stopTime");
                int islandHieght = section.getInt("islandHeight");
                int seaLevel = section.getInt("seaLevel");
                worldSettings = new WorldSettings(permission, level, plotSize, stopTime, islandHieght, seaLevel);
            }
            settings.add(worldSettings);
        }
        this.level = settings;
    }

    public void loadV2Schematic() {
        File schematicFolder = new File(getDataFolder(), "schematics");
        if (!schematicFolder.exists()) {
            schematicFolder.mkdir();
        }
        schematics = new SchematicHandler(this, schematicFolder);
    }

    public Messages getMessages() {
        return msgs;
    }

    public HashMap<String, ASlocales> getAvailableLocales() {
        return availableLocales;
    }

    public void setAvailableLocales(HashMap<String, ASlocales> availableLocales) {
        this.availableLocales = availableLocales;
    }

    private void test() {
    }
}
