# ShopChestFinder

ShopChestFinder is a highly-performant, GUI-based addon for the [ShopChest](https://github.com/EpicEricEE/ShopChest) plugin. It provides a convenient way for players to search and find items being sold by other players across the server.

## Features

- **Global Shop GUI**: Players can browse all active shops on the server in a paginated GUI.
- **High-Performance Caching**: Shop data is cached asynchronously to ensure zero lag when opening menus or flipping pages, even with thousands of shops.
- **Item Grouping**: Consolidate shops selling the same items. Configurable options allow grouping purely by the base `Material` (ignoring custom lore/names) for specific items like Enchanted Books.
- **Exclusion Filters**: Filter out unwanted items from the GUI dynamically. You can completely exclude specific materials (e.g., `WRITTEN_BOOK`), or exclude them only if they are "vanilla" items without custom display names or lore (e.g., a standard `STICK`).
- **Privacy Controls**: By default, player shops are **private**. Players must opt-in to make their shops visible to the public or allow others to teleport to their shop coordinates.
- **Search Functionality**: Players can search for specific items by name/keyword within the GUI.
- **Admin Utilities**: Admins can bypass privacy settings to view all shops and can configure grouping/exclusion lists on the fly using in-game commands.

## Commands

- `/findshop`
  - Opens the main ShopChestFinder GUI.
  - *No permissions required.*
- `/findshop <page>`
  - Opens the GUI at a specific page number.
  - *No permissions required.*
- `/findshop search <keyword>`
  - Searches for shops selling items that match the given keyword.
  - *No permissions required.*
- `/findshop settings`
  - Opens a personal settings GUI where players can toggle:
    - **Visibility**: Whether their shops are shown in `/findshop` to others.
    - **TP Permission**: Whether other players can click their shops in the GUI to instantly teleport there.
  - *No permissions required.*
- `/findshop config <list_type> <add|remove> <Material>`
  - Modifies the plugin's configuration lists directly from in-game without needing a server restart or file edit.
  - **list_type** options:
    - `group-materials`: Materials here are grouped ignoring custom names/NBT.
    - `exclude-materials`: Materials here are completely hidden from the GUI.
    - `exclude-vanilla-materials`: Materials here are hidden only if they are standard vanilla items (no custom name/lore).
  - *Requires Permission: `shopchestfinder.admin`*

## Permissions

- `shopchestfinder.admin`
  - Allows usage of the `/findshop config` command.
  - Allows the admin to view all shops in the GUI (including private ones) and teleport to any shop regardless of the owner's TP settings.

## Configuration (`config.yml`)

```yaml
# If an item's Material is in this list, it will be grouped solely by its Material in the GUI.
# Custom names, lore, enchantments, and NBT tags will be ignored for these materials.
# (Note: Shulker Box contents and Map IDs are always ignored regardless of this setting)
group-materials:
  - ENCHANTED_BOOK
  - POTION

# If an item's Material is in this list, it will be completely excluded from the GUI.
# (e.g. all written books will be excluded)
exclude-materials:
  - WRITTEN_BOOK

# If an item's Material is in this list, it will be excluded ONLY IF it is a vanilla item.
# (i.e. it does not have a custom display name or custom lore)
exclude-vanilla-materials:
  - STICK
```

*(Note: Player privacy and teleport settings are automatically saved in `data.yml`.)*

## Requirements

- Minecraft Server (1.15+)
- Java 8+
- **[ShopChest](https://github.com/EpicEricEE/ShopChest)** plugin installed on the server.

## Building from source

1. Clone the repository.
2. Run `./gradlew build` (or `gradlew.bat build` on Windows).
3. The compiled jar will be located in the `build/libs/` directory.
