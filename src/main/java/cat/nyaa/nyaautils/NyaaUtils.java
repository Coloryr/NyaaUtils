package cat.nyaa.nyaautils;

import cat.nyaa.nyaautils.commandwarpper.Teleport;
import cat.nyaa.nyaautils.dropprotect.DropProtectListener;
import cat.nyaa.nyaautils.elytra.ElytraEnhanceListener;
import cat.nyaa.nyaautils.elytra.FuelManager;
import cat.nyaa.nyaautils.exhibition.ExhibitionListener;
import cat.nyaa.nyaautils.lootprotect.LootProtectListener;
import cat.nyaa.nyaautils.mailbox.MailboxListener;
import cat.nyaa.nyaautils.particle.ParticleListener;
import cat.nyaa.nyaautils.particle.ParticleTask;
import cat.nyaa.nyaautils.realm.RealmListener;
import cat.nyaa.nyaautils.signedit.SignEditListener;
import cat.nyaa.nyaautils.timer.TimerListener;
import cat.nyaa.nyaautils.timer.TimerManager;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import net.ess3.api.IEssentials;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class NyaaUtils extends JavaPlugin {
    public static NyaaUtils instance;
    public static boolean hasHEH = false;
    public I18n i18n;
    public CommandHandler commandHandler;
    public Configuration cfg;
    public LootProtectListener lpListener;
    public DropProtectListener dpListener;
    public DamageStatListener dsListener;
    public ExhibitionListener exhibitionListener;
    public MailboxListener mailboxListener;
    public ElytraEnhanceListener elytraEnhanceListener;
    public Teleport teleport;
    public FuelManager fuelManager;
    public TimerManager timerManager;
    public TimerListener timerListener;
    public WorldEditPlugin worldEditPlugin;
    public RealmListener realmListener;
    public IEssentials ess;
    public ParticleListener particleListener;
    public ParticleTask particleTask;
    public SignEditListener signEditListener;

    @Override
    public void onEnable() {
        instance = this;
        cfg = new Configuration(this);
        cfg.load();
        i18n = new I18n(this, cfg.language);
        commandHandler = new CommandHandler(this, i18n);
        getCommand("nyaautils").setExecutor(commandHandler);
        getCommand("nyaautils").setTabCompleter((TabCompleter) commandHandler);
        lpListener = new LootProtectListener(this);
        dpListener = new DropProtectListener(this);
        dsListener = new DamageStatListener(this);
        elytraEnhanceListener = new ElytraEnhanceListener(this);
        teleport = new Teleport(this);
        exhibitionListener = new ExhibitionListener(this);
        mailboxListener = new MailboxListener(this);
        fuelManager = new FuelManager(this);
        timerManager = new TimerManager(this);
        timerListener = new TimerListener(this);
        worldEditPlugin = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
        realmListener = new RealmListener(this);
        hasHEH = getServer().getPluginManager().getPlugin("HamsterEcoHelper") != null;
        ess = (IEssentials) getServer().getPluginManager().getPlugin("Essentials");
        particleTask = new ParticleTask(this);
        particleListener = new ParticleListener(this);
        signEditListener = new SignEditListener(this);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getCommand("nyaautils").setExecutor(null);
        getCommand("nyaautils").setTabCompleter(null);
        HandlerList.unregisterAll(this);
        cfg.save();
    }
}
