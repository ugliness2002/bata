package bata.protocol;

import bata.model.Mob;
import bata.model.Room;
import bata.dao.MobDao;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MobRecorder {
    // bcproxy-style regex: match ANSI-colored mob names
    // Green (passive): ESC[1;32m...name...ESC[0m or ESC[32m...name...ESC[0m
    // Red (aggressive): ESC[1;31m...name...ESC[0m or ESC[31m...name...ESC[0m
    private static final Pattern MOB_GREEN = Pattern.compile("\\x1b\\[(?:1;)?32m([^\\x1b]+)\\x1b\\[0m");
    private static final Pattern MOB_RED   = Pattern.compile("\\x1b\\[(?:1;)?31m([^\\x1b]+)\\x1b\\[0m");
    // Non-colored mob lines: "a/an <name> is/are <action> here"
    private static final Pattern MOB_PLAIN = Pattern.compile("^\\s*(a|an)\\s+(.+?)\\s+(is|are)\\s+.+\\s+here\\s*$");

    private final MobDao mobDao;

    public MobRecorder(MobDao mobDao) {
        this.mobDao = mobDao;
    }

    public void recordMobs(String text, Room room) {
        if (text == null || text.isEmpty()) {
            return;
        }

        String[] lines = text.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Try green (passive) mobs first
            Matcher greenMatcher = MOB_GREEN.matcher(line);
            while (greenMatcher.find()) {
                String name = greenMatcher.group(1).trim();
                if (name.length() > 3) {
                    Mob mob = new Mob(room.getId(), name, false);
                    mobDao.upsertMob(mob);
                }
            }

            // Try red (aggressive) mobs
            Matcher redMatcher = MOB_RED.matcher(line);
            while (redMatcher.find()) {
                String name = redMatcher.group(1).trim();
                if (name.length() > 3) {
                    Mob mob = new Mob(room.getId(), name, true);
                    mobDao.upsertMob(mob);
                }
            }

            // Try non-colored mob descriptions
            Matcher plainMatcher = MOB_PLAIN.matcher(line);
            if (plainMatcher.find()) {
                String article = plainMatcher.group(1); // "a" or "an"
                String name = plainMatcher.group(2).trim();
                if (name.length() > 3) {
                    String longName = article + " " + name;
                    Mob mob = new Mob(room.getId(), longName, false);
                    mobDao.upsertMob(mob);
                }
            }
        }
    }
}
