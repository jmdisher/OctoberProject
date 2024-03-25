package com.jeffdisher.october.logic;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;


/**
 * This is used in the case where something related to a block has changed but includes other information related to
 * what kinds of updates are required beyond just packaging the data for network.
 */
public record BlockChangeDescription(boolean requiresUpdateEvent
		, boolean requiresLightingCheck
		, MutationBlockSetBlock serializedForm
)
{
	public static BlockChangeDescription extractFromProxy(ByteBuffer scratchBuffer, MutableBlockProxy proxy)
	{
		boolean requiresUpdateEvent = proxy.shouldTriggerUpdateEvent();
		boolean requiresLightingCheck = proxy.mayTriggerLightingChange();
		MutationBlockSetBlock serializedForm = MutationBlockSetBlock.extractFromProxy(scratchBuffer, proxy);
		return new BlockChangeDescription(requiresUpdateEvent
				, requiresLightingCheck
				, serializedForm
		);
	}
}
