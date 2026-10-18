package io.github.skyeberhard.hamletfolk.paper;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * R1.53: how a player is named in what villagers say and in a village's history, which any player can read. Bedrock players come through
 * Floodgate with a leading "." on their names (".Sam"); it is dropped here so a villager says "Sam". And with
 * {@code history.misdeeds-name-players: false} the history says "a visitor" for the things people would rather not have written beside their name
 * (taking from the stores, breaking a barrel, killing a resident); gifts and donations are always named.
 */
final class PlayerNames {
    private static volatile boolean stripPrefix = true;
    private static volatile boolean misdeeds = true;

    private PlayerNames() {
    }

    static void configure(FileConfiguration config) {
        stripPrefix = config.getBoolean("names.strip-bedrock-prefix", true);
        misdeeds = config.getBoolean("history.misdeeds-name-players", true);
    }

    /** The name a villager uses. */
    static String shown(String name) {
        if (name == null) {
            return "someone";
        }
        return stripPrefix && name.length() > 1 && name.startsWith(".") ? name.substring(1) : name;
    }

    static String shown(OfflinePlayer player) {
        return shown(player.getName());
    }

    /** The name a history line uses for a misdeed. */
    static String forMisdeed(String name) {
        return misdeeds ? shown(name) : "a visitor";
    }
}
