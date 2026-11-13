package net.vortexpvp.plus;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * plugins/VortexPvP/plus/config.yml: one switch per system plus its settings. Missing keys are written with their
 * default the first time they are read, so the file always lists everything that can be changed.
 */
public final class PlusConfig {
    private final File file;
    private YamlConfiguration y;
    private boolean dirty;

    public PlusConfig(File dir) {
        this.file = new File(dir, "config.yml");
        this.reload();
    }

    public void reload() {
        this.y = YamlConfiguration.loadConfiguration(this.file);
        this.y.options().setHeader(List.of(
            "VortexPvP add-on systems. Every system can be switched off with its 'enabled' key.",
            "Changes apply after /plus reload (or a restart)."
        ));
    }

    public boolean on(String system) {
        return this.bool(system + ".enabled", true);
    }

    public boolean bool(String path, boolean def) {
        if (!this.y.contains(path)) {
            this.y.set(path, def);
            this.dirty = true;
        }

        return this.y.getBoolean(path, def);
    }

    public int i(String path, int def) {
        if (!this.y.contains(path)) {
            this.y.set(path, def);
            this.dirty = true;
        }

        return this.y.getInt(path, def);
    }

    public double d(String path, double def) {
        if (!this.y.contains(path)) {
            this.y.set(path, def);
            this.dirty = true;
        }

        return this.y.getDouble(path, def);
    }

    public String s(String path, String def) {
        if (!this.y.contains(path)) {
            this.y.set(path, def);
            this.dirty = true;
        }

        return this.y.getString(path, def);
    }

    public List<String> list(String path, List<String> def) {
        if (!this.y.contains(path)) {
            this.y.set(path, def);
            this.dirty = true;
        }

        return this.y.getStringList(path);
    }

    public org.bukkit.configuration.ConfigurationSection section(String path) {
        return this.y.getConfigurationSection(path);
    }

    public void set(String path, Object v) {
        this.y.set(path, v);
        this.dirty = true;
        this.save();
    }

    public void save() {
        if (this.dirty) {
            this.dirty = false;
            try {
                this.y.save(this.file);
            } catch (IOException var2) {
            }
        }
    }
}
