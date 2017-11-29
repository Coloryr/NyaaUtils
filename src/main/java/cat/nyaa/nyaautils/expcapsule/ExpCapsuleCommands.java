package cat.nyaa.nyaautils.expcapsule;

import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.ILocalizer;
import cat.nyaa.nyaacore.utils.ExperienceUtils;
import cat.nyaa.nyaautils.I18n;
import cat.nyaa.nyaautils.NyaaUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ExpCapsuleCommands extends CommandReceiver {
    private final NyaaUtils plugin;

    public ExpCapsuleCommands(NyaaUtils plugin, ILocalizer i18n) {
        super(plugin, i18n);
        this.plugin = plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "expcap";
    }

    @SubCommand(value = "store", permission = "nu.expcap.store")
    public void cmdStoreExp(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        if (args.top() == null) {
            p.sendMessage(I18n.format("user.expcap.current_exp", ExperienceUtils.getExpPoints(p)));
            return;
        }
        int amount = args.nextInt();
        if (amount <= 0) {
            throw new BadCommandException("user.expcap.wrong_nbr");
        }
        ItemStack item = getItemInHand(sender);
        if (item.getType() != plugin.cfg.expCapsuleType) {
            throw new BadCommandException("user.expcap.wrong_cap_type");
        }
        if (item.getAmount() > 1) {
            throw new BadCommandException("user.expcap.not_stackable");
        }
        int exp = ExperienceUtils.getExpPoints(p);
        p.sendMessage(I18n.format("user.expcap.current_exp", exp));
        if (exp < amount) {
            throw new BadCommandException("user.expcap.not_enough_exp");
        }
        Integer storedExp = getStoredExp(item);
        if (storedExp == null) storedExp = 0;
        storedExp += amount;
        setStoredExp(item, storedExp);
        ExperienceUtils.subtractExpPoints(p, amount);
    }

    @SubCommand(value = "restore", permission = "nu.expcap.restore")
    public void cmdRestoreExp(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        int amount = args.nextInt();
        if (amount <= 0) {
            throw new BadCommandException("user.expcap.wrong_nbr");
        }
        ItemStack item = getItemInHand(sender);
        if (item.getAmount() > 1) {
            throw new BadCommandException("user.expcap.not_stackable");
        }
        Integer storedExp = getStoredExp(item);
        if (storedExp == null || amount > storedExp) {
            throw new BadCommandException("user.expcap.not_enough_exp_cap");
        }
        storedExp -= amount;
        p.giveExp(amount);
        setStoredExp(item, storedExp);
    }

    public static final String EXP_CAPSULE_MAGIC = ChatColor.translateAlternateColorCodes('&', "&e&c&a&r");
    public static Integer getStoredExp(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        ItemMeta meta = item.getItemMeta();
        List<String> lores = meta.getLore();
        for (String str : lores) {
            if (str.contains(EXP_CAPSULE_MAGIC)) {
                int offset = str.lastIndexOf(EXP_CAPSULE_MAGIC) + EXP_CAPSULE_MAGIC.length();
                String rem = str.substring(offset);
                Integer exp = null;
                try {
                    exp = Integer.parseInt(rem);
                } catch (NumberFormatException ex) {
                    ex.printStackTrace();
                }
                return exp;
            }
        }
        return null;
    }

    public static void setStoredExp(ItemStack item, int exp) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        List<String> newLore = new ArrayList<>();
        for (String str : lore) {
            if (str.contains(EXP_CAPSULE_MAGIC)) continue;
            newLore.add(str);
        }
        newLore.add(I18n.format("user.expcap.contain_exp") + EXP_CAPSULE_MAGIC + Integer.toString(exp));
        meta.setLore(newLore);
        item.setItemMeta(meta);
    }
}
