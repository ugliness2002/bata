package bata;

import bata.dao.RoomDao;
import bata.model.Room;
import bata.protocol.RoomRecorder;
import bata.protocol.MobRecorder;
import bata.protocol.LogImporter;
import bata.dao.MobDao;
import com.mythicscape.batclient.interfaces.*;
import org.jdbi.v3.core.Jdbi;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

public class Plugin extends BatClientPlugin implements BatClientPluginTrigger, BatClientPluginCommandTrigger  {
    private RoomRecorder roomRecorder;
    private Consumer<Room> onRoomChange;
    private Jdbi jdbi;
    private RoomDao roomDao;
    private MobDao mobDao;
    private MobRecorder mobRecorder;
    private LogImporter logImporter;


    @Override
    public void loadPlugin() {
        try {
            // Load PostgreSQL driver explicitly (avoids META-INF/services conflict)
            Class.forName("org.postgresql.Driver");

            String dbUrl = getDbUrl();
            this.debug("DB: " + dbUrl);

            this.jdbi = Jdbi.create(dbUrl);
            this.roomDao = new RoomDao(jdbi);
            this.mobDao = new MobDao(jdbi);
            this.mobRecorder = new MobRecorder(mobDao);
            this.logImporter = new LogImporter(roomDao, mobDao);

            this.initDb();

            this.onRoomChange = room -> {
                if (room != null) {
                    roomDao.upsertRoom(room);
                    String description = room.getDescription();
                    if (description != null && !description.isEmpty()) {
                        Room.WhereamiResult w = Room.parseWhereami(description);
                        if (w != null) {
                            this.debug("Whereami: " + w.continent + " (" + w.x + "," + w.y + ")");
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

    /**
     * Read DB connection from bata.properties next to the plugin jar,
     * or from ~/batclient/bata.properties. Falls back to localhost.
     */
    private String getDbUrl() {
        Properties props = new Properties();

        // Try plugin dir first
        try {
            String jarDir = Paths.get(Plugin.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getParent().toString();
            props.load(new FileInputStream(Paths.get(jarDir, "bata.properties").toFile()));
        } catch (Exception ignored) {}

        // Try user home
        if (props.isEmpty()) {
            try {
                String home = System.getProperty("user.home");
                props.load(new FileInputStream(Paths.get(home, "batclient", "bata.properties").toFile()));
            } catch (Exception ignored) {}
        }

        String host = props.getProperty("db.host", "localhost");
        String port = props.getProperty("db.port", "54433");
        String name = props.getProperty("db.name", "bata");
        String user = props.getProperty("db.user", "bata");
        String pass = props.getProperty("db.pass", "bata");

        return String.format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s",
            host, port, name, user, pass);
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
            String searchFor = input.substring(6).trim();
            if (searchFor.isEmpty()) {
                return "Usage: ?room <name>";
            }

            List<Map<String, String>> found = roomDao.searchByShort(searchFor);
            if (found.isEmpty()) {
                return "shrug";
            }

            found.forEach(row -> {
                String name = row.get("name");
                String area = row.get("area");
                String continent = row.get("continent");
                String x = row.get("x");
                String y = row.get("y");
                String exits = row.get("exits");
                boolean indoor = "true".equals(row.get("is_indoor"));

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("- %s (%s)", name, area));
                if (continent != null && !continent.isEmpty()) {
                    sb.append(String.format(" [%s]", continent));
                }
                if (!"0".equals(x) || !"0".equals(y)) {
                    sb.append(String.format(" (%s,%s)", x, y));
                }
                sb.append(indoor ? " 室内" : " 室外");
                if (exits != null && !exits.isEmpty()) {
                    sb.append(" 出口:").append(exits);
                }
                sb.append("\n");
                this.getClientGUI().printText("generic", sb.toString());
            });
            return "";
        }

        if (input.startsWith("?mob ")) {
            String searchFor = input.substring(5).trim();
            if (searchFor.isEmpty()) {
                return "Usage: ?mob <name>";
            }

            List<Map<String, String>> found = mobDao.searchByShort(searchFor);
            if (found.isEmpty()) {
                return "shrug";
            }

            found.forEach(row -> {
                String longName = row.get("long_name");
                String aggro = row.get("is_aggro");
                String roomName = row.get("room_name");
                String area = row.get("area");
                String color = "1".equals(aggro) ? "[红]" : "[绿]";

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("- %s %s", longName, color));
                if (roomName != null && !roomName.isEmpty()) {
                    sb.append(String.format(" @ %s", roomName));
                    if (area != null && !area.isEmpty()) {
                        sb.append(String.format(" (%s)", area));
                    }
                }
                sb.append("\n");
                this.getClientGUI().printText("generic", sb.toString());
            });
            return "";
        }

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

    private void initDb() {
        this.jdbi.withHandle(handle -> {
            handle.execute("CREATE TABLE IF NOT EXISTS rooms (\n" +
                    "    id TEXT PRIMARY KEY,\n" +
                    "    area TEXT,\n" +
                    "    name TEXT,\n" +
                    "    description TEXT,\n" +
                    "    exits TEXT,\n" +
                    "    last_move_dir TEXT,\n" +
                    "    is_indoor BOOLEAN DEFAULT FALSE,\n" +
                    "    continent TEXT,\n" +
                    "    x INTEGER DEFAULT 0,\n" +
                    "    y INTEGER DEFAULT 0\n" +
                    ")");

            handle.execute("CREATE TABLE IF NOT EXISTS mobs (\n" +
                    "    id TEXT,\n" +
                    "    long_name TEXT,\n" +
                    "    is_aggro INTEGER DEFAULT 0,\n" +
                    "    PRIMARY KEY (id, long_name)\n" +
                    ")");

            return null;
        });
    }

    private void debug(String message) {
        this.getClientGUI().printText("generic", String.format("[Bata(debug)] %s\n", message));
    }
}
