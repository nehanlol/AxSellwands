package com.artillexstudios.axsellwands.timing;

import com.artillexstudios.axapi.scheduler.ScheduledTask;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axsellwands.AxSellwands;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Absolute deadlines need no tasks or loaded chunks while a wand is stored. */
public final class WandTimer {
    private static ScheduledTask task;
    private static NamespacedKey deadline;
    private static NamespacedKey loreIndex;

    private WandTimer() {}

    public static void start() {
        deadline = new NamespacedKey(AxSellwands.getInstance(), "expires-at");
        loreIndex = new NamespacedKey(AxSellwands.getInstance(), "timer-lore-index");
        task = Scheduler.get().runTimer(() -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                Scheduler.get().run(player, ignored -> {
                    long now = System.currentTimeMillis();
                    refreshInventory(player.getInventory(), now);
                    ItemStack cursor = player.getItemOnCursor();
                    if (refresh(cursor, now)) player.setItemOnCursor(cursor);
                    Inventory top = player.getOpenInventory().getTopInventory();
                    // Folia requires container access on its owning region.
                    if (top.getLocation() == null || Scheduler.get().isOwnedByCurrentRegion(top.getLocation())) {
                        refreshInventory(top, now);
                    }
                }, null);
            }
        }, 20L, 20L);
    }

    public static void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public static Long getExpiration(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(deadline, PersistentDataType.LONG);
    }

    public static void setExpiration(ItemStack item, long expiresAt) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(deadline, PersistentDataType.LONG, expiresAt);
        item.setItemMeta(meta);
    }

    private static void refreshInventory(Inventory inventory, long now) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (refresh(item, now)) inventory.setItem(slot, item);
        }
    }

    /** Updates only our appended line; preserves all existing lore and metadata. */
    public static boolean refresh(ItemStack item, long now) {
        Long expiresAt = getExpiration(item);
        if (expiresAt == null) return false;
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        Integer index = meta.getPersistentDataContainer().get(loreIndex, PersistentDataType.INTEGER);
        String text = expiresAt <= now
                ? AxSellwands.LANG.getString("timer-expired", "&cExpired")
                : AxSellwands.LANG.getString("timer-lore", "&7Time left: &e%time%")
                    .replace("%time%", WandDuration.remaining(expiresAt, now));
        String line = StringUtils.formatToString(text);
        if (index == null || index < 0 || index >= lore.size()) {
            index = lore.size();
            lore.add(line);
        } else {
            if (lore.get(index).equals(line)) return false;
            lore.set(index, line);
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(loreIndex, PersistentDataType.INTEGER, index);
        item.setItemMeta(meta);
        return true;
    }
}
