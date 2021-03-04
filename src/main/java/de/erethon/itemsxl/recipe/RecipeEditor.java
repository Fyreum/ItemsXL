package de.erethon.itemsxl.recipe;

import de.erethon.caliburn.CaliburnAPI;
import de.erethon.caliburn.item.ExItem;
import de.erethon.caliburn.recipe.CustomRecipe;
import de.erethon.caliburn.recipe.CustomShapedRecipe;
import de.erethon.caliburn.recipe.CustomShapelessRecipe;
import de.erethon.caliburn.recipe.ingredient.ItemIngredient;
import de.erethon.caliburn.recipe.ingredient.MaterialIngredient;
import de.erethon.caliburn.recipe.ingredient.RecipeIngredient;
import de.erethon.caliburn.recipe.result.RecipeResult;
import de.erethon.caliburn.util.RecipeUtil;
import de.erethon.caliburn.util.Util;
import de.erethon.caliburn.util.Validate;
import de.erethon.caliburn.util.ValidationException;
import de.erethon.commons.chat.MessageUtil;
import de.erethon.itemsxl.ItemsXL;
import de.erethon.itemsxl.config.IMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RecipeEditor implements Listener {

    private static final int RESULT_INDEX = 14;
    private static final int[] RECIPE_FIELD_INDEXES = new int[]{1,2,3,10,11,12,19,20,21};
    private static final int[] NON_GLASS_INDEXES = new int[]{RESULT_INDEX,16,17};

    private static final NamespacedKey GLASS_KEY = ItemsXL.key("editor_glass");
    private static final NamespacedKey TYPE_KEY = ItemsXL.key("type_button");
    private static final NamespacedKey FINISH_KEY = ItemsXL.key("finish_button");
    private static final NamespacedKey NO_RESULT_KEY = ItemsXL.key("no_result_key");

    private static final String DUMMY_STRING = "dummy";

    private static final ItemStack GLASS = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    private static final ItemStack SHAPED_TYPE_BUTTON = new ItemStack(Material.PAPER);
    private static final ItemStack SHAPELESS_TYPE_BUTTON = new ItemStack(Material.PAPER);
    private static final ItemStack FINISH_BUTTON = new ItemStack(Material.GREEN_DYE);
    private static final ItemStack NO_RESULT_ITEM = new ItemStack(Material.RED_DYE);

    // listener stuff

    private static final Map<UUID, EditSession> currentEditors = new HashMap<>();

    static {
        Arrays.sort(RECIPE_FIELD_INDEXES);
        Arrays.sort(NON_GLASS_INDEXES);
        // buttons
        specifyMeta(GLASS, GLASS_KEY, "&0", true);
        specifyMeta(SHAPED_TYPE_BUTTON, TYPE_KEY, IMessage.EDITOR_SHAPED_RECIPE.getMessage());
        specifyMeta(SHAPELESS_TYPE_BUTTON, TYPE_KEY, IMessage.EDITOR_SHAPELESS_RECIPE.getMessage());
        specifyMeta(FINISH_BUTTON, FINISH_KEY, IMessage.EDITOR_ADD_RECIPE.getMessage());
        specifyMeta(NO_RESULT_ITEM, NO_RESULT_KEY, IMessage.EDITOR_MISSING_RESULT.getMessage());
    }

    private static void specifyMeta(ItemStack item, NamespacedKey key, String name) {
        specifyMeta(item, key, name, false);
    }

    private static void specifyMeta(ItemStack item, NamespacedKey key, String name, boolean hideAttr) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (hideAttr) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, DUMMY_STRING);
        item.setItemMeta(meta);
    }

    public static void startSession(String id, Player player) {
        currentEditors.put(player.getUniqueId(), new EditSession(id, null));
        openInv(player, id, null);
    }

    public static void startSession(CustomRecipe recipe, Player player) {
        currentEditors.put(player.getUniqueId(), new EditSession(recipe.getKey().getKey(), recipe));
        openInv(player, recipe.getRecipeResult().getStringKey(), recipe);
    }

    private static void openInv(Player player, String name, CustomRecipe existing) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&8[&6Recipe Editor&8] &7" + name));

        for (int i = 0; i < 27; i++) {
            if (!Util.contains(RECIPE_FIELD_INDEXES, i) && !Util.contains(NON_GLASS_INDEXES, i)) {
                inv.setItem(i, GLASS);
            }
        }
        inv.setItem(16, SHAPED_TYPE_BUTTON);
        inv.setItem(17, FINISH_BUTTON);

        if (existing != null) {
            if (existing instanceof CustomShapedRecipe) {
                CustomShapedRecipe shaped = (CustomShapedRecipe) existing;
                String[] shape = shaped.getShape();
                Map<Character, RecipeIngredient> ingredients = shaped.getIngredients();

                inv.setItem(RESULT_INDEX, shaped.getRecipeResult().getItemStack());

                int k = 0;
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        char c = shape[i].toCharArray()[j];
                        for (Character character : ingredients.keySet()) {
                            if (c == character) {
                                inv.setItem(RECIPE_FIELD_INDEXES[k], ingredients.get(character).getRecipeChoice().getItemStack());
                            }
                        }
                        k++;
                    }
                }
            } else {
                CustomShapelessRecipe shapeless = (CustomShapelessRecipe) existing;
                Map<RecipeIngredient, Integer> ingredients = shapeless.getIngredients();

                inv.setItem(RESULT_INDEX, shapeless.getRecipeResult().getItemStack());

                int i = 0;
                for (RecipeIngredient ingredient : ingredients.keySet()) {
                    for (int j = 0; j < ingredients.get(ingredient); j++) {
                        if (i >= 9) {
                            throw new IllegalArgumentException("Too many ingredients");
                        }
                        inv.setItem(RECIPE_FIELD_INDEXES[i], ingredient.getRecipeChoice().getItemStack());
                        i++;
                    }
                }
            }
        }

        player.openInventory(inv);
    }

    // listener

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (!currentEditors.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        currentEditors.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handleInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (!currentEditors.containsKey(who.getUniqueId())) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || event.getCurrentItem() == null) {
            return;
        }
        ItemStack item = event.getCurrentItem();

        if (item.equals(GLASS)) {
            event.setCancelled(true);
            return;
        }
        if (item.equals(SHAPED_TYPE_BUTTON)) {
            event.setCancelled(true);
            currentEditors.get(who.getUniqueId()).setShaped(false);
            event.setCurrentItem(SHAPELESS_TYPE_BUTTON);
            return;
        }
        if (item.equals(SHAPELESS_TYPE_BUTTON)) {
            event.setCancelled(true);
            currentEditors.get(who.getUniqueId()).setShaped(true);
            event.setCurrentItem(SHAPED_TYPE_BUTTON);
            return;
        }
        if (item.equals(FINISH_BUTTON)) {
            event.setCancelled(true);
            try {
                saveRecipe(clicked, currentEditors.get(who.getUniqueId()));
            } catch (ValidationException e) {
                event.setCurrentItem(NO_RESULT_ITEM);
                Bukkit.getScheduler().runTaskLater(ItemsXL.inst(), () -> event.setCurrentItem(FINISH_BUTTON), 30);
                return;
            }
            MessageUtil.sendMessage(who, "&aRecipe saved!");
            currentEditors.remove(who.getUniqueId());
            who.closeInventory();
        }
    }

    private void saveRecipe(Inventory inv, EditSession session) {
        ItemsXL plugin = ItemsXL.inst();
        String id = session.getId();

        ItemStack result = Validate.notNull(inv.getItem(RESULT_INDEX), "You need to set an result");

        CaliburnAPI api = ItemsXL.inst().getAPI();
        File recipesFile = api.getRecipesFile();
        if (session.isShaped()) {
            String shape = "123456789";
            Map<RecipeIngredient, List<Character>> cache = new HashMap<>();
            Map<Character, RecipeIngredient> ingredients = new HashMap<>();

            int c = 1;
            int ingredientIndex = 0; // the amount of saved ingredients so far
            for (int index : RECIPE_FIELD_INDEXES) {
                ItemStack item = inv.getItem(index);
                if (item == null || item.getType().equals(Material.AIR)) {
                    shape = shape.replace(Util.getChar(c), ' ');
                    c++;
                    continue;
                }
                RecipeIngredient ingredient = getIngredient(id, ingredientIndex, item);

                boolean set = true;
                if (!cache.isEmpty()) {
                    for (RecipeIngredient key : cache.keySet()) {
                        if (key.getRecipeChoice().equals(ingredient.getRecipeChoice())) {
                            cache.get(key).add(Util.getChar(c));
                            set = false;
                        }
                    }
                }
                if (set) {
                    List<Character> chars = new ArrayList<>();
                    chars.add(Util.getChar(c));
                    cache.put(ingredient, chars);
                }
                c++;
            }

            int i;
            for (RecipeIngredient ingredient : cache.keySet()) {
                i = ingredients.size();

                List<Character> chars = cache.get(ingredient);

                char c1 = Util.getChar(i);
                for (Character ch : chars) {
                    shape = shape.replace(ch, c1);
                }
                ingredients.put(c1, ingredient);
            }

            RecipeResult recipeResult = getResult(id, result);
            CustomShapedRecipe recipe = new CustomShapedRecipe(ItemsXL.key(id), recipeResult, RecipeUtil.toShape(shape), ingredients);

            if (session.isExisting()) {
                api.removeRecipe(session.getExisting());
                api.deleteRecipe(session.getExisting());
            }
            if (session.isExisting()) {
                api.deleteRecipe(session.getExisting());
            }
            plugin.saveRecipe(recipesFile, id, recipe);
            api.addRecipe(recipe);
        } else {
            Map<RecipeIngredient, Integer> ingredients = new HashMap<>();

            int ingredientIndex = 0; // the amount of saved ingredients so far
            for (Integer index : RECIPE_FIELD_INDEXES) {
                ItemStack item = inv.getItem(index);
                if (item == null) {
                    continue;
                }
                boolean set = true;
                for (RecipeIngredient key : ingredients.keySet()) {
                    if (key.getRecipeChoice().getItemStack().isSimilar(item)) {
                        Integer value = ingredients.get(key);
                        ingredients.put(key, value == null ? 1 : value + 1);
                        set = false;
                    }
                }
                if (set) {
                    ingredients.put(getIngredient(id, ingredientIndex, item), 1);
                    ingredientIndex++;
                }
            }

            RecipeResult recipeResult = getResult(id, result);
            CustomShapelessRecipe recipe = new CustomShapelessRecipe(ItemsXL.key(id), recipeResult, ingredients);

            if (session.isExisting()) {
                api.deleteRecipe(session.getExisting());
            }
            plugin.saveRecipe(recipesFile, id, recipe);
            api.addRecipe(recipe);
        }
    }

    private RecipeIngredient getIngredient(String id, int ingredientIndex, ItemStack item) {
        return item.hasItemMeta() ? new ItemIngredient(getExItem(id + "_INGREDIENT_" + ingredientIndex, item)) : new MaterialIngredient(item.getType());
    }

    private RecipeResult getResult(String id, ItemStack result) {
        CaliburnAPI api = ItemsXL.inst().getAPI();
        RecipeResult recipeResult = null;
        for (ExItem exItem : api.getExItems()) {
            if (exItem.toItemStack().isSimilar(result)) {
                recipeResult = new RecipeResult(exItem, result.getAmount());
            }
        }
        if (recipeResult == null) {
            ExItem exItem = getExItem(id + "_RESULT", result);
            recipeResult = result.hasItemMeta() ? new RecipeResult(exItem, result.getAmount()) : new RecipeResult(result.getType(), result.getAmount());
        }
        return recipeResult;
    }

    private ExItem getExItem(String id, ItemStack item) {
        CaliburnAPI api = ItemsXL.inst().getAPI();
        for (ExItem exItem : api.getExItems()) {
            if (exItem.toItemStack().isSimilar(item)) {
                return exItem;
            }
        }
        File file = new File(api.getCustomItemsFile(), id);
        ItemsXL.inst().saveItemStack(file, item);
        api.reload();
        return api.getExItem(item);
    }
}