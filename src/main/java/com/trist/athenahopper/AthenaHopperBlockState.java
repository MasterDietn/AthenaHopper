package com.athena.athenahopper;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.EntityChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.builtin.crafting.state.ProcessingBenchState;

import java.util.Iterator;
import java.util.List;

public class AthenaHopperBlockState extends ItemContainerState implements TickableBlockState {
    public static final Codec<AthenaHopperBlockState> CODEC;

    protected static final Vector3i[] ROTATION_OFFSET;
    protected static final short PUSH_COOLDOWN = 12;

    public short cooldown;
    protected Vector3i abovePos;
    protected Vector3i frontPos;
    protected boolean isInitialized;
    protected boolean filter;
    protected boolean blacklistMode;
    protected boolean isEnabled;
    protected String[] filterItems;

    public AthenaHopperBlockState() {
        this.cooldown = 0;
        this.isInitialized = false;
        this.filter = false;
        this.blacklistMode = false;
        this.isEnabled = true;
        this.filterItems = new String[0];
    }

    public boolean isFilterMode() {
        return filter;
    }

    public boolean isBlacklistMode() {
        return blacklistMode;
    }

    public void setBlacklistMode(boolean value) {
        this.blacklistMode = value;
        if (getChunk() != null) {
            markNeedsSave();
        }
    }

    private void setBlacklistModeRaw(boolean value) {
        this.blacklistMode = value;
    }

    public String[] getFilterItems() {
        return filterItems == null ? new String[0] : filterItems;
    }

    public void setFilterItems(String[] filterItems) {
        this.filterItems = filterItems == null ? new String[0] : filterItems;
        if (getChunk() != null) {
            markNeedsSave();
        }
    }

    private void setFilterItemsRaw(String[] filterItems) {
        this.filterItems = filterItems == null ? new String[0] : filterItems;
    }

    private boolean isCustom() {
        return custom;
    }

    private void setCustomRaw(boolean value) {
        this.custom = value;
    }

    @Override
    public void setCustom(boolean value) {
        this.custom = value;
        if (getChunk() != null) {
            markNeedsSave();
        }
    }

    private void setAllowViewingRaw(boolean value) {
        this.allowViewing = value;
    }

    @Override
    public void setAllowViewing(boolean value) {
        this.allowViewing = value;
        if (getChunk() != null) {
            markNeedsSave();
        }
    }

    private String getDroplistRaw() {
        return droplist;
    }

    private void setDroplistRaw(String value) {
        this.droplist = value;
    }

    @Override
    public void setDroplist(String value) {
        this.droplist = value;
        if (getChunk() != null) {
            markNeedsSave();
        }
    }

    private SimpleItemContainer getSimpleItemContainer() {
        return itemContainer;
    }

    private void setSimpleItemContainer(SimpleItemContainer container) {
        this.itemContainer = container;
    }

    @Override
    public boolean initialize(BlockType blockType) {
        if (!super.initialize(blockType)) {
            return false;
        }

        Vector3i pos = getBlockPosition();
        this.abovePos = pos.clone().add(0, 1, 0);
        this.frontPos = pos.clone().add(ROTATION_OFFSET[getRotationIndex()]);

        StateData stateData = blockType.getState();
        if (stateData instanceof AthenaHopperBlockStateData data) {
            this.filter = data.isFilterMode();
        }

        // When filter mode is enabled we still allow the normal container UI,
        // so players can insert items into the hopper.
        // The custom UI is only opened on Primary interaction from the plugin.

        this.isInitialized = true;
        return true;
    }

    @Override
    public void tick(float deltaSeconds, int tickCount,
                     ArchetypeChunk<ChunkStore> archetypeChunk,
                     Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> commandBuffer) {
        if (!isInitialized || !isEnabled || frontPos == null || abovePos == null) {
            return;
        }

        if (cooldown > 0) {
            cooldown = (short) (cooldown - 1);
            return;
        }

        World world = ((ChunkStore) store.getExternalData()).getWorld();

        BlockState frontState = world.getState(frontPos.x, frontPos.y, frontPos.z, true);
        BlockState aboveState = world.getState(abovePos.x, abovePos.y, abovePos.z, true);

        if (frontState != null && !itemContainer.isEmpty()) {
            boolean moved = pushTo(frontState);
            if (moved) {
                cooldown = PUSH_COOLDOWN;
                if (frontState instanceof AthenaHopperBlockState other && other.cooldown == 0) {
                    other.cooldown = PUSH_COOLDOWN;
                } else if (frontState instanceof ProcessingBenchState benchState) {
                    CombinedItemContainer combined = benchState.getItemContainer();
                    ItemContainer input = combined.getContainer(1);
                    world.execute(() -> {
                        if (!input.isEmpty() && !benchState.isActive()) {
                            benchState.setActive(true);
                        }
                    });
                }
            }
        }

        if (aboveState != null) {
            boolean moved = pullFrom(aboveState);
            if (moved) {
                cooldown = PUSH_COOLDOWN;
            }
            return;
        }

        collectItems(world);
    }

