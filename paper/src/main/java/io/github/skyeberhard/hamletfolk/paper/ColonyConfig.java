package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Colonies;
import org.bukkit.configuration.file.FileConfiguration;

/** R10.1: the daughter-village settings from the config (see {@code colonies:} in config.yml). */
final class ColonyConfig {
    private ColonyConfig() {
    }

    static Colonies.Rules rules(FileConfiguration config) {
        Colonies.Rules d = Colonies.defaults();
        return new Colonies.Rules(
                config.getInt("colonies.min-population", d.minPopulation()),
                config.getInt("colonies.min-left-behind", d.minLeft()),
                config.getInt("colonies.min-party", d.minParty()),
                config.getInt("colonies.max-party", d.maxParty()),
                config.getInt("colonies.cooldown-days", d.cooldownDays()),
                config.getInt("colonies.max-villages-per-world", d.maxVillagesInWorld()),
                config.getInt("colonies.min-spacing", d.minSpacing()),
                config.getInt("colonies.treasury-gift", d.treasuryGift()));
    }
}
