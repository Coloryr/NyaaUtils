package cat.nyaa.nyaautils.extrabackpack;

import cat.nyaa.nyaacore.Message;
import cat.nyaa.nyaacore.Pair;
import cat.nyaa.nyaacore.database.relational.Query;
import cat.nyaa.nyaacore.database.relational.RelationalDB;
import cat.nyaa.nyaacore.utils.ItemStackUtils;
import cat.nyaa.nyaautils.I18n;
import cat.nyaa.nyaautils.NyaaUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtraBackpackGUI implements InventoryHolder {

    private final NyaaUtils plugin;
    private final RelationalDB database;
    private final UUID owner;
    private final Player opener;
    private static final Map<UUID, Player> opened = new HashMap<>();
    private static final Map<UUID, List<String>> lastState = new HashMap<>();
    private int currentPage = 0;
    private int maxLine = 0;
    private BukkitTask daemonTask = null;
    // 1: scheduled. 0: done. -1: supressed.
    private final AtomicInteger saveScheduled = new AtomicInteger(0);
    private final AtomicBoolean tainted = new AtomicBoolean(false);

    ExtraBackpackGUI(NyaaUtils plugin, RelationalDB database, UUID owner, Player opener) {
        this.plugin = plugin;
        this.database = database;
        this.owner = owner;
        this.opener = opener;
    }

    void open(int page) {
        if (tainted.get()) {
            close();
            throw new IllegalStateException();
        }
        Player currentOpener;
        if ((currentOpener = opened.putIfAbsent(owner, opener)) != null) {
            if (!currentOpener.equals(opener) && opener.getOpenInventory().getTopInventory().getHolder() instanceof ExtraBackpackGUI) {
                if (opener.hasPermission("nu.bp.admin") && !currentOpener.hasPermission("nu.bp.admin")) {
                    Inventory inventory = currentOpener.getOpenInventory().getTopInventory();
                    if (inventory.getHolder() instanceof ExtraBackpackGUI) {
                        ((ExtraBackpackGUI) inventory.getHolder()).saveAll(inventory);
                        ((ExtraBackpackGUI) inventory.getHolder()).close();
                    }
                    new Message(I18n.format("user.backpack.force_opened")).send(currentOpener);
                } else {
                    new Message(I18n.format("user.backpack.already_opened")).send(opener);
                    return;
                }
            }
        }
        currentPage = page;
        String ownerId = owner.toString();
        maxLine = plugin.cfg.bp_default_lines;
        try (Query<ExtraBackpackConfig> query = database.queryTransactional(ExtraBackpackConfig.class).whereEq("player_id", ownerId)) {
            ExtraBackpackConfig cfg = query.selectUniqueForUpdate();
            if (cfg != null) {
                maxLine = cfg.getMaxLine();
                query.commit();
            } else {
                cfg = new ExtraBackpackConfig();
                cfg.setPlayerId(ownerId);
                cfg.setMaxLine(maxLine);
                query.insert(cfg);
                query.commit();
            }
        }
        if (maxLine <= 0) {
            new Message(I18n.format("user.backpack.disabled")).send(opener);
            opened.remove(owner);
            return;
        }
        List<ExtraBackpackLine> lines;
        try (Query<ExtraBackpackLine> query = database.queryTransactional(ExtraBackpackLine.class).whereEq("player_id", ownerId)) {
            lines = query.select();
            if (lines.size() > maxLine) {
                opened.remove(owner);
                throw new IllegalStateException("Too many lines");
            } else if (lines.size() != maxLine) {
                for (int cur = lines.size(); cur < maxLine; ++cur) {
                    ExtraBackpackLine l = new ExtraBackpackLine();
                    l.setPlayerId(ownerId);
                    l.setLineNo(cur);
                    l.setItemStacks(Stream.generate(() -> new ItemStack(Material.AIR)).limit(9).collect(Collectors.toList()));
                    query.insert(l);
                    lines.add(l);
                }
            }
            lastState.put(owner, lines.stream().map(ExtraBackpackLine::getItems).collect(Collectors.toList()));
            query.commit();
        }
        int pageCount = (int) Math.ceil(maxLine / 6.0);
        List<ExtraBackpackLine> view = lines.stream().skip(page * 6).limit(6).collect(Collectors.toList());
        int viewSize = view.size();
        if (viewSize == 0) {
            new Message(I18n.format("user.backpack.invalid_page", page, pageCount)).send(opener);
            opened.remove(owner);
            return;
        }
        int size = viewSize * 9;
        Inventory inventory = Bukkit.createInventory(this, size, I18n.format("user.backpack.title", Bukkit.getOfflinePlayer(owner).getName(), page, pageCount - 1));
        ItemStack[] itemStacks = view.stream().flatMap(l -> l.getItemStacks().stream()).toArray(ItemStack[]::new);
        inventory.setContents(itemStacks);
        saveScheduled.set(-1);
        opener.openInventory(inventory);
        saveScheduled.set(0);
        if (daemonTask == null) {
            daemonTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!opener.isOnline() || opener.getOpenInventory().getTopInventory().getHolder() != ExtraBackpackGUI.this) {
                        this.cancel();
                        new IllegalAccessException().printStackTrace();
                        saveScheduled.set(1);
                        saveAll(inventory);
                        close();
                    }
                }
            }.runTaskTimer(plugin, 0, 0);
        }
    }

    void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.equals(opener)) {
            player.closeInventory();
            event.setCancelled(true);
            tainted.set(false);
            return;
        }
        if (!player.equals(opened.get(owner))){
            player.closeInventory();
            event.setCancelled(true);
            tainted.set(false);
            new Message(I18n.format("user.backpack.error_closed")).send(player);
            return;
        }
        Inventory inventory = event.getInventory();
        if (event.getAction() == InventoryAction.NOTHING && event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            if (event.isLeftClick()) {
                if (maxLine <= 6) return;
                event.setCancelled(true);
                saveScheduled.set(1);
                saveAll(inventory);
                Bukkit.getScheduler().runTask(plugin, () -> this.open(prevPage()));
                return;
            } else if (event.isRightClick()) {
                if (maxLine <= 6) return;
                event.setCancelled(true);
                saveScheduled.set(1);
                saveAll(inventory);
                Bukkit.getScheduler().runTask(plugin, () -> this.open(nextPage()));
                return;
            }
        }
        scheduleSaveAll(inventory);
    }

    private int prevPage() {
        int prev = currentPage - 1;
        if (prev < 0) {
            prev = (int) (Math.ceil(maxLine / 6.0) - 1);
        }
        return prev;
    }

    private int nextPage() {
        int next = currentPage + 1;
        if (next >= Math.ceil(maxLine / 6.0)) {
            next = 0;
        }
        return next;
    }

    void onInventoryClose(InventoryCloseEvent event) {
        if (opened.containsKey(owner) && saveScheduled.compareAndSet(0, 1)) {
            daemonTask.cancel();
            Inventory inventory = event.getInventory();
            saveAll(inventory);
            opened.remove(owner);
            lastState.remove(owner);
        }
    }

    private void saveAll(Inventory inventory) {
        if (saveScheduled.getAndSet(0) != 1) {
            return;
        }
        if (inventory.getHolder() != this) {
            throw new IllegalArgumentException();
        }
        boolean saved = true;
        Map<Integer, Throwable> exceptions = new HashMap<>();
        synchronized (this) {
            for (int i = 0; i < inventory.getSize() / 9; ++i) {
                List<ItemStack> line = Arrays.stream(inventory.getContents()).skip(i * 9).limit(9).collect(Collectors.toList());
                Pair<Boolean, Throwable> currentResult = saveLine(i, line);
                saved &= currentResult.getKey();
                if (currentResult.getValue() != null) {
                    exceptions.put(i, currentResult.getValue());
                }
            }
            if (!saved) {
                close();
                new Message(I18n.format("user.backpack.error_saving")).send(opener);
                for (Map.Entry<Integer, Throwable> entry : exceptions.entrySet()) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save backpack of " + owner.toString() + " line " + entry.getKey(), entry.getValue());
                }
            } else {
                tainted.set(false);
            }
        }
    }

    private Pair<Boolean, Throwable> saveLine(int i, List<ItemStack> line) {
        String desiredState = ItemStackUtils.itemsToBase64(line);
        int lineNo = currentPage * 6 + i;
        try {
            if (!lastState.containsKey(owner) || !opened.containsKey(owner)) {
                if (tainted.get()) {
                    String tainted = String.format("Tainted backpack of %s closed before saving when backpack opened by %s! Unsaved: %s", owner.toString(), opener.getUniqueId().toString(), desiredState);
                    Message message = new Message(tainted);
                    Bukkit.getOperators().forEach(op -> message.send(op, true));
                    plugin.getLogger().severe(tainted);
                    new RuntimeException().printStackTrace();
                } else {
                    plugin.getLogger().warning(String.format("Trying to save a untainted closed backpack of %s opened by %s! Current: %s", owner.toString(), opener.getUniqueId().toString(), desiredState));
                    new RuntimeException().printStackTrace();
                }
                return Pair.of(false, null);
            }
            String lineLastState = lastState.get(owner).get(lineNo);
            if (lineLastState.equals(desiredState)) return Pair.of(true, null);
            try (Query<ExtraBackpackLine> query = database.queryTransactional(ExtraBackpackLine.class).whereEq("player_id", owner.toString()).whereEq("line_no", lineNo)) {
                ExtraBackpackLine backpackLine = query.selectUniqueForUpdate();
                String lineCurrentState = backpackLine.getItems();
                if (!lineLastState.equals(lineCurrentState)) {
                    new Message(I18n.format("user.backpack.error_state", Bukkit.getOfflinePlayer(owner).getName(), lineNo, opener.getUniqueId())).broadcast(new Permission("nu.bp.admin"));
                    String errLine1 = String.format("%s's line %s (%d) changed when backpack opened by %s! ", owner.toString(), backpackLine.getId(), lineNo, opener.getUniqueId().toString());
                    String errLine2 = String.format("Should be %s, actually %s, desired %s", lineLastState, lineCurrentState, desiredState);
                    Message message = new Message(errLine1 + "\n" + errLine2);
                    Bukkit.getOperators().forEach(op -> message.send(op, true));
                    plugin.getLogger().warning(errLine1);
                    plugin.getLogger().warning(errLine2);
                    query.rollback();
                    return Pair.of(false, null);
                }
                backpackLine.setItems(desiredState);
                lastState.get(owner).set(backpackLine.getLineNo(), backpackLine.getItems());
                plugin.getLogger().finer(() -> String.format("Saving %d: %s (%s)", lineNo, line, backpackLine.getItems()));
                query.update(backpackLine);
                query.commit();
            }
            return Pair.of(true, null);
        } catch (Throwable throwable) {
            new Message(I18n.format("user.backpack.unexpected_error", Bukkit.getOfflinePlayer(owner).getName(), lineNo, opener.getUniqueId())).broadcast(new Permission("nu.bp.admin"));
            String errLine1 = String.format("%s's line %d errored saving when backpack opened by %s! ", owner.toString(), lineNo, opener.getUniqueId().toString());
            String errLine2 = String.format("Unsaved %s, error: %s", desiredState, throwable.getLocalizedMessage());
            Message message = new Message(errLine1 + "\n" + errLine2);
            Bukkit.getOperators().forEach(op -> message.send(op, true));
            plugin.getLogger().warning(errLine1);
            plugin.getLogger().warning(errLine2);
            return Pair.of(false, throwable);
        }
    }

    void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        scheduleSaveAll(inventory);
    }

    private void scheduleSaveAll(Inventory inventory) {
        saveScheduled.set(1);
        Bukkit.getScheduler().runTask(plugin, () -> saveAll(inventory));
    }

    private void close() {
        opened.remove(owner);
        lastState.remove(owner);
        opener.closeInventory();
    }

    void taint() {
        tainted.set(true);
    }

    static boolean isOpened(UUID owner) {
        Player opener = opened.get(owner);
        if (opener != null && opener.getOpenInventory().getTopInventory().getHolder() instanceof ExtraBackpackGUI) {
            return true;
        } else if (opener != null) {
            opened.remove(owner);
        }
        return false;
    }

    public static void closeAll() {
        Collection<Player> openers = opened.values();
        for (Player p : openers) {
            InventoryView view = p.getOpenInventory();
            Inventory inventory = view.getTopInventory();
            if (inventory.getHolder() instanceof ExtraBackpackGUI) {
                ExtraBackpackGUI holder = (ExtraBackpackGUI) inventory.getHolder();
                holder.saveAll(inventory);
                holder.close();
            }
        }
    }

    @Override
    @Nonnull
    public Inventory getInventory() {
        return Bukkit.createInventory(null, 54);
    }
}
