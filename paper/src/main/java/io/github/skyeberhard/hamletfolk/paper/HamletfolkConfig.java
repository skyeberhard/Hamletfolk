package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.WorldFilter;
import org.bukkit.configuration.ConfigurationSection;

record HamletfolkConfig(int settlementRadius, int maxCatchUpDays, boolean showNames, WorldFilter worlds) {

    static HamletfolkConfig from(ConfigurationSection config) {
        return new HamletfolkConfig(
                Math.max(16, config.getInt("settlement-radius", 96)),
                Math.max(1, config.getInt("max-catch-up-days", 60)),
                config.getBoolean("show-names", true),
                new WorldFilter(config.getStringList("worlds.allow"), config.getStringList("worlds.deny")));
    }
}
