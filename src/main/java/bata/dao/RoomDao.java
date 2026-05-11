package bata.dao;

import bata.model.Room;
import org.jdbi.v3.core.Jdbi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomDao {
    private final Jdbi jdbi;

    public RoomDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void upsertRoom(Room room) {
        this.jdbi.withHandle(handle -> {
            handle.createUpdate("INSERT INTO rooms (id, area, name, description, exits, last_move_dir, is_indoor, continent, x, y) " +
                               "VALUES (:id, :area, :name, :desc, :exits, :dir, :indoor, :continent, :x, :y) " +
                               "ON CONFLICT (id) DO UPDATE SET " +
                               "area = EXCLUDED.area, " +
                               "name = EXCLUDED.name, " +
                               "description = EXCLUDED.description, " +
                               "exits = EXCLUDED.exits, " +
                               "last_move_dir = EXCLUDED.last_move_dir, " +
                               "is_indoor = EXCLUDED.is_indoor, " +
                               "continent = COALESCE(EXCLUDED.continent, rooms.continent), " +
                               "x = CASE WHEN EXCLUDED.x != 0 THEN EXCLUDED.x ELSE rooms.x END, " +
                               "y = CASE WHEN EXCLUDED.y != 0 THEN EXCLUDED.y ELSE rooms.y END")
                .bind("id", room.getId())
                .bind("area", room.getArea())
                .bind("name", room.getName())
                .bind("desc", room.getDescription())
                .bind("exits", room.getExits())
                .bind("dir", room.getLastMoveDir())
                .bind("indoor", room.isIndoor())
                .bind("continent", room.getContinent())
                .bind("x", room.getX())
                .bind("y", room.getY())
                .execute();
            return null;
        });
    }

    public List<Map<String, String>> searchByShort(String searchFor) {
        return this.jdbi.withHandle(handle ->
            handle.createQuery(
                "SELECT name, area, continent, x, y, exits, is_indoor " +
                "FROM rooms WHERE LOWER(name) LIKE :searchFor " +
                "ORDER BY area, name LIMIT 16")
                .bind("searchFor", "%" + searchFor.toLowerCase() + "%")
                .map((rs, ctx) -> {
                    Map<String, String> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("area", rs.getString("area"));
                    row.put("continent", rs.getString("continent"));
                    row.put("x", String.valueOf(rs.getInt("x")));
                    row.put("y", String.valueOf(rs.getInt("y")));
                    row.put("exits", rs.getString("exits"));
                    row.put("is_indoor", String.valueOf(rs.getBoolean("is_indoor")));
                    return row;
                })
                .list()
        );
    }
}
