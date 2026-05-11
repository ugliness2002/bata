package bata.model;

public class Mob {
    private String id, roomId, area;
    private String name;
    private boolean aggressive;

    public Mob(String roomId, String name, boolean aggressive) {
        this.roomId = roomId;
        this.name = name;
        this.aggressive = aggressive;
    }

    public String getId() { return id; }
    public String getRoomId() { return roomId; }
    public String getLongName() { return name; }
    public boolean isAggro() { return aggressive; }

    public void setId(String id) { this.id = id; }
}
