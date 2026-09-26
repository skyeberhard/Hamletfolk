package io.github.skyeberhard.societies.paper;

import org.bukkit.configuration.ConfigurationSection;

record SocietiesConfig(int settlementRadius, int maxCatchUpDays, boolean showNames) {

    static SocietiesConfig from(ConfigurationSection config) {
        return new SocietiesConfig(
                Math.max(16, config.getInt("settlement-radius", 96)),
                Math.max(1, config.getInt("max-catch-up-days", 60)),
                config.getBoolean("show-names", true));
    }
}
