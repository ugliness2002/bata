package bata.dao;

import bata.model.Mob;
import org.jdbi.v3.core.Jdbi;

public class MobDao {
    private final Jdbi jdbi;
    public MobDao(Jdbi jdbi) { this.jdbi = jdbi; }

    public void upsertMob(Mob mob) {
        this.jdbi.withHandle(handle -> {
            handle.execute("INSERT INTO mobs (room_id, name, aggressive) VALUES (?, ?, ?) ON CONFLICT (room_id, name) DO UPDATE SET aggressive = excluded.aggressive",
                mob.getRoomId(), mob.getLongName(), mob.isAggro() ? 1 : 0);
            return null;
        });
    }
}
