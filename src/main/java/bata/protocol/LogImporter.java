package bata.protocol;

import bata.dao.MobDao;
import bata.dao.RoomDao;
import bata.model.Mob;
import bata.model.Room;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Offline log importer — port of bcproxy's parse_logs.py.
 * Reads BatMUD batclient log files and imports rooms, mobs, and coordinates.
 */
public class LogImporter {

    // BAT_MAPPER frame marker: <99BAT_MAPPER;;area;;hash;;roomid;;...;;shortdesc;;longdesc...>99
    private static final Pattern BAT_MAPPER_PATTERN =
        Pattern.compile("<99BAT_MAPPER;(.*?)>99");

    // ANSI-colored mob lines (bcproxy-style)
    private static final Pattern MOB_GREEN = Pattern.compile("\\x1b\\[(?:1;)?32m([^\\x1b]+)\\x1b\\[0m");
    private static final Pattern MOB_RED   = Pattern.compile("\\x1b\\[(?:1;)?31m([^\\x1b]+)\\x1b\\[0m");

    // Latin-1 charset used by batclient logs
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private final RoomDao roomDao;
    private final MobDao mobDao;
    private final LogImportStats stats = new LogImportStats();

    public LogImporter(RoomDao roomDao, MobDao mobDao) {
        this.roomDao = roomDao;
        this.mobDao = mobDao;
    }

    /**
     * Import all batclient log files from a directory.
     * Processes both playback-autolog*.txt and Generic*.txt files.
     */
    public LogImportStats importDirectory(String dirPath) throws IOException {
        stats.reset();
        Path dir = Paths.get(dirPath);
        if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dirPath);
        }

        // Process playback-autolog files (have ANSI colors)
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().contains("playback-autolog"))
                 .filter(p -> p.toString().endsWith(".txt"))
                 .sorted()
                 .forEach(this::processPlaybackFile);
        }

        // Process Generic files
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().contains("Generic"))
                 .filter(p -> p.toString().endsWith(".txt"))
                 .sorted()
                 .forEach(this::processGenericFile);
        }

        return stats;
    }

    /**
     * Import a single log file (auto-detects playback vs generic).
     */
    public LogImportStats importFile(String filePath) throws IOException {
        stats.reset();
        Path path = Paths.get(filePath);
        String name = path.getFileName().toString();

        if (name.contains("playback-autolog")) {
            processPlaybackFile(path);
        } else if (name.contains("Generic")) {
            processGenericFile(path);
        } else {
            // Try as generic format
            processPlaybackFile(path);
        }
        return stats;
    }

    private void processPlaybackFile(Path path) {
        stats.filesProcessed++;
        try {
            byte[] raw = Files.readAllBytes(path);
            String text = new String(raw, LATIN1);

            // Parse BAT_MAPPER frames
            Matcher m = BAT_MAPPER_PATTERN.matcher(text);
            Set<String> seenRooms = new HashSet<>();
            while (m.find()) {
                String frame = "BAT_MAPPER" + m.group(1);
                Room room = Room.fromFrame(frame);
                if (room != null && seenRooms.add(room.getId())) {
                    roomDao.upsertRoom(room);
                    stats.roomsImported++;
                }
                stats.batMapperFrames++;
            }

            // Parse mobs from ANSI-colored lines
            String[] lines = text.split("\\n");
            String lastRoomId = null;

            for (String line : lines) {
                // Track last known room from BAT_MAPPER frames
                Matcher bm = BAT_MAPPER_PATTERN.matcher(line);
                if (bm.find()) {
                    String[] parts = bm.group(1).split(";;");
                    if (parts.length >= 3 && !parts[0].isEmpty() && !parts[2].isEmpty()) {
                        lastRoomId = parts[2];
                    }
                }

                // Extract mobs
                if (lastRoomId != null) {
                    boolean found = false;

                    Matcher gm = MOB_GREEN.matcher(line);
                    while (gm.find()) {
                        String name = gm.group(1).trim();
                        if (name.length() > 3) {
                            mobDao.upsertMob(new Mob(lastRoomId, name, false));
                            stats.mobsImported++;
                            found = true;
                        }
                    }

                    Matcher rm = MOB_RED.matcher(line);
                    while (rm.find()) {
                        String name = rm.group(1).trim();
                        if (name.length() > 3) {
                            mobDao.upsertMob(new Mob(lastRoomId, name, true));
                            stats.mobsImported++;
                            found = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            stats.errors.add(path + ": " + e.getMessage());
        }
    }

    private void processGenericFile(Path path) {
        stats.filesProcessed++;
        try {
            byte[] raw = Files.readAllBytes(path);
            String text = new String(raw, LATIN1);

            // Parse BAT_MAPPER frames
            Matcher m = BAT_MAPPER_PATTERN.matcher(text);
            while (m.find()) {
                String frame = "BAT_MAPPER" + m.group(1);
                Room room = Room.fromFrame(frame);
                if (room != null) {
                    roomDao.upsertRoom(room);
                    stats.roomsImported++;
                }
                stats.batMapperFrames++;
            }

            // Parse whereami for coordinates
            m = Room.WHEREAMI.matcher(text);
            while (m.find()) {
                stats.whereamiFound++;
            }
        } catch (IOException e) {
            stats.errors.add(path + ": " + e.getMessage());
        }
    }

    /**
     * Import statistics.
     */
    public static class LogImportStats {
        public int filesProcessed;
        public int batMapperFrames;
        public int roomsImported;
        public int mobsImported;
        public int whereamiFound;
        public final List<String> errors = new ArrayList<>();

        void reset() {
            filesProcessed = 0;
            batMapperFrames = 0;
            roomsImported = 0;
            mobsImported = 0;
            whereamiFound = 0;
            errors.clear();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Files processed: ").append(filesProcessed).append("\n");
            sb.append("BAT_MAPPER frames: ").append(batMapperFrames).append("\n");
            sb.append("Rooms imported: ").append(roomsImported).append("\n");
            sb.append("Mobs imported: ").append(mobsImported).append("\n");
            sb.append("Whereami found: ").append(whereamiFound).append("\n");
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                errors.forEach(e -> sb.append("  - ").append(e).append("\n"));
            }
            return sb.toString();
        }
    }
}
