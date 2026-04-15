package com.jeffdisher.october.transactions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The helper used to create a transaction of mutations to run in the next tick atomically (either all of the mutations
 * will run or none of them will).
 * Transactions model explained:
 * -these transactions work less like DB transactions and more like transactional memory, in that they can fail due to
 *  conflicts without any transaction making progress
 * -a transaction can ONLY be scheduled for the following tick
 * -a transaction will fail if any of the following are true:
 *   -any of the referenced blocks aren't loaded in the tick when requested or the tick where run
 *   -there are ANY other mutations scheduled for the referenced blocks in the tick when requested
 *   -there are ANY OTHER (other than the transaction) mutations scheduled for the referenced blocks int he tick where
 *    run
 * -as mutations can't actually "fail", all the mutations will run, and thus the transaction considered "committed" if
 *  none of the above restrictions are encountered
 * -all mutations in the transaction are run via MutationBlockTransactionWrapper, which checks that the transaction can
 *  proceed (nothing referenced was unloaded or has anything else scheduled)
 */
public class TransactionBuilder
{
	private final List<IMutationBlock> _parts = new ArrayList<>();

	public void addMutation(IMutationBlock mutation)
	{
		_parts.add(mutation);
	}

	public boolean didStartTransaction(TickProcessingContext context)
	{
		boolean didStart = false;
		
		// There must at least be something.
		if (_parts.size() > 0)
		{
			// We need to make sure that none of these locations are duplicated.
			Set<AbsoluteLocation> locations = _parts.stream()
				.map((IMutationBlock mutation) -> mutation.getAbsoluteLocation())
				.collect(Collectors.toSet())
			;
			
			if (locations.size() == _parts.size())
			{
				// We need to make sure that none of the locations were running mutations in this tick (as that could invalidate any checks performed by the caller).
				if (context.transactions.checkScheduledMutationCount(locations, 0))
				{
					// Schedule all the mutations.
					for (IMutationBlock mutation : _parts)
					{
						MutationBlockTransactionWrapper wrapper = new MutationBlockTransactionWrapper(mutation, locations);
						boolean didSchedule = context.mutationSink.next(wrapper);
						// We already checked that these are loaded.
						Assert.assertTrue(didSchedule);
					}
					
					// This phase of the transaction passed.
					didStart = true;
				}
			}
		}
		return didStart;
	}
}
