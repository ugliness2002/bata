package bata.dao;

import bata.model.Mob;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobDao {
    private final Jdbi jdbi;

    public MobDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void upsertMob(Mob mob) {
        this.jdbi.withHandle(handle -> {
            handle.createUpdate("INSERT INTO mobs (id, long_name, is_aggro) VALUES (:id, :name, :aggro) " +
                               "ON CONFLICT (id, long_name) DO UPDATE SET is_aggro = EXCLUDED.is_aggro")
                .bind("id", mob.getId())
                .bind("name", mob.getLongName())
                .bind("aggro", mob.isAggro() ? 1 : 0)
                .execute();
            return null;
        });
    }

    public List<Map<String, String>> searchByShort(String searchFor) {
        return this.jdbi.withHandle(handle ->
            handle.createQuery(
                "SELECT m.long_name, m.is_aggro, r.name AS room_name, r.area " +
                "FROM mobs m LEFT JOIN rooms r ON m.id = r.id " +
                "WHERE LOWER(m.long_name) LIKE :searchFor " +
                "ORDER BY m.long_name LIMIT 16")
                .bind("searchFor", "%" + searchFor.toLowerCase() + "%")
                .map((rs, ctx) -> {
                    Map<String, String> row = new HashMap<>();
                    row.put("long_name", rs.getString("long_name"));
                    row.put("is_aggro", String.valueOf(rs.getInt("is_aggro")));
                    row.put("room_name", rs.getString("room_name"));
                    row.put("area", rs.getString("area"));
                    return row;
                })
                .list()
        );
    }
}
