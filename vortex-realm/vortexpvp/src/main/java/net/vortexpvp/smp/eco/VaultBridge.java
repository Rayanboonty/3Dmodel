package net.vortexpvp.smp.eco;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.smp.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

/**
 * Connects the built-in economy to Vault without a compile dependency: our balances are offered to other plugins as a
 * Vault economy (a {@link Proxy} of Vault's interface), and when another plugin already provides one (EssentialsX,
 * CMI, a DonutSMP-style economy...) the built-in economy uses that one instead, so every plugin sees the same money.
 * <ul>
 * <li>{@code provider: auto} - ours at the lowest priority; another provider wins and is used by us too.</li>
 * <li>{@code provider: builtin} - ours at the highest priority, always used.</li>
 * <li>{@code provider: external} - never register ours; use the other provider when there is one.</li>
 * </ul>
 */
public final class VaultBridge implements Listener {
    private final VortexPvP plugin;
    private final Economy eco;
    private final String mode;
    private final Class<?> api;
    private final Constructor<?> response;
    private final Class<?> responseType;
    private Object ours;
    private Object external;
    private Method extBalance;
    private Method extDeposit;
    private Method extWithdraw;
    private Method extHas;
    private Method extFormat;
    private Method success;

    private VaultBridge(VortexPvP plugin, Economy eco, String mode, Class<?> api) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.eco = eco;
        this.mode = mode;
        this.api = api;
        Class<?> resp = Class.forName("net.milkbowl.vault.economy.EconomyResponse", true, api.getClassLoader());
        this.responseType = Class.forName("net.milkbowl.vault.economy.EconomyResponse$ResponseType", true, api.getClassLoader());
        this.response = resp.getConstructor(double.class, double.class, this.responseType, String.class);
        this.success = resp.getMethod("transactionSuccess");
    }

    /** Hooks into Vault when it is installed (null otherwise). */
    public static VaultBridge hook(VortexPvP plugin, Economy eco, String mode) {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null) {
            return null;
        } else {
            try {
                Class<?> api = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
                VaultBridge b = new VaultBridge(plugin, eco, mode.toLowerCase(Locale.ROOT), api);
                b.register();
                b.pickExternal();
                Bukkit.getPluginManager().registerEvents(b, plugin);
                return b;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException var5) {
                plugin.getLogger().log(Level.WARNING, "Vault is installed but its economy API could not be hooked.", var5);
                return null;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void register() {
        if (!this.mode.equals("external")) {
            this.ours = Proxy.newProxyInstance(this.api.getClassLoader(), new Class<?>[]{this.api}, new VaultBridge.Handler());
            ServicePriority prio = this.mode.equals("builtin") ? ServicePriority.Highest : ServicePriority.Lowest;
            Bukkit.getServicesManager().register((Class)this.api, this.ours, this.plugin, prio);
            this.plugin.getLogger().info("Vault: built-in economy registered (" + prio + " priority).");
        }
    }

    /** The provider other than ours that Vault hands out, if any (auto / external modes). */
    private void pickExternal() {
        Object found = null;
        if (!this.mode.equals("builtin")) {
            RegisteredServiceProvider<?> best = null;

            for (RegisteredServiceProvider<?> r : Bukkit.getServicesManager().getRegistrations(this.api)) {
                if (r.getProvider() != this.ours && (best == null || r.getPriority().compareTo(best.getPriority()) > 0)) {
                    best = r;
                }
            }

            found = best == null ? null : best.getProvider();
        }

        if (found != this.external) {
            this.external = found;

            try {
                if (found != null) {
                    this.extBalance = this.api.getMethod("getBalance", OfflinePlayer.class);
                    this.extDeposit = this.api.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                    this.extWithdraw = this.api.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                    this.extHas = this.api.getMethod("has", OfflinePlayer.class, double.class);
                    this.extFormat = this.api.getMethod("format", double.class);
                    this.plugin.getLogger().info("Vault: using the economy of " + this.extName() + " for SMP money.");
                }
            } catch (NoSuchMethodException var4) {
                this.external = null;
                this.plugin.getLogger().warning("Vault: the other economy is missing methods, using the built-in one.");
            }
        }
    }

    private String extName() {
        try {
            return String.valueOf(this.api.getMethod("getName").invoke(this.external));
        } catch (ReflectiveOperationException | RuntimeException var2) {
            return "another plugin";
        }
    }

    @EventHandler
    public void onRegister(ServiceRegisterEvent e) {
        if (e.getProvider().getService() == this.api) {
            this.pickExternal();
        }
    }

    @EventHandler
    public void onUnregister(ServiceUnregisterEvent e) {
        if (e.getProvider().getService() == this.api) {
            this.pickExternal();
        }
    }

    public void unregister() {
        if (this.ours != null) {
            Bukkit.getServicesManager().unregister(this.api, this.ours);
            this.ours = null;
        }
    }

    /** True while another plugin's economy holds the money. */
    public boolean external() {
        return this.external != null;
    }

    public String status() {
        return this.external != null ? "external (" + this.extName() + ")" : (this.ours != null ? "built-in, offered to Vault" : "built-in");
    }

    public double balance(UUID u) {
        try {
            return ((Number)this.extBalance.invoke(this.external, Bukkit.getOfflinePlayer(u))).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException var3) {
            return 0.0;
        }
    }

    public boolean has(UUID u, double v) {
        try {
            return (Boolean)this.extHas.invoke(this.external, Bukkit.getOfflinePlayer(u), v);
        } catch (ReflectiveOperationException | RuntimeException var5) {
            return false;
        }
    }

    public boolean deposit(UUID u, double v) {
        return this.call(this.extDeposit, u, v);
    }

    public boolean withdraw(UUID u, double v) {
        return this.call(this.extWithdraw, u, v);
    }

    public String format(double v) {
        try {
            return String.valueOf(this.extFormat.invoke(this.external, v));
        } catch (ReflectiveOperationException | RuntimeException var4) {
            return null;
        }
    }

    private boolean call(Method m, UUID u, double v) {
        try {
            Object r = m.invoke(this.external, Bukkit.getOfflinePlayer(u), v);
            return r != null && (Boolean)this.success.invoke(r);
        } catch (ReflectiveOperationException | RuntimeException var6) {
            this.plugin.getLogger().log(Level.FINE, "Vault call failed", var6);
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object respond(double amount, double balance, String type, String error) throws ReflectiveOperationException {
        return this.response.newInstance(amount, balance, Enum.valueOf((Class)this.responseType, type), error);
    }

    /** Vault passes players as OfflinePlayer or by name. */
    private static UUID who(Object o) {
        if (o instanceof OfflinePlayer op) {
            return op.getUniqueId();
        } else if (o instanceof String name) {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                return online.getUniqueId();
            } else {
                OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
                return cached == null ? null : cached.getUniqueId();
            }
        } else {
            return null;
        }
    }

    private static double amount(Object[] args) {
        return args != null && args.length > 0 && args[args.length - 1] instanceof Double d ? d : 0.0;
    }

    /** Our side of Vault's interface, answered from the built-in balances. */
    private final class Handler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Economy e = VaultBridge.this.eco;
            UUID u = args != null && args.length > 0 ? who(args[0]) : null;
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "VortexRealm economy";
                case "isEnabled":
                    return VaultBridge.this.plugin.isEnabled();
                case "getName":
                    return VaultBridge.this.plugin.getName();
                case "hasBankSupport":
                    return false;
                case "fractionalDigits":
                    return 2;
                case "format":
                    return e.builtinMoney((Double)args[0]);
                case "currencyNamePlural":
                    return "Dollars";
                case "currencyNameSingular":
                    return "Dollar";
                case "hasAccount":
                case "createPlayerAccount":
                    return u != null;
                case "getBalance":
                    return u == null ? 0.0 : e.builtinBalance(u);
                case "has":
                    return u != null && e.builtinBalance(u) >= amount(args) - 1.0E-9;
                case "withdrawPlayer": {
                    double v = amount(args);
                    if (u == null) {
                        return VaultBridge.this.respond(v, 0.0, "FAILURE", "Unknown player");
                    } else if (v < 0.0) {
                        return VaultBridge.this.respond(v, e.builtinBalance(u), "FAILURE", "Cannot withdraw negative funds");
                    } else if (!e.builtinTake(u, v)) {
                        return VaultBridge.this.respond(v, e.builtinBalance(u), "FAILURE", "Insufficient funds");
                    } else {
                        return VaultBridge.this.respond(v, e.builtinBalance(u), "SUCCESS", null);
                    }
                }
                case "depositPlayer": {
                    double v = amount(args);
                    if (u == null) {
                        return VaultBridge.this.respond(v, 0.0, "FAILURE", "Unknown player");
                    } else if (v < 0.0) {
                        return VaultBridge.this.respond(v, e.builtinBalance(u), "FAILURE", "Cannot deposit negative funds");
                    } else {
                        e.builtinGive(u, v);
                        return VaultBridge.this.respond(v, e.builtinBalance(u), "SUCCESS", null);
                    }
                }
                case "getBanks":
                    return List.of();
                default:
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    } else if (method.getReturnType() == double.class) {
                        return 0.0;
                    } else {
                        return VaultBridge.this.respond(0.0, 0.0, "NOT_IMPLEMENTED", "Banks are not supported");
                    }
            }
        }
    }
}
