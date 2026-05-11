package bata.dao;

import bata.model.Room;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomDao {
    private final Jdbi jdbi;
    public RoomDao(Jdbi jdbi) { this.jdbi = jdbi; }

    public void upsertRoom(Room room) {
        this.jdbi.withHandle(handle -> {
            handle.execute("INSERT INTO rooms (id, area, shortdesc, longdesc, exits, indoors) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                room.getId(), room.getArea(), room.getName(), room.getDescription(), room.getExits(), room.isIndoors());
            return null;
        });
    }

    public List<Map<String, String>> searchByShort(String searchFor) {
        return this.jdbi.withHandle(handle ->
            handle.createQuery(
                "SELECT shortdesc, area, exits, indoors FROM rooms WHERE LOWER(shortdesc) LIKE :q ORDER BY area, shortdesc LIMIT 16")
                .bind("q", "%" + searchFor.toLowerCase() + "%")
                .map((rs, ctx) -> {
                    Map<String, String> row = new HashMap<>();
                    row.put("name", rs.getString("shortdesc"));
                    row.put("area", rs.getString("area"));
                    row.put("exits", rs.getString("exits"));
                    row.put("indoors", String.valueOf(rs.getBoolean("indoors")));
                    return row;
                }).list());
    }
}
