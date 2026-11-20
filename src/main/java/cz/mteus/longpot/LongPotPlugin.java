package cz.mteus.longpot;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import io.papermc.paper.potion.PotionMix;
import java.util.*;

public final class LongPotPlugin extends JavaPlugin implements Listener {
    private static final int DURATION = 9600;
    private final List<NamespacedKey> strengthRecipeKeys = new ArrayList<>();
    private static final String[] KEYS = {"SPEED","STRENGTH","FIRE_RESISTANCE","REGENERATION","JUMP_BOOST","NIGHT_VISION","INVISIBILITY","WATER_BREATHING","SLOW_FALLING","POISON","WEAKNESS","SLOWNESS"};
    private static final String[] POTIONS = {"SWIFTNESS","STRENGTH","FIRE_RESISTANCE","REGENERATION","LEAPING","NIGHT_VISION","INVISIBILITY","WATER_BREATHING","SLOW_FALLING","POISON","WEAKNESS","SLOWNESS"};
    private static final String[] EFFECTS = {"speed","strength","fire_resistance","regeneration","jump_boost","night_vision","invisibility","water_breathing","slow_falling","poison","weakness","slowness"};
    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;
        public Inventory getInventory() { return inventory; }
    }
    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this,this);
        updateStrengthRecipes();
    }
    @Override public void onDisable() {
        removeStrengthRecipes();
    }
    private void removeStrengthRecipes() {
        for (NamespacedKey key : strengthRecipeKeys) {
            Bukkit.getPotionBrewer().removePotionMix(key);
        }
        strengthRecipeKeys.clear();
    }
    private void updateStrengthRecipes() {
        removeStrengthRecipes();
        if (!getConfig().getBoolean("enabled.STRENGTH")) return;
        for (Material bottle : new Material[]{Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION}) {
            registerStrengthMix(bottle, PotionType.STRONG_STRENGTH, Material.REDSTONE, "strong_redstone");
            registerStrengthMix(bottle, PotionType.LONG_STRENGTH, Material.GLOWSTONE_DUST, "long_glowstone");
        }
    }
    private void registerStrengthMix(Material bottle, PotionType input, Material ingredient, String suffix) {
        ItemStack result = new ItemStack(bottle);
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        // A neutral base avoids duplicate Strength effects in the tooltip.
        meta.setBasePotionType(PotionType.WATER);
        meta.setDisplayName(ChatColor.RESET + "Potion of Strength II");
        meta.setColor(Color.fromRGB(255, 205, 50));
        meta.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, DURATION, 1), true);
        result.setItemMeta(meta);
        NamespacedKey key = new NamespacedKey(this, "strength_" + suffix + "_" + bottle.name().toLowerCase(Locale.ROOT));
        PotionMix mix = new PotionMix(
            key,
            result,
            PotionMix.createPredicateChoice(item -> {
                if (item == null || item.getType() != bottle) return false;
                if (!(item.getItemMeta() instanceof PotionMeta potionMeta)) return false;
                return potionMeta.getBasePotionType() == input
                    && !potionMeta.hasCustomEffects();
            }),
            new RecipeChoice.MaterialChoice(ingredient)
        );
        Bukkit.getPotionBrewer().addPotionMix(mix);
        strengthRecipeKeys.add(key);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (!player.hasPermission("longpot.admin")) { player.sendMessage(ChatColor.RED+"No permission."); return true; }
        show(player);
        return true;
    }
    private void show(Player player) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder,27,"LongPot | 8 minutes");
        holder.inventory=inv;
        for (int i=0;i<KEYS.length;i++) {
            boolean enabled=getConfig().getBoolean("enabled."+KEYS[i]);
            ItemStack item=new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta=item.getItemMeta();
            meta.setDisplayName((enabled ? ChatColor.GREEN : ChatColor.RED)+KEYS[i]);
            meta.setLore(List.of(ChatColor.GRAY+"8:00 minutes",ChatColor.YELLOW+"Click to toggle"));
            item.setItemMeta(meta);
            inv.setItem(i,item);
        }
        player.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        int slot=e.getRawSlot();
        if (slot<0 || slot>=KEYS.length) return;
        if (!(e.getWhoClicked() instanceof Player player) || !player.hasPermission("longpot.admin")) return;
        String key="enabled."+KEYS[slot];
        getConfig().set(key,!getConfig().getBoolean(key));
        saveConfig();
        if (slot == 1) updateStrengthRecipes();
        show(player);
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }
    @EventHandler public void brew(BrewEvent e) {
        List<ItemStack> results=e.getResults();
        for (int i=0;i<results.size();i++) {
            ItemStack item=results.get(i);
            if (item==null || !(item.getItemMeta() instanceof PotionMeta meta)) continue;
            if (item.getType()!=Material.POTION && item.getType()!=Material.SPLASH_POTION && item.getType()!=Material.LINGERING_POTION) continue;
            PotionType base=meta.getBasePotionType();
            if (base==null) continue;
            String name=base.name();
            for (int j=0;j<KEYS.length;j++) {
                if (j == 1) continue; // Keep normal Strength brewing vanilla; special mixes handle upgrades.
                if (!name.equals(POTIONS[j]) && !name.equals("LONG_"+POTIONS[j]) && !name.equals("STRONG_"+POTIONS[j])) continue;
                if (!getConfig().getBoolean("enabled."+KEYS[j])) break;
                PotionEffectType type=Registry.EFFECT.get(NamespacedKey.minecraft(EFFECTS[j]));
                if (type==null) break;
                int amplifier=name.startsWith("STRONG_")?1:0;
                // Use a neutral base potion to avoid showing both vanilla and custom effects.
                meta.setBasePotionType(PotionType.WATER);
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Potion of " + KEYS[j].replace('_', ' ') + (amplifier == 1 ? " II" : ""));
                meta.setColor(type.getColor());
                meta.addCustomEffect(new PotionEffect(type,DURATION,amplifier),true);
                item.setItemMeta(meta);
                results.set(i,item);
                break;
            }
        }
    }
}
