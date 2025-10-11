package com.jeffdisher.october.types;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;


/**
 * Passed to IMutation and IEntityChange during tick processing so that they can look up details of the previous world
 * state and schedule mutations and changes for future ticks.
 */
public class TickProcessingContext
{
	/**
	 * The current tick being processed (always >0 on server, 0 when speculatively running on client).  This is only
	 * useful for debugging/logging.
	 */
	public final long currentTick;

	/**
	 * The view of the entire world, as of the beginning of this tick.
	 * Returns null if the requested block isn't in a loaded cuboid.
	 */
	public final Function<AbsoluteLocation, BlockProxy> previousBlockLookUp;

	/**
	 * The view of the entire entity crowd, as of the beginning of this tick.  This will look up either entities or
	 * creatures.
	 * Returns null if the requested entity isn't loaded.
	 */
	public final Function<Integer, MinimalEntity> previousEntityLookUp;

	/**
	 * Looks up the sky light landing on the block at the given location as of the previous tick.
	 * Note that this means the sky light will account for the current time of day as well as the height map, such that
	 * only the highest non-air block in the z-column will return non-0 (everything above and below is always 0).
	 */
	public final IByteLookup<AbsoluteLocation> skyLight;

	/**
	 * The consumer of any new block mutations produced as a side-effect of this operation (will be scheduled in a
	 * future tick - never this one).
	 */
	public final IMutationSink mutationSink;

	/**
	 * The consumer of any new entity changes produced as a side-effect of this operation (will be scheduled for the
	 * next tick).
	 */
	public final IChangeSink newChangeSink;

	/**
	 * Used when spawning creatures in the world.
	 */
	public final ICreatureSpawner creatureSpawner;

	/**
	 * Used when spawning passives in the world.
	 */
	public final IPassiveSpawner passiveSpawner;

	/**
	 * Returns a random number when given a bound in the range:  [0..bound).
	 */
	public final IntUnaryOperator randomInt;

	/**
	 * Called by some changes in order to give high-level meanings to what they are doing.
	 */
	public final IEventSink eventSink;

	/**
	 * Called in some special cases to request that a cuboid either be loaded or remain loaded for the keep-alive
	 * interval.
	 */
	public final Consumer<CuboidAddress> keepAliveSink;

	/**
	 * The server's config object.
	 */
	public final WorldConfig config;

	/**
	 * The number of milliseconds since the last tick, based on server configuration.
	 */
	public final long millisPerTick;

	/**
	 * The time of the tick, in millis.  Note that this shouldn't be considered wall-clock time (although it could be),
	 * nor should any value based on it be persisted, as it is allowed to reset or skip arbitrary amounts of time
	 * between server restarts.
	 */
	public final long currentTickTimeMillis;

	public TickProcessingContext(long currentTick
			, Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, Function<Integer, MinimalEntity> previousEntityLookUp
			, IByteLookup<AbsoluteLocation> skyLight
			, IMutationSink mutationSink
			, IChangeSink newChangeSink
			, ICreatureSpawner creatureSpawner
			, IPassiveSpawner passiveSpawner
			, IntUnaryOperator randomInt
			, IEventSink eventSink
			, Consumer<CuboidAddress> keepAliveSink
			, WorldConfig config
			, long millisPerTick
			, long currentTickTimeMillis
	)
	{
		this.currentTick = currentTick;
		this.previousBlockLookUp = previousBlockLookUp;
		this.previousEntityLookUp = previousEntityLookUp;
		this.skyLight = skyLight;
		this.mutationSink = mutationSink;
		this.newChangeSink = newChangeSink;
		this.creatureSpawner = creatureSpawner;
		this.passiveSpawner = passiveSpawner;
		this.randomInt = randomInt;
		this.eventSink = eventSink;
		this.keepAliveSink = keepAliveSink;
		this.config = config;
		this.millisPerTick = millisPerTick;
		this.currentTickTimeMillis = currentTickTimeMillis;
	}


	/**
	 * The sink for block mutations.  Depending on the entry-point, they will be scheduled in the next tick or a later
	 * one.
	 */
	public static interface IMutationSink
	{
		/**
		 * Requests that a block mutation be scheduled for the next tick.
		 * 
		 * @param mutation The mutation to schedule.
		 * @return True if the mutation was scheduled (will run even if target unloaded) and false if the target isn't
		 * loaded and the mutation was NOT scheduled.
		 */
		boolean next(IMutationBlock mutation);
		/**
		 * Requests that a block mutation be scheduled for the future.
		 * 
		 * @param mutation The mutation to schedule.
		 * @param millisToDelay Milliseconds to delay before running the mutation.
		 * @return True if the mutation was scheduled (will run even if target unloaded) and false if the target isn't
		 * loaded and the mutation was NOT scheduled.
		 */
		boolean future(IMutationBlock mutation, long millisToDelay);
	}


	/**
	 * The sink for entity changes.  Depending on the entry-point, they will be scheduled in the next tick or a later
	 * one.
	 */
	public static interface IChangeSink
	{
		/**
		 * Requests that an entity change be scheduled for the next tick.
		 * 
		 * @param targetEntityId The ID of the entity which should run the change.
		 * @param change The change to schedule.
		 * @return True if the action was scheduled (will run even if target unloaded) and false if the target isn't
		 * loaded and the action was NOT scheduled.
		 */
		boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change);
		/**
		 * Requests that an entity change be scheduled in the future.
		 * 
		 * @param targetEntityId The ID of the entity which should run the change.
		 * @param change The change to schedule.
		 * @param millisToDelay Milliseconds to delay before running the mutation.
		 * @return True if the action was scheduled (will run even if target unloaded) and false if the target isn't
		 * loaded and the action was NOT scheduled.
		 */
		boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay);
		/**
		 * Requests that the given change be scheduled against this creature in the next tick.
		 * 
		 * @param targetCreatureId The ID of the creature which should run the change.
		 * @param change The change to run.
		 * @return True if the action was scheduled and false if the target isn't loaded and the action was NOT
		 * scheduled.  Note that creature actions can still return true here but NEVER be run if the creature is
		 * unloaded on the next tick (as they aren't stored).
		 */
		boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change);
		/**
		 * Requests that the given action be scheduled on the target passive in the next tick.
		 * 
		 * @param targetPassiveId The ID of the passive entity which should run the action.
		 * @param action The action to run.
		 * @return True if this was scheduled, false if the passive isn't known.  Note that passive actions can still
		 * return true here but NEVER be run if the passive is unloaded on the next tick (as they aren't stored).
		 */
		boolean passive(int targetPassiveId, IPassiveAction action);
	}


	/**
	 * Events are data elements which clients can observe but are never read within tick processing, nor are they
	 * persisted on the server.
	 * They exist purely to allow the client to make meaningful sense of things happening in the world.  For example,
	 * instead of a block appearing in the world, an event could be used to say that an entity placed it.
	 */
	public static interface IEventSink
	{
		void post(EventRecord event);
	}

	/**
	 * When new creatures are spawned, they use this interface.  The implementation is responsible for assigning the ID
	 * to the new creature, instantiating it, and making sure that it is included in the next tick.
	 */
	public static interface ICreatureSpawner
	{
		void spawnCreature(EntityType type, EntityLocation location, byte health);
	}

	/**
	 * When new passives are spawned, they use this interface.  The implementation is responsible for assigning the ID
	 * to the new passive, instantiating it, and making sure that it is included in the next tick.
	 */
	public static interface IPassiveSpawner
	{
		void spawnPassive(PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData);
	}
}
