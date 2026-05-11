package bata;

import bata.dao.RoomDao;
import bata.model.Room;
import bata.protocol.RoomRecorder;
import bata.protocol.MobRecorder;
import bata.protocol.LogImporter;
import bata.dao.MobDao;
import bata.model.Mob;
import com.mythicscape.batclient.interfaces.*;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlite3.SQLitePlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Plugin extends BatClientPlugin implements BatClientPluginTrigger, BatClientPluginCommandTrigger  {
    private RoomRecorder roomRecorder;
    private BatWindow gui;
    private Consumer<Room> onRoomChange;
    private Jdbi jdbi;
    private RoomDao roomDao;
    private MobDao mobDao;
    private MobRecorder mobRecorder;
    private LogImporter logImporter;


    @Override
    public void loadPlugin() {
        try {
            Class.forName("org.sqlite.JDBC");

            this.debug(System.getProperty("java.io.tmpdir"));
            String dbFile = String.format("jdbc:sqlite:%s", getSqliteDbFile());

            this.jdbi = Jdbi
                    .create(dbFile)
                    .installPlugin(new SQLitePlugin());
            this.roomDao = new RoomDao(jdbi);
            this.mobDao = new MobDao(jdbi);
            this.mobRecorder = new MobRecorder(mobDao);
            this.logImporter = new LogImporter(roomDao, mobDao);

            this.initDb();

            this.onRoomChange = room -> {
                if (room != null) {
                    roomDao.upsertRoom(room);
                    this.debug("Room changed: " + room.getName());
                    String description = room.getDescription();
                    if (description != null && !description.isEmpty()) {
                        this.debug("Room description length: " + description.length());
                        if (description.contains("\u001b[31m") || description.contains("\u001b[32m")) {
                            this.debug("Found color codes in description");
                        }

                        // Parse whereami for coordinates (bcproxy feature)
                        Room.WhereamiResult w = Room.parseWhereami(description);
                        if (w != null) {
                            this.debug("Whereami: continent=" + w.continent + " (" + w.x + "," + w.y + ")");
                        }
                    }
                    mobRecorder.recordMobs(room.getDescription(), room);
                }
            };

            this.roomRecorder = new RoomRecorder(this.onRoomChange);
            this.getPluginManager().addProtocolListener(this.roomRecorder);

            this.debug("Bata loaded.");
        } catch (Exception e) {
            this.debug("Failed to load plugin Bata:");
            this.debug(e.toString());
        }
    }

    @Override
    public String getName() {
        return "Bata";
    }

    @Override
    public ParsedResult trigger(ParsedResult parsedResult) {
        return null;
    }

    @Override
    public String trigger(String input) {
        if (input.startsWith("?room ")) {
            String searchFor = input.substring(6);
            if (searchFor.isEmpty()) {
                this.debug("empty input for room search");
            }

            List<Map<String, String>> found = roomDao.searchByShort(searchFor);
            if (found.isEmpty()) {
                return "shrug";
            }

            found.forEach(row -> {
                this.getClientGUI().printText("generic", String.format("- %s (%s)\n", row.get("name"), row.get("area")));
            });

            return "";
        }

        if (input.startsWith("?mob ")) {
            String searchFor = input.substring(5);
            if (searchFor.isEmpty()) {
                this.debug("empty input for mob search");
            }

            List<Map<String, Object>> found = this.jdbi.withHandle(handle ->
                handle.createQuery("SELECT long_name, is_aggro FROM mobs WHERE LOWER(long_name) LIKE :searchFor LIMIT 16")
                    .bind("searchFor", String.format("%%%s%%", searchFor.toLowerCase()))
                    .mapToMap()
                    .list()
            );

            if (found.isEmpty()) {
                return "shrug";
            }

            found.forEach(row -> {
                String color = "1".equals(String.valueOf(row.get("is_aggro"))) ? "红" : "绿";
                this.getClientGUI().printText("generic", String.format("- %s (%s)\n", row.get("long_name"), color));
            });

            return "";
        }

        // ?import command: import log files (bcproxy feature)
        if (input.startsWith("?import ")) {
            String path = input.substring(8).trim();
            if (path.isEmpty()) {
                return "Usage: ?import <log_dir_or_file>";
            }
            try {
                LogImporter.LogImportStats stats;
                if (Files.isDirectory(Paths.get(path))) {
                    stats = logImporter.importDirectory(path);
                } else {
                    stats = logImporter.importFile(path);
                }
                this.getClientGUI().printText("generic", "[Bata] Import complete:\n" + stats.toString());
            } catch (Exception e) {
                this.getClientGUI().printText("generic", "[Bata] Import error: " + e.getMessage() + "\n");
            }
            return "";
        }

        return null;
    }

    private String getSqliteDbFile() throws IOException {
        String home = System.getProperty("user.home");
        this.debug(home);
        String dataDir = String.format("%s/batclient/data", home);
        Files.createDirectories(Paths.get(dataDir));
        return Paths.get(dataDir, "bata.db").toString();
    }

    private void initDb() {
        this.jdbi.withHandle(handle -> {
            handle.execute("CREATE TABLE IF NOT EXISTS rooms (\n" +
                    "    id TEXT PRIMARY KEY,\n" +
                    "    area TEXT,\n" +
                    "    name TEXT,\n" +
                    "    description TEXT,\n" +
                    "    exits TEXT,\n" +
                    "    last_move_dir TEXT,\n" +
                    "    is_indoor BOOLEAN,\n" +
                    "    continent TEXT,\n" +
                    "    x INTEGER DEFAULT 0,\n" +
                    "    y INTEGER DEFAULT 0\n" +
                    ");\n");

            handle.execute("CREATE TABLE IF NOT EXISTS mobs (\n" +
                    "    id TEXT,\n" +
                    "    long_name TEXT,\n" +
                    "    is_aggro INTEGER,\n" +
                    "    PRIMARY KEY (id, long_name)\n" +
                    ");\n");

            return null;
        });
    }

    private void debug(String message) {
        this.getClientGUI().printText("generic", String.format("[Bata(debug)] %s\n", message));
    }
}
