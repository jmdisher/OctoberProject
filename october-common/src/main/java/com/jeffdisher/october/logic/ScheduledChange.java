package com.jeffdisher.october.logic;

import com.jeffdisher.october.mutations.IMutationEntity;


/**
 * This is just the common type exported by both CrowdProcessor and WorldProcessor for the delayed entity changes.
 */
public record ScheduledChange(IMutationEntity change
		, long millisUntilReady
) {}
