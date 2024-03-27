package com.jeffdisher.october.logic;

import com.jeffdisher.october.mutations.IMutationBlock;


/**
 * This is just the common type exported by both CrowdProcessor and WorldProcessor for the delayed mutations.
 */
public record ScheduledMutation(IMutationBlock mutation
		, long millisUntilReady
) {}
