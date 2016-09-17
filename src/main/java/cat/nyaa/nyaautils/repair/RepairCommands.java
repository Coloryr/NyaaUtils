package cat.nyaa.nyaautils.repair;

import cat.nyaa.nyaautils.I18n;
import cat.nyaa.nyaautils.NyaaUtils;
import cat.nyaa.utils.CommandReceiver;
import cat.nyaa.utils.Internationalization;
import cat.nyaa.utils.Message;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Repairable;

import static cat.nyaa.nyaautils.repair.RepairInstance.RepairStat.REPAIRABLE;
import static cat.nyaa.nyaautils.repair.RepairInstance.RepairStat.UNREPAIRABLE;

public class RepairCommands extends CommandReceiver<NyaaUtils> {
    private final NyaaUtils plugin;

    public RepairCommands(Object plugin, Internationalization i18n) {
        super((NyaaUtils) plugin, i18n);
        this.plugin = (NyaaUtils) plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "repair";
    }

    @SubCommand(value = "add", permission = "nu.addrepair")
    public void addRepairItem(CommandSender sender, Arguments args) {
        Material tool = args.nextEnum(Material.class);
        Material material = args.nextEnum(Material.class);
        int fullAmount = args.nextInt();
        int xpConsumption = args.nextInt();
        double enchantCost = args.nextDouble();
        int repairLimit = args.nextInt();
        RepairConfig.RepairConfigItem item = new RepairConfig.RepairConfigItem();
        item.material = material;
        item.fullRepairCost = fullAmount;
        item.expCost = xpConsumption;
        item.enchantCostPerLv = enchantCost;
        item.repairLimit = repairLimit;
        plugin.cfg.repair.addItem(tool, item);
        msg(sender, "user.repair.item_added");
    }

    @SubCommand(value = "info", permission = "nu.repair")
    public void repairInfo(CommandSender sender, Arguments args) {
        ItemStack item = getItemInHand(sender);
        RepairInstance info = new RepairInstance(item, plugin.cfg.repair);
        new Message(I18n._("user.repair.info_1")).append(item).send(asPlayer(sender));
        msg(sender, "user.repair.info_2", item.getType().name());
        if (info.stat != REPAIRABLE) {
            msg(sender, "user.repair.info_3", I18n._("user.repair.unrepairable." + info.stat.name()));
        }
        if (info.stat == UNREPAIRABLE) return;
        int fullDur = item.getType().getMaxDurability();
        int currDur = fullDur - item.getDurability();
        msg(sender, "user.repair.info_4", currDur, fullDur, (double) currDur / (double) fullDur * 100);
        new Message(I18n._("user.repair.info_5")).append(new ItemStack(info.repairMaterial)).send(asPlayer(sender));
        msg(sender, "user.repair.info_6", info.expConsumption);
        msg(sender, "user.repair.info_7", info.durRecovered, (double) info.durRecovered / (double) fullDur * 100);
        if (info.repairLimit <= 0) {
            msg(sender, "user.repair.info_8");
        } else {
            int repairTime = ((Repairable) item.getItemMeta()).getRepairCost();
            msg(sender, "user.repair.info_9", repairTime, info.repairLimit);
        }
        if (info.stat == REPAIRABLE) {
            msg(sender, "user.repair.info_10", (int)Math.ceil(item.getDurability()/(double)info.durRecovered));
        }
    }

    @SubCommand(value = "hand", permission = "nu.repair")
    public void repairHand(CommandSender sender, Arguments args) {
        ItemStack item = getItemInHand(sender);
        ItemStack material = getItemInOffHand(sender);
        RepairInstance info = new RepairInstance(item, plugin.cfg.repair);
        if (info.stat != REPAIRABLE) {
            msg(sender, "user.repair.info_3", I18n._("user.repair.unrepairable." + info.stat.name()));
            return;
        }
        if (material.getType() != info.repairMaterial || material.getAmount() <= 0) {
            msg(sender, "user.repair.material_mismatch");
            return;
        }
        Player p = asPlayer(sender);
        if (p.getTotalExperience() < info.expConsumption) {
            msg(sender, "user.repair.no_enough_exp");
            return;
        }

        addPlayerExperience(p, -info.expConsumption);

        int dur = item.getDurability();
        dur -= info.durRecovered;
        if (dur < 0) dur = 0;
        item.setDurability((short) dur);
        p.getInventory().setItemInMainHand(item);
        int count = material.getAmount();
        if (count <= 1) {
            p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            material.setAmount(count - 1);
            p.getInventory().setItemInOffHand(material);
        }
        msg(p, "user.repair.repaired");
    }

    @SubCommand(value = "full", permission = "nu.repair")
    public void repairFull(CommandSender sender, Arguments args) {
        ItemStack item = getItemInHand(sender);
        ItemStack material = getItemInOffHand(sender);
        RepairInstance info = new RepairInstance(item, plugin.cfg.repair);
        if (info.stat != REPAIRABLE) {
            msg(sender, "user.repair.info_3", I18n._("user.repair.unrepairable." + info.stat.name()));
            return;
        }
        if (material.getType() != info.repairMaterial || material.getAmount() <= 0) {
            msg(sender, "user.repair.material_mismatch");
            return;
        }
        Player p = asPlayer(sender);

        int expMax = (int)Math.floor(p.getTotalExperience()/(double)info.expConsumption);
        int materialMax = material.getAmount();
        int durMax = (int)Math.ceil(item.getDurability()/(double)info.durRecovered);
        int repairAmount = Math.min(Math.min(expMax, materialMax), durMax);

        addPlayerExperience(p, -info.expConsumption*repairAmount);
        int dur = item.getDurability();
        dur -= info.durRecovered*repairAmount;
        if (dur < 0) dur = 0;
        item.setDurability((short) dur);
        p.getInventory().setItemInMainHand(item);
        int count = material.getAmount() - repairAmount;
        if (count <= 0) {
            p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            material.setAmount(count);
            p.getInventory().setItemInOffHand(material);
        }
        msg(p, "user.repair.repaired");
    }

    public static void addPlayerExperience(Player p, int exp) {
        if (exp > 0) {
            p.giveExp(exp);
        } else if (exp < 0) {
            exp = -exp;
            int totalExp = p.getTotalExperience();
            if (totalExp < exp) throw new IllegalArgumentException("Negative Exp Left");
            totalExp -= exp;
            int currentLevelExp = (int)(p.getExpToLevel() * p.getExp());
            while(exp > 0) {
                if (currentLevelExp <= 0) {
                    if (p.getLevel() <= 0) return;
                    p.setLevel(p.getLevel()-1);
                    currentLevelExp = p.getExpToLevel();
                }
                if (exp > currentLevelExp) {
                    exp -= currentLevelExp;
                    currentLevelExp = 0;
                } else {
                    currentLevelExp -= exp;
                    exp = 0;
                }
            }
            p.setExp(currentLevelExp/(float)p.getExpToLevel());
            p.setTotalExperience(totalExp);
        }
    }
}
