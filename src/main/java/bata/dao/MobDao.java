package bata.dao;

import bata.model.Mob;
import org.jdbi.v3.core.Jdbi;

public class MobDao {
    private final Jdbi jdbi;

    public MobDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void upsertMob(Mob mob) {
        this.jdbi.withHandle(handle -> {
            handle.execute("INSERT INTO mobs (id, long_name, is_aggro) VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET long_name = excluded.long_name, is_aggro = excluded.is_aggro",
                    mob.getId(),
                    mob.getLongName(),
                    mob.isAggro() ? 1 : 0
            );
            return null;
        });
    }
}
