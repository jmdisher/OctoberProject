package com.jeffdisher.october.types;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;


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

	public final CreatureIdAssigner idAssigner;

	/**
	 * Returns a random number when given a bound in the range:  [0..bound).
	 */
	public final IntUnaryOperator randomInt;

	/**
	 * The server's config object.
	 */
	public final WorldConfig config;

	/**
	 * The number of milliseconds since the last tick, based on server configuration.
	 */
	public final long millisPerTick;

	public TickProcessingContext(long currentTick
			, Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, Function<Integer, MinimalEntity> previousEntityLookUp
			, IByteLookup<AbsoluteLocation> skyLight
			, IMutationSink mutationSink
			, IChangeSink newChangeSink
			, CreatureIdAssigner idAssigner
			, IntUnaryOperator randomInt
			, WorldConfig config
			, long millisPerTick
	)
	{
		this.currentTick = currentTick;
		this.previousBlockLookUp = previousBlockLookUp;
		this.previousEntityLookUp = previousEntityLookUp;
		this.skyLight = skyLight;
		this.mutationSink = mutationSink;
		this.newChangeSink = newChangeSink;
		this.idAssigner = idAssigner;
		this.randomInt = randomInt;
		this.config = config;
		this.millisPerTick = millisPerTick;
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
		 */
		void next(IMutationBlock mutation);
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
		 */
		void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change);
		/**
		 * Requests that an entity change be scheduled in the future.
		 * 
		 * @param targetEntityId The ID of the entity which should run the change.
		 * @param change The change to schedule.
		 * @param millisToDelay Milliseconds to delay before running the mutation.
		 */
		void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay);
		/**
		 * Requests that the given change be scheduled against this creature in the next tick.
		 * 
		 * @param targetCreatureId The ID of the creature which should run the change.
		 * @param change The change to run.
		 */
		void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change);
	}
}
