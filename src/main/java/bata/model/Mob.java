package bata.model;

public class Mob {
    private String id; // 房间id
    private String longName;
    private boolean isAggro;

    public Mob(String id, String longName, boolean isAggro) {
        this.id = id;
        this.longName = longName;
        this.isAggro = isAggro;
    }

    public String getId() {
        return id;
    }

    public String getLongName() {
        return longName;
    }

    public boolean isAggro() {
        return isAggro;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public void setAggro(boolean aggro) {
        isAggro = aggro;
    }
}
