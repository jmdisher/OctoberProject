package com.jeffdisher.october.types;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
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
	 * The consumer of any new mutations produces as a side-effect of this operation (will be scheduled for the next
	 * tick).
	 */
	public final Consumer<IMutationBlock> newMutationSink;

	/**
	 * The consumer of any new entity changes produced as a side-effect of this operation (will be scheduled for the
	 * next tick).
	 */
	public final IChangeSink newChangeSink;

	public TickProcessingContext(long currentTick
			, Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, Consumer<IMutationBlock> newMutationSink
			, IChangeSink newChangeSink
	)
	{
		this.currentTick = currentTick;
		this.previousBlockLookUp = previousBlockLookUp;
		this.newMutationSink = newMutationSink;
		this.newChangeSink = newChangeSink;
	}


	/**
	 * The sink for new entity changes produced while applying a mutation or change.  It will be scheduled in the
	 * following tick.
	 */
	public static interface IChangeSink
	{
		void accept(int targetEntityId, IMutationEntity change);
	}
}
