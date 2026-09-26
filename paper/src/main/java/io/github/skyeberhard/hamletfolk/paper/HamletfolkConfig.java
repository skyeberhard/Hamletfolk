package io.github.skyeberhard.hamletfolk.paper;

import org.bukkit.configuration.ConfigurationSection;

record HamletfolkConfig(int settlementRadius, int maxCatchUpDays, boolean showNames) {

    static HamletfolkConfig from(ConfigurationSection config) {
        return new HamletfolkConfig(
                Math.max(16, config.getInt("settlement-radius", 96)),
                Math.max(1, config.getInt("max-catch-up-days", 60)),
                config.getBoolean("show-names", true));
    }
}
