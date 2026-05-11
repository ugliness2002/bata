package bata.dao;

import bata.model.Room;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.MapMapper;

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
            handle.execute("INSERT INTO rooms (id, area, name, description, exits, last_move_dir, is_indoor, continent, x, y) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                           "ON CONFLICT (id) DO UPDATE SET " +
                           "area = excluded.area, " +
                           "name = excluded.name, " +
                           "description = excluded.description, " +
                           "exits = excluded.exits, " +
                           "last_move_dir = excluded.last_move_dir, " +
                           "is_indoor = excluded.is_indoor, " +
                           "continent = COALESCE(excluded.continent, rooms.continent), " +
                           "x = CASE WHEN excluded.x != 0 THEN excluded.x ELSE rooms.x END, " +
                           "y = CASE WHEN excluded.y != 0 THEN excluded.y ELSE rooms.y END",
                    room.getId(),
                    room.getArea(),
                    room.getName(),
                    room.getDescription(),
                    room.getExits(),
                    room.getLastMoveDir(),
                    room.isIndoor(),
                    room.getContinent(),
                    room.getX(),
                    room.getY()
            );
            return null;
        });
    }

    public List<Map<String, String>> searchByShort(String searchFor) {
        List<Map<String, Object>> result = this.jdbi.withHandle(handle ->
                handle.createQuery("SELECT name, area FROM rooms WHERE LOWER(name) LIKE :searchFor LIMIT 16")
                        .bind("searchFor", String.format("%%%s%%", searchFor.toLowerCase()))
                        .map(new MapMapper())
                        .list()
        );

        List<Map<String, String>> rows = new ArrayList<>(result.size());
        result.forEach(m -> {
            Map<String, String> row = new HashMap<>(m.size());
            m.forEach((k, v) -> row.put(k, v.toString()));
            rows.add(row);
        });

        return rows;
    }
}
