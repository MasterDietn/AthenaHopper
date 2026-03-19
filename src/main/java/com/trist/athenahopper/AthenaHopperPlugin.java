package com.trist.athenahopper;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3i;

public class AthenaHopperPlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.getLogger();

    public AthenaHopperPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getBlockStateRegistry().registerBlockState(
                AthenaHopperBlockState.class,
                "AthenaHopper_Block",
                AthenaHopperBlockState.CODEC,
                AthenaHopperBlockState.AthenaHopperBlockStateData.class,
                AthenaHopperBlockState.AthenaHopperBlockStateData.CODEC
        );

        getEntityStoreRegistry().registerSystem(new AthenaHopperUseSystem());
    }

    private final class AthenaHopperUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private AthenaHopperUseSystem() {
            super(UseBlockEvent.Pre.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void handle(int index,
                           ArchetypeChunk<EntityStore> archetypeChunk,
                           Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer,
                           UseBlockEvent.Pre event) {
            InteractionType type = event.getInteractionType();
            // We decide behavior later after verifying the hopper is in filter mode.

            InteractionContext context = event.getContext();
            if (context == null) {
                return;
            }

            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) {
                return;
            }

            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null) {
                return;
            }

            World world = player.getWorld();
            if (world == null) {
                return;
            }

            Vector3i target = event.getTargetBlock();
            if (target == null) {
                return;
            }

            BlockState state = world.getState(target.x, target.y, target.z, true);
            if (!(state instanceof AthenaHopperBlockState funnelState) || !funnelState.isFilterMode()) {
                return;
            }

            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) {
                return;
            }

            // Filter-mode behavior:
            // - Only interaction "Use" (typically bound to F) opens our custom UI.
            // - Any other "use" interaction (e.g. mouse buttons) cancels the default container UI.
            event.setCancelled(true);
            if (type == InteractionType.Use) {
                PageManager pages = player.getPageManager();
                AthenaHopperPage page = new AthenaHopperPage(playerRef, playerRef.getWorldUuid(), target);
                pages.openCustomPage(entityRef, store, page);
            }
        }
    }
}
