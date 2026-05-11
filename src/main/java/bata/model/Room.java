package bata.model;

import bata.protocol.MapperFrameIndex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Room {
    // bcproxy whereami pattern: "You are in 'X' in Y on the continent of Z. (Coordinates: Nx, My)"
    public static final Pattern WHEREAMI = Pattern.compile(
        "You are in '([^']*)' in ([^.]+) on the continent of ([^.]+)\\.\\s*\\(Coordinates:\\s*(\\d+)x,\\s*(\\d+)y"
    );

    private final String id;
    private final String area;
    private final String name;
    private final String description;
    private final String exits;
    private final boolean isIndoor;
    private final String lastMoveDir;
    private final String continent;
    private final int x;
    private final int y;

    public Room(String id, String area, String name, String description, String exits,
                boolean isIndoor, String lastMoveDir, String continent, int x, int y) {
        this.id = id;
        this.area = area;
        this.name = name;
        this.description = description;
        this.exits = exits;
        this.isIndoor = isIndoor;
        this.lastMoveDir = lastMoveDir;
        this.continent = continent;
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }
    public String getArea() { return area; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getExits() { return exits; }
    public boolean isIndoor() { return isIndoor; }
    public String getLastMoveDir() { return lastMoveDir; }
    public String getContinent() { return continent; }
    public int getX() { return x; }
    public int getY() { return y; }

    public static Room fromFrame(String frame) {
        String[] parts = frame.split(";;", -1);

        if (!parts[MapperFrameIndex.HEAD.ordinal()].equals("BAT_MAPPER")) {
            return null;
        }

        if (parts[MapperFrameIndex.AREA.ordinal()].equals("REALM_MAP")) {
            return null;
        }

        return new Room(
                parts[MapperFrameIndex.ROOM_ID.ordinal()],
                parts[MapperFrameIndex.AREA.ordinal()],
                parts[MapperFrameIndex.ROOM_NAME.ordinal()],
                parts[MapperFrameIndex.ROOM_DESCRIPTION.ordinal()],
                parts[MapperFrameIndex.EXITS.ordinal()],
                parts[MapperFrameIndex.IS_INDOOR.ordinal()].equals("1"),
                parts[MapperFrameIndex.LAST_MOVE_DIR.ordinal()],
                null,  // continent — not in BAT_MAPPER frame
                0,     // x — not in BAT_MAPPER frame
                0      // y — not in BAT_MAPPER frame
        );
    }

    /**
     * Parse whereami text to extract continent and coordinates.
     * Format: "You are in 'ShortDesc' in Area on the continent of Continent. (Coordinates: Xx, Yy)"
     * Returns null if no match.
     */
    public static WhereamiResult parseWhereami(String text) {
        Matcher m = WHEREAMI.matcher(text);
        if (m.find()) {
            return new WhereamiResult(
                m.group(3).trim(),  // continent
                Integer.parseInt(m.group(4)),  // x
                Integer.parseInt(m.group(5))   // y
            );
        }
        return null;
    }

    /**
     * Holds parsed whereami data.
     */
    public static class WhereamiResult {
        public final String continent;
        public final int x;
        public final int y;

        public WhereamiResult(String continent, int x, int y) {
            this.continent = continent;
            this.x = x;
            this.y = y;
        }
    }
}
