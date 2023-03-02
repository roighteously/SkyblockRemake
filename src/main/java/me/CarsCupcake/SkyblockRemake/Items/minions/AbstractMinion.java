package me.CarsCupcake.SkyblockRemake.Items.minions;

import me.CarsCupcake.SkyblockRemake.API.Bundle;
import me.CarsCupcake.SkyblockRemake.Configs.CustomConfig;
import me.CarsCupcake.SkyblockRemake.Items.ItemHandler;
import me.CarsCupcake.SkyblockRemake.Items.ItemManager;
import me.CarsCupcake.SkyblockRemake.Items.Items;
import me.CarsCupcake.SkyblockRemake.Main;
import me.CarsCupcake.SkyblockRemake.Skyblock.SkyblockPlayer;
import me.CarsCupcake.SkyblockRemake.utils.Assert;
import me.CarsCupcake.SkyblockRemake.utils.Tools;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Basic implementation of the {@link Minion} interface, here are all
 * usefull and general methods implemented
 */

public abstract class AbstractMinion implements Minion {
    private int level;
    private final IMinion base;
    protected ArmorStand stand;
    protected final Location location;
    private final String minionId;
    protected final SkyblockPlayer player;
    protected final ArrayList<ItemStack> inventory = new ArrayList<>();
    protected int inventorySpace;
    protected long timeBetweenActions;
    protected BukkitRunnable breakingRunnable;
    protected boolean isFull;
    protected ArmorStand message;

    /**
     * This constructor provides the basic values
     *
     * @param level            is the level of the minion
     * @param base             is the base item of the minion
     * @param location         is the location where the minion is placed
     * @param minionIdentifier is a string for the minion. This is a random UUID from the method {@link UUID#randomUUID()} and is also used to load the minion from the file
     * @param placer           is the player who owns the isle
     */
    public AbstractMinion(int level, IMinion base, Location location, String minionIdentifier, SkyblockPlayer placer) {
        Assert.state(level <= base.getLevels());
        inventorySpace = getMinionInventorySpace(level);
        this.level = level;
        this.base = base;
        this.location = location;
        minionId = minionIdentifier;
        this.player = placer;
        timeBetweenActions = base.timeBetweenActions()[level - 1];
        loadInventory();
        placeMinion();
        startWorking();
    }

    public void placeMinion() {
        stand = location.getWorld().spawn(location, ArmorStand.class, s -> {
            s.setSmall(true);
            s.setInvulnerable(true);
            s.setGravity(false);
            s.getEquipment().setItemInMainHand(base.getItemInHand());
            HashMap<EquipmentSlot, ItemStack> equipment = base.getEquipment();
            for (EquipmentSlot slot : equipment.keySet())
                s.getEquipment().setItem(slot, equipment.get(slot));
        });
    }

    public void loadInventory() {
        inventory.clear();
        CustomConfig config = new CustomConfig(player, "minions", false);
        ConfigurationSection section = config.get().getConfigurationSection(minionId + ".items");
        if (section == null)
            return;
        ArrayList<Bundle<Integer, ItemStack>> values = new ArrayList<>();
        for (String s : section.getKeys(false)) {
            ConfigurationSection ss = section.getConfigurationSection(s);
            ItemStack item = Items.SkyblockItems.get(ss.getString("id")).createNewItemStack();
            item.setAmount(Integer.parseInt(ss.getString("count")));
            values.add(new Bundle<>(Integer.parseInt(s), item));
        }
        values.sort(new Tools.SmalerLargerEqualsNumberBundle());
        boolean oneItemStackHasSpace = false;
        for (Bundle<Integer, ItemStack> b : values) {
            inventory.add(b.getLast());
            if (b.getLast().getAmount() < b.getLast().getMaxStackSize())
                oneItemStackHasSpace = true;
        }
        isFull = inventory.size() == inventorySpace && !oneItemStackHasSpace;
        if(isFull)
            setFull();
    }

    public void saveMinion() {
        CustomConfig config = new CustomConfig(player, "minions", false);
        config.get().set(minionId, null);
        config.get().set(minionId + ".id", base.id() + "-" + level);
        config.get().set(minionId + ".location.x", location.getX());
        config.get().set(minionId + ".location.y", location.getY());
        config.get().set(minionId + ".location.z", location.getZ());
        int i = 0;
        for (ItemStack item : inventory) {
            config.get().set(minionId + ".items." + i + ".id", ItemHandler.getPDC("id", item, PersistentDataType.STRING));
            config.get().set(minionId + ".items." + i + ".count", item.getAmount());
            i++;
        }
        config.save();
    }

    public void removeMinionFromFile() {
        CustomConfig config = new CustomConfig(player, "minions", false);
        config.get().set(minionId, null);
        config.save();
    }

