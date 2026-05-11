package bata.model;

import bata.protocol.MapperFrameIndex;

public class Room {
    private final String id, area, shortdesc, longdesc, exits;
    private final boolean indoors;

    public Room(String id, String area, String shortdesc, String longdesc, String exits, boolean indoors) {
        this.id = id; this.area = area; this.shortdesc = shortdesc;
        this.longdesc = longdesc; this.exits = exits; this.indoors = indoors;
    }

    public String getId() { return id; }
    public String getArea() { return area; }
    public String getName() { return shortdesc; }
    public String getDescription() { return longdesc; }
    public String getExits() { return exits; }
    public boolean isIndoor() { return indoors; }
    public boolean isIndoors() { return indoors; }

    public static Room fromFrame(String frame) {
        String[] parts = frame.split(";;", -1);
        if (parts.length < MapperFrameIndex.values().length) return null;
        if (!parts[MapperFrameIndex.HEAD.ordinal()].equals("BAT_MAPPER")) return null;
        if (parts[MapperFrameIndex.AREA.ordinal()].equals("REALM_MAP")) return null;
        return new Room(
            parts[MapperFrameIndex.ROOM_ID.ordinal()],
            parts[MapperFrameIndex.AREA.ordinal()],
            parts[MapperFrameIndex.ROOM_NAME.ordinal()],
            parts[MapperFrameIndex.ROOM_DESCRIPTION.ordinal()],
            parts[MapperFrameIndex.EXITS.ordinal()],
            parts[MapperFrameIndex.IS_INDOOR.ordinal()].equals("1")
        );
    }
}
