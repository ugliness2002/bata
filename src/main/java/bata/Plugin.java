package bata;

import bata.dao.RoomDao;
import bata.model.Room;
import bata.protocol.RoomRecorder;
import bata.protocol.MobRecorder;
import bata.dao.MobDao;
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
    private Consumer<Room> onRoomChange;
    private Jdbi jdbi;
    private RoomDao roomDao;
    private MobDao mobDao;
    private MobRecorder mobRecorder;

    @Override
    public void loadPlugin() {
        try {
            Class.forName("org.sqlite.JDBC");
            String dbFile = String.format("jdbc:sqlite:%s", getSqliteDbFile());
            this.jdbi = Jdbi.create(dbFile).installPlugin(new SQLitePlugin());
            this.roomDao = new RoomDao(jdbi);
            this.mobDao = new MobDao(jdbi);
            this.mobRecorder = new MobRecorder(mobDao);
            this.initDb();
            this.onRoomChange = room -> {
                if (room != null) {
                    roomDao.upsertRoom(room);
                    mobRecorder.recordMobs(room.getDescription(), room);
                }
            };
            this.roomRecorder = new RoomRecorder(this.onRoomChange);
            this.getPluginManager().addProtocolListener(this.roomRecorder);
            this.debug("Bata loaded.");
        } catch (Exception e) {
            this.debug("Failed: " + e.toString());
        }
    }

    @Override public String getName() { return "Bata"; }
    @Override public ParsedResult trigger(ParsedResult r) { return null; }

    @Override
    public String trigger(String input) {
        if (input.startsWith("?room ")) {
            String s = input.substring(6).trim();
            if (s.isEmpty()) return "Usage: ?room <name>";
            List<Map<String, String>> found = roomDao.searchByShort(s);
            if (found.isEmpty()) return "shrug";
            found.forEach(row -> {
                String name = row.get("name"), area = row.get("area");
                String exits = row.get("exits");
                boolean indoor = "true".equals(row.get("indoors"));
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("- %s (%s)", name, area));
                sb.append(indoor ? " [室内]" : " [室外]");
                if (exits != null && !exits.isEmpty()) sb.append(" exits:").append(exits);
                sb.append("\n");
                this.getClientGUI().printText("generic", sb.toString());
            });
            return "";
        }
        if (input.startsWith("?mob ")) {
            String s = input.substring(5).trim();
            if (s.isEmpty()) return "Usage: ?mob <name>";
            try {
            List<Map<String, String>> found = this.jdbi.withHandle(h ->
                h.createQuery("SELECT m.name AS mob_name, m.aggressive, r.shortdesc AS room_name, r.area FROM mobs m LEFT JOIN rooms r ON m.room_id = r.id WHERE LOWER(m.name) LIKE :q LIMIT 16")
                    .bind("q", "%" + s.toLowerCase() + "%")
                    .map((rs, ctx) -> {
                        Map<String, String> row = new java.util.HashMap<>();
                        row.put("name", rs.getString("mob_name"));
                        row.put("aggressive", String.valueOf(rs.getInt("aggressive")));
                        row.put("room_name", rs.getString("room_name"));
                        row.put("area", rs.getString("area"));
                        return row;
                    }).list());
            if (found.isEmpty()) return "shrug";
            found.forEach(row -> {
                String color = "1".equals(row.get("aggressive")) ? "[红]" : "[绿]";
                String rn = row.get("room_name"), ar = row.get("area");
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("- %s %s", row.get("name"), color));
                if (rn != null && !rn.isEmpty()) sb.append(" @ ").append(rn);
                if (ar != null && !ar.isEmpty()) sb.append(" (").append(ar).append(")");
                sb.append("\n");
                this.getClientGUI().printText("generic", sb.toString());
            });
            } catch (Exception e) {
                this.debug("?mob error: " + e.toString());
            }
            return "";
        }
        return null;
    }

    private String getSqliteDbFile() throws IOException {
        String home = System.getProperty("user.home");
        String dataDir = home + "/batclient/data";
        Files.createDirectories(Paths.get(dataDir));
        return Paths.get(dataDir, "bata.db").toString();
    }

    private void initDb() {
        this.jdbi.withHandle(handle -> {
            handle.execute("CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, area TEXT, shortdesc TEXT, longdesc TEXT, exits TEXT, indoors BOOLEAN)");
            handle.execute("CREATE TABLE IF NOT EXISTS mobs (room_id TEXT, name TEXT, area TEXT, aggressive INTEGER DEFAULT 0, PRIMARY KEY (room_id, name))");
            return null;
        });
    }

    private void debug(String msg) {
        this.getClientGUI().printText("generic", String.format("[Bata(debug)] %s\n", msg));
    }
}
