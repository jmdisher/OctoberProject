package com.jeffdisher.october.types;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.IMutation;


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
	 */
	public final Function<AbsoluteLocation, BlockProxy> previousBlockLookUp;

	/**
	 * The consumer of any new mutations produces as a side-effect of this operation (will be scheduled for the next
	 * tick).
	 */
	public final Consumer<IMutation> newMutationSink;

	/**
	 * The consumer of any new entity changes produced as a side-effect of this operation (will be scheduled for the
	 * next tick).
	 */
	public final Consumer<IEntityChange> newChangeSink;

	/**
	 * The consumer of the second part of a 2-part entity change.
	 * Arguments:
	 * 1) the change
	 * 2) the number of milliseconds until it should be run
	 * Note that calling this has the consequence of putting the entity into a "busy" state.  Any other change executed
	 * on the same entity will clear this busy state, meaning that the second part of the change will never be run.
	 * This means that any modification of the entity state should be run in the second part.
	 * This is ONLY present when processing normal changes, not mutations, and not phase2 changes.
	 */
	public final BiConsumer<IEntityChange, Long> twoPhaseChangeSink;

	public TickProcessingContext(long currentTick
			, Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, Consumer<IMutation> newMutationSink
			, Consumer<IEntityChange> newChangeSink
			, BiConsumer<IEntityChange, Long> twoPhaseChangeSink
	)
	{
		this.currentTick = currentTick;
		this.previousBlockLookUp = previousBlockLookUp;
		this.newMutationSink = newMutationSink;
		this.newChangeSink = newChangeSink;
		this.twoPhaseChangeSink = twoPhaseChangeSink;
	}
}
