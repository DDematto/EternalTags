package xyz.oribuin.eternaltags.gui.menu;

import dev.rosewood.rosegarden.config.CommentedConfigurationSection;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.components.ScrollType;
import dev.triumphteam.gui.guis.BaseGui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import dev.triumphteam.gui.guis.ScrollingGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.oribuin.eternaltags.EternalTags;
import xyz.oribuin.eternaltags.action.Action;
import xyz.oribuin.eternaltags.gui.MenuItem;
import xyz.oribuin.eternaltags.gui.MenuProvider;
import xyz.oribuin.eternaltags.gui.PluginMenu;
import xyz.oribuin.eternaltags.gui.enums.SortType;
import xyz.oribuin.eternaltags.manager.ConfigurationManager.Setting;
import xyz.oribuin.eternaltags.manager.LocaleManager;
import xyz.oribuin.eternaltags.manager.TagsManager;
import xyz.oribuin.eternaltags.obj.Tag;
import xyz.oribuin.eternaltags.util.TagsUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FavouritesGUI extends PluginMenu {

    private final TagsManager manager = this.rosePlugin.getManager(TagsManager.class);
    private final LocaleManager locale = this.rosePlugin.getManager(LocaleManager.class);

    private final Map<String, ItemStack> tagItems = new LinkedHashMap<>(); // Cache the tag items, so we don't have to create them every time.
    private final List<Integer> tagSlots = new ArrayList<>();

    /**
     * Constructor for FavouritesGUI
     */
    public FavouritesGUI() {
        super(EternalTags.getInstance());
    }

    /**
     * Load the GUI configuration and tag slots
     */
    @Override
    public void load() {
        super.load();

        this.tagItems.clear();
        this.loadTagSlots();
    }

    /**
     * Load the tag slots from the configuration
     */
    private void loadTagSlots() {
        this.tagSlots.clear(); // clear the cache

        List<String> slotsConfig = this.config.getStringList("tag-item.slots");
        if (slotsConfig.isEmpty()) {
            int rows = this.config.getInt("gui-settings.rows", 6);
            for (int i = 0; i < rows * 9; i++) {
                this.tagSlots.add(i);
            }
        } else {
            for (String slotConfig : slotsConfig) {
                if (slotConfig.contains("-")) {
                    String[] range = slotConfig.split("-");
                    int start = Integer.parseInt(range[0]);
                    int end = Integer.parseInt(range[1]);
                    for (int i = start; i <= end; i++) {
                        this.tagSlots.add(i);
                    }
                } else {
                    this.tagSlots.add(Integer.parseInt(slotConfig));
                }
            }
        }
    }

    /**
     * Open the GUI for a player with an optional filter
     *
     * @param player The player to open the GUI for
     */
    public void open(@NotNull Player player) {
        String menuTitle = this.config.getString("gui-settings.title");
        if (menuTitle == null)
            menuTitle = "EternalTags | %page%/%total%";
        String finalMenuTitle = menuTitle;

        boolean scrollingGui = this.config.getBoolean("gui-settings.scrolling-gui", false);
        ScrollType scrollingType = TagsUtils.getEnum(
                ScrollType.class,
                this.config.getString("gui-settings.scrolling-type"),
                ScrollType.VERTICAL
        );

        PaginatedGui gui = (scrollingGui && scrollingType != null) ? this.createScrollingGui(player, scrollingType) : this.createPagedGUI(player);

        this.setupGuiLayout(gui);
        this.addExtraItems(gui, player);
        this.addFunctionalItems(gui, player);

        this.sync(() -> gui.open(player));

        Runnable task = () -> {
            gui.setPageSize(this.tagSlots.size());
            this.addTags(gui, player);

            if (this.reloadTitle())
                this.sync(() -> gui.updateTitle(this.formatString(player, finalMenuTitle, this.getPagePlaceholders(gui))));
        };

        if (this.addPagesAsynchronously())
            this.async(task);
        else
            task.run();

        this.addNavigationIcons(gui, player, finalMenuTitle);
    }

    /**
     * Set up the initial layout of the GUI
     *
     * @param gui The GUI to set up
     */
    private void setupGuiLayout(PaginatedGui gui) {
        int rows = this.config.getInt("gui-settings.rows", 6);
        int totalSlots = rows * 9;

        for (int i = 0; i < totalSlots; i++) {
            if (!this.tagSlots.contains(i)) {
                gui.setItem(i, new GuiItem(new ItemStack(Material.AIR)));
            }
        }
    }

    /**
     * Add extra items to the GUI
     *
     * @param gui    The GUI to add items to
     * @param player The player viewing the GUI
     */
    private void addExtraItems(PaginatedGui gui, Player player) {
        CommentedConfigurationSection extraItems = this.config.getConfigurationSection("extra-items");
        if (extraItems != null) {
            for (String key : extraItems.getKeys(false)) {
                MenuItem.create(this.config)
                        .path("extra-items." + key)
                        .player(player)
                        .action((item, event) -> item.sound((Player) event.getWhoClicked()))
                        .place(gui);
            }
        }
    }

    /**
     * Add functional items to the GUI
     *
     * @param gui    The GUI to add items to
     * @param player The player viewing the GUI
     */
    private void addFunctionalItems(PaginatedGui gui, Player player) {
        MenuItem.create(this.config)
                .path("clear-tag")
                .player(player)
                .action((item, event) -> {
                    item.sound((Player) event.getWhoClicked());
                    this.clearTag(player);
                })
                .place(gui);

        MenuItem.create(this.config)
                .path("categories")
                .player(player)
                .action((item, event) -> {
                    item.sound((Player) event.getWhoClicked());
                    MenuProvider.get(CategoryGUI.class).open(player);
                })
                .place(gui);

        MenuItem.create(this.config)
                .path("search")
                .player(player)
                .action((item, event) -> {
                    item.sound((Player) event.getWhoClicked());
                    this.searchTags(player, gui);
                })
                .place(gui);

        MenuItem.create(this.config)
                .path("main-menu")
                .player(player)
                .action((item, event) -> {
                    item.sound((Player) event.getWhoClicked());
                    if (Setting.OPEN_CATEGORY_GUI_FIRST.getBoolean()) {
                        MenuProvider.get(CategoryGUI.class).open(player);
                    } else {
                        MenuProvider.get(TagsGUI.class).open(player, null);
                    }
                })
                .place(gui);

        MenuItem.create(this.config)
                .path("reset-favourites")
                .player(player)
                .action(event -> {
                    if (event.getClick() == ClickType.DOUBLE_CLICK)
                        this.clearFavourites(player, gui);
                })
                .place(gui);
    }

    /**
     * Add navigation icons to the GUI
     *
     * @param gui            The GUI to add navigation icons to
     * @param player         The player viewing the GUI
     * @param finalMenuTitle The title of the GUI
     */
    private void addNavigationIcons(PaginatedGui gui, Player player, String finalMenuTitle) {

        MenuItem.create(this.config)
                .path("next-page")
                .player(player)
                .action(event -> {
                    gui.next();
                    this.sync(() -> gui.updateTitle(this.formatString(player, finalMenuTitle, this.getPagePlaceholders(gui))));
                })
                .player(player)
                .place(gui);

        MenuItem.create(this.config)
                .path("previous-page")
                .player(player)
                .action(event -> {
                    gui.previous();
                    this.sync(() -> gui.updateTitle(this.formatString(player, finalMenuTitle, this.getPagePlaceholders(gui))));
                })
                .place(gui);

        gui.update(); // Update the GUI to apply the changes.
    }

    /**
     * Add tags to the GUI
     *
     * @param gui    The GUI to add tags to
     * @param player The player viewing the GUI
     */
    private void addTags(@NotNull BaseGui gui, @NotNull Player player) {
        if (gui instanceof PaginatedGui paginatedGui) // Remove all items from the GUI
            paginatedGui.clearPageItems();

        if (gui instanceof ScrollingGui scrollingGui) // Remove all items from the GUI
            scrollingGui.clearPageItems();


        Map<ClickType, List<Action>> tagActions = this.getTagActions();
        for (Tag tag : this.getTags(player)) {
            GuiAction<InventoryClickEvent> action = event -> {

                // Make sure the player has permission to use the tag
                if (!this.manager.canUseTag(player, tag)) {
                    this.locale.sendMessage(player, "no-permission");
                    gui.close(player);
                    return;
                }

                // Run the tag actions
                if (!tagActions.isEmpty()) {
                    this.runActions(tagActions, event, this.getTagPlaceholders(tag, player));
                    gui.close(player);
                    return;
                }

                // If the player is shift clicking, toggle the favourite
                if (event.isShiftClick()) {
                    this.toggleFavourite(player, tag);
                    this.addTags(gui, player);
                    return;
                }

                // Set the tag
                this.setTag(player, tag);
                gui.close(player);
            };

            // If the tag is already in the cache, use that instead of creating a new one.
            if (Setting.CACHE_GUI_TAGS.getBoolean() && this.tagItems.containsKey(tag.getId())) {
                GuiItem item = new GuiItem(this.tagItems.get(tag.getId()));
                item.setAction(action);
                gui.addItem(item);
                continue;
            }

            GuiItem item = new GuiItem(this.getTagItem(player, tag), action);

            // Add the tag to the cache
            if (Setting.CACHE_GUI_TAGS.getBoolean())
                this.tagItems.put(tag.getId(), item.getItemStack());

            gui.addItem(item);

        }

        gui.update();
    }


    /**
     * Get all the tags that should be displayed in the GUI
     *
     * @param player The player to get the tags for
     * @return A list of tags
     */
    @NotNull
    private List<Tag> getTags(@NotNull Player player) {
        SortType sortType = TagsUtils.getEnum(SortType.class, this.config.getString("gui-settings.sort-type"));
        if (sortType == null)
            sortType = SortType.ALPHABETICAL;

        List<Tag> tags = new ArrayList<>(this.manager.getUsersFavourites(player.getUniqueId()).values());

        sortType.sort(tags);
        return tags;
    }

    /**
     * Change a player's active tag, and send the message to the player.
     *
     * @param player The player
     * @param tag    The tag
     */
    private void setTag(Player player, Tag tag) {
        Tag activeTag = this.manager.getUserTag(player);
        if (activeTag != null && activeTag.equals(tag) && Setting.RE_EQUIP_CLEAR.getBoolean()) {
            this.clearTag(player);
            return;
        }

        tag.equip(player);
        this.locale.sendMessage(player, "command-set-changed", StringPlaceholders.of("tag", this.manager.getDisplayTag(tag, player)));
    }

    /**
     * Toggle a player's favourite tag
     *
     * @param player The player
     * @param tag    The tag
     */
    private void toggleFavourite(Player player, Tag tag) {
        boolean isFavourite = this.manager.isFavourite(player.getUniqueId(), tag);

        if (isFavourite)
            this.manager.removeFavourite(player.getUniqueId(), tag);
        else
            this.manager.addFavourite(player.getUniqueId(), tag);


        String message = locale.getLocaleMessage(isFavourite ? "command-favorite-off" : "command-favorite-on");
        this.locale.sendMessage(player, "command-favorite-toggled", StringPlaceholders.builder("tag", this.manager.getDisplayTag(tag, player))
                .add("toggled", message)
                .build());
    }

    private void clearFavourites(Player player, BaseGui gui) {
        this.manager.clearFavourites(player.getUniqueId());
        this.locale.sendMessage(player, "command-favorite-cleared");
        this.close(gui, player);
    }

    /**
     * Get the name of the menu
     *
     * @return The name of the menu
     */
    @Override
    public String getMenuName() {
        return "favorites-gui";
    }
}