    protected boolean pushTo(BlockState targetState) {
        if (targetState instanceof ProcessingBenchState benchState) {
            CombinedItemContainer benchContainer = benchState.getItemContainer();
            ItemContainer input = benchContainer.getContainer(0);
            ItemContainer output = benchContainer.getContainer(1);
            CombinedItemContainer combined = new CombinedItemContainer(new ItemContainer[]{input, output});
            return moveFirstItemFromTo(itemContainer, combined, filter);
        }

        if (targetState instanceof ItemContainerState containerState) {
            ItemContainer target = containerState.getItemContainer();
            return moveFirstItemFromTo(itemContainer, target, filter);
        }

        return false;
    }

    protected boolean pullFrom(BlockState sourceState) {
        if (sourceState instanceof ProcessingBenchState benchState) {
            CombinedItemContainer benchContainer = benchState.getItemContainer();
            ItemContainer output = benchContainer.getContainer(2);
            return moveFirstItemFromTo(output, itemContainer);
        }

        if (sourceState instanceof ItemContainerState containerState) {
            ItemContainer source = containerState.getItemContainer();
            if (!source.isEmpty()) {
                return moveFirstItemFromTo(source, itemContainer, filter);
            }
        }

        return false;
    }

    protected void collectItems(World world) {
        SimpleItemContainer container = itemContainer;
        WorldChunk chunk = getChunk();
        if (chunk == null || abovePos == null) {
            return;
        }

        EntityChunk entityChunk = chunk.getEntityChunk();
        if (entityChunk == null) {
            return;
        }

        List<Ref<EntityStore>> refs = entityChunk.getEntityReferences().stream().toList();
        Iterator<Ref<EntityStore>> iterator = refs.iterator();
        while (iterator.hasNext()) {
            Ref<EntityStore> ref = iterator.next();
            Store<EntityStore> store = ref.getStore();
            ItemComponent itemComponent = store.getComponent(ref, ItemComponent.getComponentType());
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
            if (itemComponent == null || transform == null) {
                continue;
            }

            ItemStack stack = itemComponent.getItemStack();
            if (stack == null || !isItemAllowed(stack)) {
                continue;
            }

            Vector3d itemPos = transform.getPosition();
            Vector3d aboveCenter = new Vector3d(abovePos).add(0.5d, 0.0d, 0.5d);
            boolean closeEnough = itemPos.clone().subtract(aboveCenter).closeToZero(0.5d);
            if (!closeEnough) {
                continue;
            }

            boolean canFit = false;
            for (short i = 0; i < container.getCapacity(); i++) {
                ItemStack slot = container.getItemStack(i);
                if (slot == null || slot.isEmpty()) {
                    canFit = true;
                    break;
                }
                if (slot.isStackableWith(stack)) {
                    Item slotItem = slot.getItem();
                    if (slot.getQuantity() < slotItem.getMaxStack()) {
                        canFit = true;
                        break;
                    }
                }
            }

            if (!canFit) {
                continue;
            }

            world.execute(() -> {
                ItemStackTransaction tx = container.addItemStack(stack);
                ItemStack remainder = tx.getRemainder();
                if (!tx.succeeded()) {
                    return;
                }

                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                entityStore.removeEntity(ref, RemoveReason.REMOVE);

                if (remainder != null && !remainder.isEmpty()) {
                    Vector3f velocityVec = new Vector3f(0f, 0f, 0f);
                    if (velocity != null) {
                        velocityVec.add((float) velocity.getX(), (float) velocity.getY(), (float) velocity.getZ());
                    }
                    Holder<EntityStore> drop = ItemComponent.generateItemDrop(
                            entityStore,
                            remainder,
                            itemPos,
                            transform.getRotation(),
                            velocityVec.x,
                            velocityVec.y,
                            velocityVec.z
                    );
                    if (drop != null) {
                        entityStore.addEntity(drop, AddReason.SPAWN);
                    }
                }
            });
        }
    }