    /**
     * starts the get animation, and after finishing the animation runs the {@link AbstractMinion#generateLoot()} method
     */
    abstract void startGetAnimation();

    /**
     * is used to regenerate the resource
     *
     * @return true if the max amount of resources has been spawned
     */
    abstract boolean startGenerateAnimation();

    /**
     * is a check if all resourses has been respawned
     *
     * @return true if all resources are regenerated false if not
     */
    abstract boolean isMaxGenerated();

    public void generateLoot() {
        for (Bundle<ItemManager, Integer> b : Tools.generateItems(base.drops())) {
            if (!addItemToInventory(b.getFirst(), b.getLast())) {
                setFull();
            }
        }
    }

    public void setFull() {
        isFull = true;
        try {
            breakingRunnable.cancel();
        } catch (Exception ignored) {
        }
        setMinionMessage("§c/!\\ Minion is full!", -1);
    }

    /**
     * adds an item to the inventory
     *
     * @param item   is the item thats getting added
     * @param amount is the amount of the item. amount has to be smaller then max stack size
     * @return true if the adding was possible (also returns false if not the full amount of items was possible to add to the inventory)
     */
    public boolean addItemToInventory(ItemManager item, int amount) {
        Assert.isLarger(0, amount);
        Assert.isSmaller(item.material.getMaxStackSize() + 1, amount);
        for (ItemStack s : inventory) {
            if (s.getMaxStackSize() == s.getAmount())
                continue;
            if (ItemHandler.getPDC("id", s, PersistentDataType.STRING).equals(item.itemID)) {
                if (amount + s.getAmount() > s.getMaxStackSize()) {
                    amount -= (s.getMaxStackSize() - s.getAmount());
                    s.setAmount(s.getMaxStackSize());
                } else {
                    s.setAmount(amount + s.getAmount());
                    return true;
                }
            }
        }
        if (amount <= 0)
            return true;

        if (inventory.size() < inventorySpace) {
            ItemStack i = item.createNewItemStack();
            i.setAmount(amount);
            inventory.add(i);
            return true;
        }

        return false;
    }

    @Override
    public void remove(MinionRemoveReason removeReason) {
        stand.remove();
        if (removeReason == MinionRemoveReason.QUIT) {
            saveMinion();
        } else removeMinionFromFile();
    }

    public void setLevel(int i) {
        Assert.isLarger(level, i, "New Minion level has to be larger than the level before!");
        level = i;
        stand.getEquipment().setHelmet(Tools.CustomHeadTexture(base.getHeadStrings()[level - 1]));
        inventorySpace = getMinionInventorySpace(level);
        startWorking();
    }

    @Override
    public void startWorking() {
        if (isFull)
            return;

        if (message != null) {
            message.remove();
            message = null;
        }

        if (breakingRunnable != null && !breakingRunnable.isCancelled())
            try {
                breakingRunnable.cancel();
            } catch (Exception ignored) {
            }
        breakingRunnable = new BukkitRunnable() {
            int phase = (isMaxGenerated()) ? 0 : 1;

            @Override
            public void run() {
                if (phase == 0) {
                    startGetAnimation();
                    phase = 1;
                } else {
                    if (startGenerateAnimation())
                        phase = 0;
                }
            }
        };
        breakingRunnable.runTaskTimer(Main.getMain(), timeBetweenActions, timeBetweenActions);
    }

    private BukkitRunnable messageTimer;

    @Override
    public void setMinionMessage(String message, long duration) {
        if (messageTimer != null && !messageTimer.isCancelled())
            try {
                messageTimer.cancel();
            } catch (Exception ignored) {
            }
        if (this.message == null)
            this.message = stand.getWorld().spawn(stand.getLocation().clone().add(0, 1, 0), ArmorStand.class, s -> {
                s.setMarker(true);
                s.setInvisible(true);
                s.setCustomNameVisible(true);
            });

        this.message.setCustomName(message);

        if (duration != -1) {
            messageTimer = new BukkitRunnable() {
                @Override
                public void run() {
                    AbstractMinion.this.message.remove();
                    AbstractMinion.this.message = null;
                }
            };
            messageTimer.runTaskLater(Main.getMain(), duration);
        }

    }

    public static int getMinionInventorySpace(int level) {
        switch (level) {
            case 1 -> {
                return 1;
            }
            case 2, 3 -> {
                return 3;
            }
            case 4, 5 -> {
                return 6;
            }
            case 6, 7 -> {
                return 9;
            }
            case 8, 9 -> {
                return 12;
            }
            case 10, 11, 12 -> {
                return 15;
            }
        }
        return 0;
    }
}
