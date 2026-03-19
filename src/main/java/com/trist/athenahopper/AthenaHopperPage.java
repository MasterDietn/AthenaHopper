package com.athena.athenahopper;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AthenaHopperPage extends InteractiveCustomUIPage<AthenaHopperPage.AthenaHopperPageEventData> {
    private static final int MAX_FILTER_ITEMS = 20;
    private static final String PAGE_LAYOUT = "Pages/AthenaHopperPage.ui";
    private static final String ELEMENT_LAYOUT = "Pages/AthenaHopperItem.ui";
    private static final String ELEMENT_LIST_SELECTOR = "#ElementList";
    private static final String SELECTED_COUNT_SELECTOR = "#SelectedCount.Text";
    private static final String SELECTED_LIST_SELECTOR = "#SelectedList";
    private static final String SEARCH_INPUT_SELECTOR = "#SearchInput.Value";
    // Mode button text is already defined in the UI markup (AthenaHopperPage.ui),
    // and the correct selector/property may vary by the underlying UI template.
    // To avoid CustomUI selector mismatches (and server disconnects), we only update the mode status label.
    private static final String MODE_BUTTON_SELECTOR = "#ModeButton";
    private static final String MODE_STATUS_SELECTOR = "#ModeStatus.Text";
    private static final String INFO_LABEL_SELECTOR = "#InfoLabel.Text";
    private static final String CLEAR_BUTTON_SELECTOR = "#ClearButton";
    private static final String UNDO_BUTTON_SELECTOR = "#UndoButton";
    private static final String MODE_BUTTON_ROOT_SELECTOR = "#ModeButton";
    private static final String EMPTY_STATE_SELECTOR = "#EmptyState";
    private static final String HELP_TEXT =
            "Buttons: Modus = Erlauben/Blockieren; Auswahl leeren = Filter löschen; Rückgängig = letzte Änderung rückgängig\n" +
                    "Suche: listet nur passende Items.";

    private final UUID worldId;
    private final Vector3i position;
    private final List<Item> items;
    private String searchTerm;
    private String category;
    private SortMode sortMode;
    private FilterMode filterMode;
    private boolean categoryDropdownOpen;
    private boolean blacklistMode;
    private String lastActionItemId;
    private boolean lastActionWasAdd;

    public AthenaHopperPage(PlayerRef playerRef, UUID worldId, Vector3i position) {
        this(playerRef, worldId, position, null, null, SortMode.NAME_ASC, FilterMode.ALL, false);
    }

    public AthenaHopperPage(PlayerRef playerRef,
                            UUID worldId,
                            Vector3i position,
                            String searchTerm,
                            String category,
                            SortMode sortMode,
                            FilterMode filterMode,
                            boolean categoryDropdownOpen) {
        super(playerRef, CustomPageLifetime.CanDismiss, AthenaHopperPageEventData.CODEC);
        this.worldId = worldId;
        this.position = position.clone();
        this.items = buildItemList();
        this.searchTerm = normalize(searchTerm);
        this.category = (category == null || category.isBlank()) ? "All" : category;
        this.sortMode = SortMode.NAME_ASC;
        this.filterMode = filterMode == null ? FilterMode.ALL : filterMode;
        this.categoryDropdownOpen = categoryDropdownOpen;
        this.items.sort(this.sortMode.getComparator());
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append(PAGE_LAYOUT);
        commandBuilder.clear(ELEMENT_LIST_SELECTOR);

        this.items.sort(this.sortMode.getComparator());
        AthenaHopperBlockState funnelState = getFunnelState();
        if (funnelState != null) {
            this.blacklistMode = funnelState.isBlacklistMode();
        }
        int selectedCount = funnelState == null ? 0 : funnelState.getFilterItems().length;
        commandBuilder.set(SELECTED_COUNT_SELECTOR, buildSelectedCountText(selectedCount));
        commandBuilder.set(SEARCH_INPUT_SELECTOR, searchTerm == null ? "" : searchTerm);
        commandBuilder.set(MODE_STATUS_SELECTOR, buildModeStatusText());
        commandBuilder.set(INFO_LABEL_SELECTOR, HELP_TEXT);

        boolean showEmptyState = searchTerm == null || searchTerm.isBlank();
        commandBuilder.set(ELEMENT_LIST_SELECTOR + ".Visible", !showEmptyState);
        commandBuilder.set(EMPTY_STATE_SELECTOR + ".Visible", showEmptyState);

        buildSelectedList(commandBuilder, funnelState);

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String selector = elementSelector(i);
            commandBuilder.append(ELEMENT_LIST_SELECTOR, ELEMENT_LAYOUT);
            commandBuilder.set(selector + " #Icon.ItemId", item.getId());
            commandBuilder.set(selector + " #Name.TextSpans", Message.translation(item.getTranslationKey()));
            int count = countSelected(funnelState, item);
            commandBuilder.set(selector + " #Selected.Text", count > 0 ? Integer.toString(count) : "");
            commandBuilder.set(selector + ".Visible", isItemVisible(item));

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of(AthenaHopperPageEventData.ELEMENT_INDEX, Integer.toString(i))
                            .append(AthenaHopperPageEventData.ACTION, "inc"),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.RightClicking,
                    selector,
                    EventData.of(AthenaHopperPageEventData.ELEMENT_INDEX, Integer.toString(i))
                            .append(AthenaHopperPageEventData.ACTION, "dec"),
                    false
            );
        }

        for (int i = 0; i < MAX_FILTER_ITEMS; i++) {
            String selector = SELECTED_LIST_SELECTOR + "[" + i + "]";
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of(AthenaHopperPageEventData.ACTION, "dec_selected")
                            .append(AthenaHopperPageEventData.ELEMENT_INDEX, Integer.toString(i)),
                    false
            );
        }

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of(AthenaHopperPageEventData.ACTION, "search")
                        .append(AthenaHopperPageEventData.SEARCH_INPUT, SEARCH_INPUT_SELECTOR)
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                MODE_BUTTON_ROOT_SELECTOR,
                EventData.of(AthenaHopperPageEventData.ACTION, "toggle_mode"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                CLEAR_BUTTON_SELECTOR,
                EventData.of(AthenaHopperPageEventData.ACTION, "clear_all"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                UNDO_BUTTON_SELECTOR,
                EventData.of(AthenaHopperPageEventData.ACTION, "undo"),
                false
        );
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, AthenaHopperPageEventData data) {
        String action = data.getAction();
        if ("dec_selected".equalsIgnoreCase(action)) {
            handleSelectedListClick(data.getIndex());
            return;
        }
        if (action != null && !action.equalsIgnoreCase("inc") && !action.equalsIgnoreCase("dec")) {
            handleAction(ref, store, data);
            return;
        }

        int index = data.getIndex();
        if (index < 0 || index >= items.size()) {
            return;
        }

        AthenaHopperBlockState funnelState = getFunnelState();
        if (funnelState == null) {
            close();
            return;
        }

        String itemId = items.get(index).getId();
        List<String> selected = new ArrayList<>(Arrays.asList(funnelState.getFilterItems()));
        if ("dec".equalsIgnoreCase(action)) {
            removeOne(selected, itemId);
            lastActionItemId = itemId;
            lastActionWasAdd = false;
        } else {
            if (selected.size() >= MAX_FILTER_ITEMS) {
                playerRef.sendMessage(Message.raw("Du kannst nur 20 auswählen"));
                return;
            }
            selected.add(itemId);
            lastActionItemId = itemId;
            lastActionWasAdd = true;
        }

        funnelState.setFilterItems(selected.toArray(new String[0]));

        UICommandBuilder updates = new UICommandBuilder();
        updates.set(SELECTED_COUNT_SELECTOR, buildSelectedCountText(selected.size()));
        updates.set(elementSelector(index) + " #Selected.Text", Integer.toString(countInList(selected, itemId)));
        updateSelectedList(updates, funnelState);
        sendUpdate(updates);
    }

    private void handleSelectedListClick(int selectedIndex) {
        AthenaHopperBlockState funnelState = getFunnelState();
        if (funnelState == null) {
            close();
            return;
        }
        List<String> selected = new ArrayList<>(Arrays.asList(funnelState.getFilterItems()));
        java.util.LinkedHashMap<String, Integer> counts = buildSelectedCounts(funnelState);
        if (selectedIndex < 0 || selectedIndex >= counts.size()) {
            return;
        }
        String targetId = new ArrayList<>(counts.keySet()).get(selectedIndex);
        removeOne(selected, targetId);
        lastActionItemId = targetId;
        lastActionWasAdd = false;
        funnelState.setFilterItems(selected.toArray(new String[0]));

        UICommandBuilder updates = new UICommandBuilder();
        updates.set(SELECTED_COUNT_SELECTOR, buildSelectedCountText(selected.size()));
        int itemIndex = findItemIndex(targetId);
        if (itemIndex >= 0) {
            updates.set(elementSelector(itemIndex) + " #Selected.Text", Integer.toString(countInList(selected, targetId)));
        }
        updateSelectedList(updates, funnelState);
        sendUpdate(updates);
    }

    private int findItemIndex(String itemId) {
        if (itemId == null) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            if (itemId.equals(items.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private void handleAction(Ref<EntityStore> ref, Store<EntityStore> store, AthenaHopperPageEventData data) {
        String action = data.getAction();
        if (action == null) {
            return;
        }
        if ("search".equalsIgnoreCase(action)) {
            String nextSearch = normalize(data.getSearchInput());
            this.searchTerm = nextSearch;
            sendFilterUpdate();
            return;
        }
        // Hover actions intentionally removed:
        // We no longer show item info on mouse hover.

        AthenaHopperBlockState funnelState = getFunnelState();
        if (funnelState == null) {
            close();
            return;
        }

        if ("toggle_mode".equalsIgnoreCase(action)) {
            blacklistMode = !blacklistMode;
            funnelState.setBlacklistMode(blacklistMode);
            UICommandBuilder updates = new UICommandBuilder();
            updateModeLabels(updates);
            sendUpdate(updates);
            return;
        }

        if ("clear_all".equalsIgnoreCase(action)) {
            funnelState.setFilterItems(new String[0]);
            lastActionItemId = null;
            lastActionWasAdd = false;
            UICommandBuilder updates = new UICommandBuilder();
            updateAllCounts(updates, funnelState);
            updateSelectedList(updates, funnelState);
            updates.set(SELECTED_COUNT_SELECTOR, buildSelectedCountText(0));
            sendUpdate(updates);
            return;
        }

        if ("undo".equalsIgnoreCase(action)) {
            if (lastActionItemId == null) {
                return;
            }
            List<String> selected = new ArrayList<>(Arrays.asList(funnelState.getFilterItems()));
            if (lastActionWasAdd) {
                removeOne(selected, lastActionItemId);
            } else {
                if (selected.size() >= MAX_FILTER_ITEMS) {
                    playerRef.sendMessage(Message.raw("Du kannst nur 20 auswählen"));
                    return;
                }
                selected.add(lastActionItemId);
            }
            funnelState.setFilterItems(selected.toArray(new String[0]));
            lastActionItemId = null;
            lastActionWasAdd = false;

            UICommandBuilder updates = new UICommandBuilder();
            updateAllCounts(updates, funnelState);
            updateSelectedList(updates, funnelState);
            updates.set(SELECTED_COUNT_SELECTOR, buildSelectedCountText(selected.size()));
            sendUpdate(updates);
            return;
        }
    }

    private String buildSelectedCountText(int selectedCount) {
        return "Ausgew\u00e4hlt: " + selectedCount + "/" + MAX_FILTER_ITEMS;
    }

    private String buildModeStatusText() {
        return blacklistMode ? "Modus: Blockieren" : "Modus: Erlauben";
    }

    private void updateModeLabels(UICommandBuilder updates) {
        updates.set(MODE_STATUS_SELECTOR, buildModeStatusText());
    }

    private void updateAllCounts(UICommandBuilder updates, AthenaHopperBlockState funnelState) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int count = countSelected(funnelState, item);
            updates.set(elementSelector(i) + " #Selected.Text", count > 0 ? Integer.toString(count) : "");
        }
    }

    private String buildInfoText(Item item) {
        if (item == null) {
            return "";
        }
        String name = getDisplayName(item);
        String id = item.getId() == null ? "" : item.getId();
        StringBuilder catBuilder = new StringBuilder();
        String[] cats = item.getCategories();
        if (cats != null) {
            for (String c : cats) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                if (catBuilder.length() > 0) {
                    catBuilder.append(", ");
                }
                catBuilder.append(c);
            }
        }
        String catsText = catBuilder.length() == 0 ? "-" : catBuilder.toString();
        return name + " | ID: " + id + " | Kategorien: " + catsText + " | Stack: " + item.getMaxStack();
    }

    private String elementSelector(int index) {
        return ELEMENT_LIST_SELECTOR + "[" + index + "]";
    }

    private boolean isSelected(AthenaHopperBlockState funnelState, Item item) {
        if (funnelState == null) {
            return false;
        }
        String id = item.getId();
        for (String allowed : funnelState.getFilterItems()) {
            if (allowed != null && id != null && (allowed.equals(id) || allowed.equalsIgnoreCase(id))) {
                return true;
            }
        }
        return false;
    }

    private AthenaHopperBlockState getFunnelState() {
        World world = Universe.get().getWorld(worldId);
        if (world == null) {
            return null;
        }
        BlockState state = world.getState(position.x, position.y, position.z, true);
        if (state instanceof AthenaHopperBlockState funnelState && funnelState.isFilterMode()) {
            return funnelState;
        }
        return null;
    }

    private List<Item> buildItemList() {
        List<Item> list = new ArrayList<>(Item.getAssetMap().getAssetMap().values());
        list.remove(Item.UNKNOWN);
        return list;
    }

    private boolean isItemVisible(Item item) {
        // Only show items when the player actually types something into the search bar.
        if (searchTerm == null || searchTerm.isBlank()) {
            return false;
        }
        String search = normalize(searchTerm);
        String cat = category;
        if (search != null && !matchesSearch(item, search)) {
            return false;
        }
        if (!"All".equalsIgnoreCase(cat) && !matchesCategory(item, cat)) {
            return false;
        }
        if (!matchesFilter(item)) {
            return false;
        }
        return !isCreativeBuildingTool(item);
    }

    private boolean matchesSearch(Item item, String search) {
        String id = item.getId() == null ? "" : item.getId().toLowerCase();
        String name = getDisplayName(item).toLowerCase();
        return id.contains(search) || name.contains(search);
    }

    private boolean matchesCategory(Item item, String cat) {
        String[] cats = item.getCategories();
        if (cats == null) {
            return false;
        }
        for (String c : cats) {
            if (c == null) {
                continue;
            }
            if (c.equalsIgnoreCase(cat)) {
                return true;
            }
            if (c.startsWith(cat + ".")) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildCategoryList() {
        List<String> categories = new ArrayList<>();
        categories.add("All");
        for (Item item : items) {
            String[] cats = item.getCategories();
            if (cats == null) {
                continue;
            }
            for (String c : cats) {
                if (c == null) {
                    continue;
                }
                String top = c.split("\\.")[0];
                if (!categories.contains(top)) {
                    categories.add(top);
                }
            }
        }
        categories.removeIf(c -> c.equalsIgnoreCase("Fish") || c.equalsIgnoreCase("Tool") || c.equalsIgnoreCase("Upgrade"));
        categories.sort(String::compareToIgnoreCase);
        return categories;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matchesFilter(Item item) {
        if (filterMode == FilterMode.ALL) {
            return true;
        }
        String blockId = item.getBlockId();
        boolean isBlock = blockId != null && !blockId.isBlank();
        if (filterMode == FilterMode.BLOCKS) {
            return isBlock;
        }
        if (filterMode == FilterMode.ITEMS) {
            return !isBlock;
        }
        if (filterMode == FilterMode.CRAFTING) {
            String[] cats = item.getCategories();
            if (cats == null) {
                return false;
            }
            for (String c : cats) {
                if (c == null) {
                    continue;
                }
                if (c.startsWith("Crafting") || c.startsWith("Ingredient") || c.startsWith("Resources")) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private void sendFilterUpdate() {
        UICommandBuilder updates = new UICommandBuilder();

        this.items.sort(this.sortMode.getComparator());
        boolean showEmptyState = searchTerm == null || searchTerm.isBlank();
        updates.set(ELEMENT_LIST_SELECTOR + ".Visible", !showEmptyState);
        updates.set(EMPTY_STATE_SELECTOR + ".Visible", showEmptyState);

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String selector = elementSelector(i);
            updates.set(selector + ".Visible", isItemVisible(item));
        }

        sendUpdate(updates);
    }

    private void buildSelectedList(UICommandBuilder builder, AthenaHopperBlockState funnelState) {
        builder.clear(SELECTED_LIST_SELECTOR);
        for (int i = 0; i < MAX_FILTER_ITEMS; i++) {
            builder.append(SELECTED_LIST_SELECTOR, "Pages/AthenaHopperSelectedItem.ui");
        }
        updateSelectedList(builder, funnelState);
    }

    private void updateSelectedList(UICommandBuilder builder, AthenaHopperBlockState funnelState) {
        java.util.List<java.util.Map.Entry<String, Integer>> entries = new java.util.ArrayList<>();
        if (funnelState != null) {
            java.util.LinkedHashMap<String, Integer> counts = buildSelectedCounts(funnelState);
            entries.addAll(counts.entrySet());
        }
        for (int i = 0; i < MAX_FILTER_ITEMS; i++) {
            String selector = SELECTED_LIST_SELECTOR + "[" + i + "]";
            if (i >= entries.size()) {
                builder.set(selector + ".Visible", false);
                builder.set(selector + " #SelectedItem.Text", "");
                builder.set(selector + " #SelectedItem.TextSpans", Message.translation(""));
                builder.set(selector + " #SelectedItemSuffix.Text", "");
                continue;
            }
            java.util.Map.Entry<String, Integer> entry = entries.get(i);
            Item item = Item.getAssetMap().getAssetMap().get(entry.getKey());
            builder.set(selector + ".Visible", true);
            builder.set(selector + " #SelectedIcon.ItemId", entry.getKey());
            if (item != null && item.getTranslationKey() != null) {
                builder.set(selector + " #SelectedItem.Text", "");
                builder.set(selector + " #SelectedItem.TextSpans", Message.translation(item.getTranslationKey()));
            } else {
                builder.set(selector + " #SelectedItem.TextSpans", Message.translation(""));
                builder.set(selector + " #SelectedItem.Text", getDisplayNameOrId(item, entry.getKey()));
            }
            builder.set(selector + " #SelectedItemSuffix.Text", " x" + entry.getValue());
        }
    }

    private String getDisplayNameOrId(Item item, String itemId) {
        if (item != null) {
            return getDisplayName(item);
        }
        if (itemId == null) {
            return "";
        }
        return prettifyKey(itemId);
    }

    private java.util.LinkedHashMap<String, Integer> buildSelectedCounts(AthenaHopperBlockState funnelState) {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (funnelState == null) {
            return counts;
        }
        String[] selected = funnelState.getFilterItems();
        if (selected == null) {
            return counts;
        }
        for (String id : selected) {
            if (id == null) {
                continue;
            }
            counts.put(id, counts.getOrDefault(id, 0) + 1);
        }
        return counts;
    }

    private int countSelected(AthenaHopperBlockState state, Item item) {
        if (state == null || item == null) {
            return 0;
        }
        return countInList(Arrays.asList(state.getFilterItems()), item.getId());
    }

    private int countInList(List<String> list, String itemId) {
        int count = 0;
        for (String id : list) {
            if (id != null && itemId != null && (id.equals(itemId) || id.equalsIgnoreCase(itemId))) {
                count++;
            }
        }
        return count;
    }

    private void removeOne(List<String> list, String itemId) {
        for (int i = 0; i < list.size(); i++) {
            if (itemId.equals(list.get(i))) {
                list.remove(i);
                return;
            }
        }
    }

    private String getDisplayName(Item item) {
        if (item == null) {
            return "";
        }
        String translated = getTranslatedText(item);
        if (translated != null && !translated.isBlank()) {
            return translated;
        }
        if (item.getTranslationProperties() != null && item.getTranslationProperties().getName() != null) {
            String name = item.getTranslationProperties().getName();
            if (name != null && !name.startsWith("server.items.")) {
                return name;
            }
        }
        if (item.getTranslationKey() != null) {
            return prettifyKey(item.getTranslationKey());
        }
        return item.getId() == null ? "" : item.getId();
    }

    private static String getTranslatedText(Item item) {
        if (item == null || item.getTranslationKey() == null) {
            return null;
        }
        Message msg = Message.translation(item.getTranslationKey());
        String raw = msg == null ? null : msg.getRawText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("server.items.")) {
            return null;
        }
        return raw;
    }

    private static String getSortKey(Item item) {
        String translated = getTranslatedText(item);
        if (translated != null && !translated.isBlank()) {
            return translated;
        }
        if (item != null && item.getTranslationProperties() != null && item.getTranslationProperties().getName() != null) {
            return item.getTranslationProperties().getName();
        }
        if (item != null && item.getTranslationKey() != null) {
            return prettifyKey(item.getTranslationKey());
        }
        return item == null || item.getId() == null ? "" : item.getId();
    }

    private static String prettifyKey(String key) {
        if (key == null) {
            return "";
        }
        String base = key;
        if (base.endsWith(".name")) {
            base = base.substring(0, base.length() - 5);
        }
        int idx = base.lastIndexOf('.');
        if (idx >= 0) {
            base = base.substring(idx + 1);
        }
        base = base.replace('_', ' ').trim();
        return base.isEmpty() ? key : base;
    }


    private boolean isCreativeBuildingTool(Item item) {
        if (item == null) {
            return false;
        }
        String id = item.getId() == null ? "" : item.getId().toLowerCase();
        String key = item.getTranslationKey() == null ? "" : item.getTranslationKey().toLowerCase();
        if (id.contains("builder") && id.contains("tool")) {
            return true;
        }
        if (key.contains("builder") && key.contains("tool")) {
            return true;
        }
        String[] cats = item.getCategories();
        if (cats == null) {
            return false;
        }
        for (String c : cats) {
            if (c == null) {
                continue;
            }
            String lower = c.toLowerCase();
            if (lower.contains("buildingtools") || lower.contains("buildertools") || lower.contains("creative")) {
                return true;
            }
        }
        return false;
    }

    public static class AthenaHopperPageEventData {
        static final String ELEMENT_INDEX = "Index";
        static final String ACTION = "Action";
        static final String SEARCH_INPUT = "@SearchInput";
        static final String CATEGORY = "Category";
        static final String FILTER = "Filter";
        public static final BuilderCodec<AthenaHopperPageEventData> CODEC;

        private int index;
        private String indexStr;
        private String action;
        private String searchInput;
        private String category;
        private String filter;

        public AthenaHopperPageEventData() {
        }

        public int getIndex() {
            return index;
        }

        public String getAction() {
            return action;
        }

        public String getSearchInput() {
            return searchInput;
        }

        public String getCategory() {
            return category;
        }

        public String getFilter() {
            return filter;
        }

        private void setIndexStr(String indexStr) {
            this.indexStr = indexStr;
            try {
                this.index = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                // If the client sends an invalid payload, ignore the click to avoid crashing the server.
                this.index = -1;
            }
        }

        private String getIndexStr() {
            return indexStr;
        }

        private void setAction(String action) {
            this.action = action;
        }

        private void setSearchInput(String searchInput) {
            this.searchInput = searchInput;
        }

        private void setCategory(String category) {
            this.category = category;
        }

        private void setFilter(String filter) {
            this.filter = filter;
        }

        static {
            CODEC = BuilderCodec.builder(AthenaHopperPageEventData.class, AthenaHopperPageEventData::new)
                    .append(new KeyedCodec<>(ELEMENT_INDEX, Codec.STRING),
                            (data, value) -> data.setIndexStr(value),
                            AthenaHopperPageEventData::getIndexStr)
                    .add()
                    .append(new KeyedCodec<>(ACTION, Codec.STRING),
                            (data, value) -> data.setAction(value),
                            AthenaHopperPageEventData::getAction)
                    .add()
                    .append(new KeyedCodec<>(SEARCH_INPUT, Codec.STRING),
                            (data, value) -> data.setSearchInput(value),
                            AthenaHopperPageEventData::getSearchInput)
                    .add()
                    .append(new KeyedCodec<>(CATEGORY, Codec.STRING),
                            (data, value) -> data.setCategory(value),
                            AthenaHopperPageEventData::getCategory)
                    .add()
                    .append(new KeyedCodec<>(FILTER, Codec.STRING),
                            (data, value) -> data.setFilter(value),
                            AthenaHopperPageEventData::getFilter)
                    .add()
                    .build();
        }
    }

    private enum SortMode {
        NAME_ASC("Name A-Z", Comparator.comparing(AthenaHopperPage::getSortKey, String.CASE_INSENSITIVE_ORDER)),
        NAME_DESC("Name Z-A", Comparator.comparing(AthenaHopperPage::getSortKey, String.CASE_INSENSITIVE_ORDER).reversed()),
        ID_ASC("ID A-Z", Comparator.comparing(Item::getId, Comparator.nullsLast(String::compareToIgnoreCase))),
        ID_DESC("ID Z-A", Comparator.comparing(Item::getId, Comparator.nullsLast(String::compareToIgnoreCase)).reversed());

        private final String label;
        private final Comparator<Item> comparator;

        SortMode(String label, Comparator<Item> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        public String getLabel() {
            return label;
        }

        public Comparator<Item> getComparator() {
            return comparator;
        }

        public SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private enum FilterMode {
        ALL,
        BLOCKS,
        ITEMS,
        CRAFTING;

        public static FilterMode fromString(String value) {
            if (value == null) {
                return ALL;
            }
            for (FilterMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return ALL;
        }
    }
}