    protected boolean moveFirstItemFromTo(ItemContainer from, ItemContainer to) {
        return moveFirstItemFromTo(from, to, false);
    }

    protected boolean moveFirstItemFromTo(ItemContainer from, ItemContainer to, boolean applyFilter) {
        for (short i = 0; i < from.getCapacity(); i++) {
            ItemStack stack = from.getItemStack(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (applyFilter && !isItemAllowed(stack)) {
                continue;
            }

            ItemStack single = stack.withQuantity(1);
            if (!to.canAddItemStack(single)) {
                continue;
            }

            ItemStackTransaction tx = to.addItemStack(single);
            if (!tx.succeeded()) {
                continue;
            }

            from.setItemStackForSlot(i, stack.withQuantity(stack.getQuantity() - 1), false);
            return true;
        }
        return false;
    }

    protected boolean isItemAllowed(ItemStack stack) {
        if (!filter) {
            return true;
        }

        String[] allowed = getFilterItems();
        if (allowed.length == 0) {
            return blacklistMode;
        }

        String id = stack.getItemId();
        boolean contains = false;
        for (String allowedId : allowed) {
            if (allowedId != null && id != null && (allowedId.equals(id) || allowedId.equalsIgnoreCase(id))) {
                contains = true;
                break;
            }
        }
        return blacklistMode ? !contains : contains;
    }

    static {
        ROTATION_OFFSET = new Vector3i[]{
                new Vector3i(0, 0, -1),
                new Vector3i(-1, 0, 0),
                new Vector3i(0, 0, 1),
                new Vector3i(1, 0, 0)
        };

        CODEC = BuilderCodec.builder(AthenaHopperBlockState.class, AthenaHopperBlockState::new, ItemContainerState.BASE_CODEC)
                .append(new KeyedCodec<>("ItemContainer", SimpleItemContainer.CODEC),
                        AthenaHopperBlockState::setSimpleItemContainer,
                        AthenaHopperBlockState::getSimpleItemContainer)
                .add()
                .append(new KeyedCodec<>("Droplist", Codec.STRING),
                        AthenaHopperBlockState::setDroplistRaw,
                        AthenaHopperBlockState::getDroplistRaw)
                .add()
                .append(new KeyedCodec<>("Custom", Codec.BOOLEAN),
                        AthenaHopperBlockState::setCustomRaw,
                        AthenaHopperBlockState::isCustom)
                .add()
                .append(new KeyedCodec<>("AllowViewing", Codec.BOOLEAN),
                        AthenaHopperBlockState::setAllowViewingRaw,
                        AthenaHopperBlockState::isAllowViewing)
                .add()
                .append(new KeyedCodec<>("FilterItems", Codec.STRING_ARRAY),
                        AthenaHopperBlockState::setFilterItemsRaw,
                        AthenaHopperBlockState::getFilterItems)
                .add()
                .append(new KeyedCodec<>("BlacklistMode", Codec.BOOLEAN),
                        AthenaHopperBlockState::setBlacklistModeRaw,
                        AthenaHopperBlockState::isBlacklistMode)
                .add()
                .build();
    }

    public static class AthenaHopperBlockStateData extends ItemContainerStateData {
        public static final BuilderCodec<AthenaHopperBlockStateData> CODEC;
        protected short capacity;
        protected boolean filterMode;

        protected AthenaHopperBlockStateData() {
        }

        public short getCapacity() {
            return capacity;
        }

        public boolean isFilterMode() {
            return filterMode;
        }

        @Override
        public String toString() {
            return "AthenaHopperBlockStateData{capacity=" + capacity + ", isFilter=" + filterMode + "} " + super.toString();
        }

        static {
            CODEC = BuilderCodec.builder(AthenaHopperBlockStateData.class, AthenaHopperBlockStateData::new, ItemContainerStateData.DEFAULT_CODEC)
                    .appendInherited(new KeyedCodec<>("Capacity", Codec.INTEGER),
                            (data, value) -> data.capacity = value.shortValue(),
                            data -> (int) data.capacity,
                            (parent, child) -> child.capacity = parent.capacity)
                    .add()
                    .append(new KeyedCodec<>("IsFilter", Codec.BOOLEAN),
                            (data, value) -> data.filterMode = value,
                            data -> data.filterMode)
                    .add()
                    .build();
        }
    }
}
