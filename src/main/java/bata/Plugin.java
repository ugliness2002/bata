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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Plugin extends BatClientPlugin implements BatClientPluginTrigger, BatClientPluginCommandTrigger  {
    private RoomRecorder roomRecorder;
    private Consumer<Room> onRoomChange;
    private Jdbi jdbi;
    private RoomDao roomDao;
    private MobDao mobDao;
    private MobRecorder mobRecorder;
    private LogImporter logImporter;
    private final AtomicBoolean dbReady = new AtomicBoolean(false);


    @Override
    public void loadPlugin() {
        // Register listeners IMMEDIATELY — don't block game startup
        this.onRoomChange = room -> {
            if (room != null && dbReady.get() && jdbi != null) {
                try {
                    roomDao.upsertRoom(room);
                    Room.WhereamiResult w = Room.parseWhereami(room.getDescription());
                    if (w != null) {
                        this.debug("Whereami: " + w.continent + " (" + w.x + "," + w.y + ")");
                    }
                    mobRecorder.recordMobs(room.getDescription(), room);
                } catch (Exception ignored) {}
            }
        };

        this.roomRecorder = new RoomRecorder(this.onRoomChange);
        this.getPluginManager().addProtocolListener(this.roomRecorder);

        this.debug("Bata loaded.");

        // DB connection in background thread — never blocks game
        new Thread(this::connectDb, "Bata-DB-Init").start();
    }

    private void connectDb() {
        try {
            Class.forName("org.postgresql.Driver");
            String dbUrl = getDbUrl();
            this.debug("DB: " + dbUrl.replaceAll("password=[^&]*", "password=***"));

            this.jdbi = Jdbi.create(dbUrl);
            this.roomDao = new RoomDao(jdbi);
            this.mobDao = new MobDao(jdbi);
            this.mobRecorder = new MobRecorder(mobDao);
            this.logImporter = new LogImporter(roomDao, mobDao);

            this.initDb();
            this.dbReady.set(true);
            this.debug("Database ready.");
        } catch (Exception e) {
            this.debug("DB failed: " + e.getMessage());
        }
    }

    private String getDbUrl() {
        Properties props = new Properties();
        try {
            String jarDir = Paths.get(Plugin.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getParent().toString();
            props.load(new FileInputStream(Paths.get(jarDir, "bata.properties").toFile()));
        } catch (Exception ignored) {}
        if (props.isEmpty()) {
            try {
                String home = System.getProperty("user.home");
                props.load(new FileInputStream(Paths.get(home, "batclient", "bata.properties").toFile()));
            } catch (Exception ignored) {}
        }
        String host = props.getProperty("db.host", "100.125.11.72");
        String port = props.getProperty("db.port", "5432");
        String name = props.getProperty("db.name", "batmud");
        String user = props.getProperty("db.user", "batmud_remote");
        String pass = props.getProperty("db.pass", "batmud2024remote");
        return String.format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s&connectTimeout=5&loginTimeout=5",
            host, port, name, user, pass);
    }

    @Override
    public String getName() { return "Bata"; }

    @Override
    public ParsedResult trigger(ParsedResult parsedResult) { return null; }

    @Override
    public String trigger(String input) {
        if (input.startsWith("?room ")) {
            String s = input.substring(6).trim();
            if (s.isEmpty() || !dbReady.get()) return "shrug";
            List<Map<String, String>> found = roomDao.searchByShort(s);
            if (found.isEmpty()) return "shrug";
            found.forEach(row -> {
                String name = row.get("name"), area = row.get("area");
                String continent = row.get("continent");
                String x = row.get("x"), y = row.get("y");
                String exits = row.get("exits");
                boolean indoor = "true".equals(row.get("is_indoor"));
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("- %s (%s)", name, area));
                if (continent != null && !continent.isEmpty()) sb.append(String.format(" [%s]", continent));
                if (!"0".equals(x) || !"0".equals(y)) sb.append(String.format(" (%s,%s)", x, y));
                sb.append(indoor ? " 室内" : " 室外");
                if (exits != null && !exits.isEmpty()) sb.append(" 出口:").append(exits);
                sb.append("\n");
                this.getClientGUI().printText("generic", sb.toString());
            });
            return "";
        }
        if (input.startsWith("?mob ")) {
            String s = input.substring(5).trim();
            if (s.isEmpty() || !dbReady.get()) return "shrug";
            List<Map<String, String>> found = mobDao.searchByShort(s);
            if (found.isEmpty()) return "shrug";
            found.forEach(row -> {
                String n = row.get("long_name"), aggro = row.get("is_aggro");
                String rn = row.get("room_name"), area = row.get("area");
                String color = "1".equals(aggro) ? "[红]" : "[绿]";
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("- %s %s", n, color));
                if (rn != null && !rn.isEmpty()) {
                    sb.append(String.format(" @ %s", rn));
                    if (area != null && !area.isEmpty()) sb.append(String.format(" (%s)", area));
                }
                sb.append("\n");
                this.getClientGUI().printText("generic", sb.toString());
            });
            return "";
        }
        if (input.startsWith("?import ")) {
            String path = input.substring(8).trim();
            if (path.isEmpty() || !dbReady.get()) return "shrug";
            try {
                LogImporter.LogImportStats stats;
                if (Files.isDirectory(Paths.get(path))) stats = logImporter.importDirectory(path);
                else stats = logImporter.importFile(path);
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
                    "    id TEXT PRIMARY KEY, area TEXT, name TEXT, description TEXT,\n" +
                    "    exits TEXT, last_move_dir TEXT, is_indoor BOOLEAN DEFAULT FALSE,\n" +
                    "    continent TEXT, x INTEGER DEFAULT 0, y INTEGER DEFAULT 0\n" +
                    ")");
            handle.execute("CREATE TABLE IF NOT EXISTS mobs (\n" +
                    "    id TEXT, long_name TEXT, is_aggro INTEGER DEFAULT 0,\n" +
                    "    PRIMARY KEY (id, long_name)\n" +
                    ")");
            return null;
        });
    }

    private void debug(String message) {
        this.getClientGUI().printText("generic", String.format("[Bata(debug)] %s\n", message));
    }
}
