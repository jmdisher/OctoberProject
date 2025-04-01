package com.jeffdisher.october.client;

import java.util.Set;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.PartialEntity;


/**
 * The listener passed in to the SpeculativeProjection to receive callbacks for changes to the client's state.  Note
 * there should be no redundant updates (being notified that a block/cuboid/entity changed when it is the same as the
 * last callback).
 */
public interface IProjectionListener
{
	/**
	 * Called when a new cuboid is loaded (may have been previously unloaded but not currently loaded).
	 * 
	 * @param cuboid The read-only cuboid data.
	 * @param heightMap The height map for this cuboid's column.
	 */
	void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap);

	/**
	 * Called when a new cuboid is replaced due to changes (must have been previously loaded).
	 * 
	 * @param cuboid The read-only cuboid data.
	 * @param heightMap The height map for this cuboid's column.
	 * @param changedBlocks The set of blocks which have some kind of change.
	 * @param changedAspects The set of aspects changed by any of the changedBlocks.
	 */
	void cuboidDidChange(IReadOnlyCuboidData cuboid
			, ColumnHeightMap heightMap
			, Set<BlockAddress> changedBlocks
			, Set<Aspect<?, ?>> changedAspects
	);

	/**
	 * Called when a new cuboid should be unloaded as the server is no longer telling the client about it.
	 * 
	 * @param address The address of the cuboid.
	 */
	void cuboidDidUnload(CuboidAddress address);

	/**
	 * Called when the client's entity has loaded for the first time.
	 * Only called once per instance.
	 * 
	 * @param authoritativeEntity The entity state from the server.
	 */
	void thisEntityDidLoad(Entity authoritativeEntity);

	/**
	 * Called when the client's entity has changed (either due to server-originating changes or local changes).
	 * Called very frequently.
	 * 
	 * @param authoritativeEntity The entity state from the server.
	 * @param projectedEntity The client's local state (local changes applied to server data).
	 */
	void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity);

	/**
	 * Called when another entity is loaded for the first time.
	 * 
	 * @param entity The server's entity data.
	 */
	void otherEntityDidLoad(PartialEntity entity);

	/**
	 * Called when a previously-loaded entity's state changes.
	 * 
	 * @param entity The server's entity data.
	 */
	void otherEntityDidChange(PartialEntity entity);

	/**
	 * Called when another entity should be unloaded as the server is no longer sending us updates.
	 * 
	 * @param id The ID of the entity to unload.
	 */
	void otherEntityDidUnload(int id);

	/**
	 * Called when a game tick from the server has been fully processed.
	 * 
	 * @param gameTick The tick number (this is monotonic).
	 */
	void tickDidComplete(long gameTick);

	/**
	 * Called when a high-level event is generated, either by a local change or as a result of absorbing changes
	 * from the server.
	 * 
	 * @param event The event.
	 */
	void handleEvent(EventRecord event);
}
