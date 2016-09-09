package cat.nyaa.nyaautils.mailbox;

import cat.nyaa.nyaautils.NyaaUtils;
import cat.nyaa.utils.CommandReceiver;
import cat.nyaa.utils.Internationalization;
import me.crafter.mc.lockettepro.LocketteProAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MailboxCommands extends CommandReceiver<NyaaUtils> {
    private final NyaaUtils plugin;

    @Override
    public String getHelpPrefix() {
        return "mailbox";
    }

    public MailboxCommands(Object plugin, Internationalization i18n) {
        super((NyaaUtils) plugin, i18n);
        this.plugin = (NyaaUtils) plugin;
    }

    @SubCommand(value = "create", permission = "nu.mailbox")
    public void createMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        if (plugin.cfg.mailbox.getMailboxLocation(p.getUniqueId()) != null) {
            msg(p, "user.mailbox.already_set");
            return;
        }
        plugin.mailboxListener.registerRightClickCallback(p, 100,
                (Location clickedBlock) -> {
                    Block b = clickedBlock.getBlock();
                    if (b.getState() instanceof Chest) {
                        plugin.cfg.mailbox.updateLocationMapping(p.getUniqueId(), b.getLocation());
                        msg(p, "user.mailbox.set_success");
                        return;
                    }
                    msg(p, "user.mailbox.set_fail");
                });
        msg(p, "user.mailbox.now_right_click");
    }

    @SubCommand(value = "remove", permission = "nu.mailbox")
    public void removeMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        if (plugin.cfg.mailbox.getMailboxLocation(p.getUniqueId()) == null) {
            msg(p, "user.mailbox.havent_set_self");
            return;
        }
        plugin.cfg.mailbox.updateLocationMapping(p.getUniqueId(), null);
        msg(p, "user.mailbox.remove_success");
    }

    @SubCommand(value = "info", permission = "nu.mailbox")
    public void infoMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        Location loc = plugin.cfg.mailbox.getMailboxLocation(p.getUniqueId());
        if (loc == null) {
            msg(p, "user.mailbox.havent_set_self");
        } else {

            msg(p, "user.mailbox.info.location", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            msg(p, "user.mailbox.info.hand_price", (float) plugin.cfg.mailHandFee);
            msg(p, "user.mailbox.info.chest_price", (float) plugin.cfg.mailChestFee);
            msg(p, "user.mailbox.info.send_cooldown", ((double) plugin.cfg.mailCooldown) / 20D);
            msg(p, "user.mailbox.info.send_timeout", ((double) plugin.cfg.mailTimeout) / 20D);
        }
    }

    @SubCommand(value = "send", permission = "nu.mailbox")
    public void sendMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        ItemStack stack = getItemInHand(sender);
        String toPlayer = args.next();
        if (toPlayer == null) {
            msg(sender, "manual.mailbox.send.usage");
            return;
        }
        UUID recipient = plugin.cfg.mailbox.getUUIDbyName(toPlayer);
        Location toLocation = plugin.cfg.mailbox.getMailboxLocation(recipient);

        // Check remote mailbox
        if (recipient != null && toLocation != null) {
            Block b = toLocation.getBlock();
            if (!(b.getState() instanceof InventoryHolder)) {
                plugin.cfg.mailbox.updateLocationMapping(recipient, null);
                toLocation = null;
            }
        }

        if (recipient == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer);
            return;
        } else if (toLocation == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer);
            Player tmp = plugin.getServer().getPlayer(toPlayer);
            if (tmp != null && tmp.isOnline()) {
                msg(tmp, "user.mailbox.create_mailbox_hint", sender.getName());
            }
            return;
        }

        Player recp = plugin.getServer().getPlayer(toPlayer);
        if (recp != null && !recp.isOnline()) recp = null;
        Inventory targetInventory = ((InventoryHolder) toLocation.getBlock().getState()).getInventory();
        int slot = targetInventory.firstEmpty();
        if (slot < 0) {
            msg(sender, "user.mailbox.recipient_no_space");
            if (recp != null) {
                msg(recp, "user.mailbox.mailbox_no_space", sender.getName());
            }
        } else {
            targetInventory.setItem(slot, stack);
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            msg(sender, "user.mailbox.mail_sent", toPlayer, (float) plugin.cfg.mailHandFee);
            if (recp != null) {
                msg(recp, "user.mailbox.mail_received", sender.getName());
            }
            // TODO: log & fee
        }
    }

    @SubCommand(value = "sendchest", permission = "nu.mailbox")
    public void sendBoxMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        String toPlayer = args.next();
        if (toPlayer == null) {
            msg(sender, "manual.mailbox.sendchest.usage");
            return;
        }
        UUID recipient = plugin.cfg.mailbox.getUUIDbyName(toPlayer);
        Location toLocation = plugin.cfg.mailbox.getMailboxLocation(recipient);

        // Check remote mailbox
        if (recipient != null && toLocation != null) {
            Block b = toLocation.getBlock();
            if (!(b.getState() instanceof InventoryHolder)) {
                plugin.cfg.mailbox.updateLocationMapping(recipient, null);
                toLocation = null;
            }
        }

        if (recipient == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer);
            return;
        } else if (toLocation == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer);
            Player tmp = plugin.getServer().getPlayer(toPlayer);
            if (tmp != null && tmp.isOnline()) {
                msg(tmp, "user.mailbox.create_mailbox_hint", sender.getName());
            }
            return;
        }

        final Location toLocationFinal = toLocation;
        Player recp = plugin.getServer().getPlayer(toPlayer);
        if (recp != null && !recp.isOnline()) recp = null;
        final Player recpFinal = recp;

        plugin.mailboxListener.registerRightClickCallback(p, 100,
                (Location boxLocation) -> {
                    Block b = boxLocation.getBlock();
                    if (plugin.getServer().getPluginManager().getPlugin("LockettePro") != null) {
                        if (LocketteProAPI.isLocked(b) && !LocketteProAPI.isUser(b, p)) {
                            msg(p, "user.mailbox.chest_protected");
                            return;
                        }
                    }

                    Inventory fromInventory = ((InventoryHolder) b.getState()).getInventory();
                    Inventory toInventory = ((InventoryHolder) toLocationFinal.getBlock().getState()).getInventory();
                    ItemStack[] from = fromInventory.getStorageContents();
                    ItemStack[] to = toInventory.getStorageContents();
                    int nextSlot = 0;
                    boolean itemMoved = false;
                    for (int i = 0; i < from.length; i++) {
                        if (from[i] != null && from[i].getType() != Material.AIR) {
                            while (nextSlot < to.length && to[nextSlot] != null && to[nextSlot].getType() != Material.AIR) {
                                nextSlot++;
                            }
                            if (nextSlot >= to.length) {
                                msg(sender, "user.mailbox.recipient_no_space");
                                if (recpFinal != null) {
                                    msg(recpFinal, "user.mailbox.mailbox_no_space", sender.getName());
                                }
                                return;
                            }
                            to[nextSlot] = from[i].clone();
                            itemMoved = true;
                            from[i] = new ItemStack(Material.AIR);
                            nextSlot++;
                        }
                    }
                    if (itemMoved) {
                        fromInventory.setStorageContents(from);
                        toInventory.setStorageContents(to);
                        msg(sender, "user.mailbox.mail_sent", toPlayer, (float) plugin.cfg.mailChestFee);
                        if (recpFinal != null) {
                            msg(recpFinal, "user.mailbox.mail_received", sender.getName());
                        }
                    } else {
                        msg(sender, "user.mailbox.mail_sent_nothing");
                    }
                    //TODO: LOG & FEE
                });
        msg(p, "user.mailbox.now_right_click_send", toPlayer);
    }
}
