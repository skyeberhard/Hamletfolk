package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.WorldFilter;
import org.bukkit.configuration.ConfigurationSection;

record HamletfolkConfig(int settlementRadius, int maxCatchUpDays, boolean showNames, boolean oldAgeDeaths, boolean appearanceTypes,
                       double lifespanScale, boolean pricesFollowSupply, WorldFilter worlds) {

    /** How much longer than the base 60 / 90-110 days a life lasts: 20 is roughly a month of real time. */
    static final double DEFAULT_LIFESPAN_SCALE = 20;

    static HamletfolkConfig from(ConfigurationSection config) {
        return new HamletfolkConfig(
                Math.max(16, config.getInt("settlement-radius", 96)),
                Math.max(1, config.getInt("max-catch-up-days", 60)),
                config.getBoolean("show-names", true),
                config.getBoolean("aging.old-age-deaths", false),
                config.getBoolean("appearance.villager-type", false),
                config.getDouble("aging.lifespan-scale", DEFAULT_LIFESPAN_SCALE),
                config.getBoolean("prices.follow-supply", true),
                new WorldFilter(config.getStringList("worlds.allow"), config.getStringList("worlds.deny")));
    }
}
