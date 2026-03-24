package com.mrfenek.syncshield;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Set;

/**
 * Legacy config mapper for SyncShield.
 */
final class MigrationUtils {
    private MigrationUtils() {}

    static YamlConfiguration mapLegacyConfig(YamlConfiguration legacy) {
        YamlConfiguration out = new YamlConfiguration();
        if (legacy == null) return out;
        Set<String> keys = legacy.getKeys(true);
        for (String key : keys) {
            out.set(key, legacy.get(key));
        }
        return out;
    }
}
