package me.clip.placeholderapi.expansion;
/** Compile-only stub of PlaceholderAPI 2.11.6 (the real plugin is used at runtime). */
public abstract class PlaceholderExpansion {
    public abstract String getIdentifier();
    public abstract String getAuthor();
    public abstract String getVersion();
    public boolean persist() { return false; }
    public boolean canRegister() { return true; }
    public boolean register() { throw new UnsupportedOperationException("stub"); }
    public boolean unregister() { throw new UnsupportedOperationException("stub"); }
    public boolean isRegistered() { return false; }
    public String onRequest(org.bukkit.OfflinePlayer player, String params) { return null; }
    public String onPlaceholderRequest(org.bukkit.entity.Player player, String params) { return null; }
}
