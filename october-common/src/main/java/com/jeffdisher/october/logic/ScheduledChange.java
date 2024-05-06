package com.jeffdisher.october.logic;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;


/**
 * This is just the common type exported by both CrowdProcessor and WorldProcessor for the delayed entity changes.
 */
public record ScheduledChange(IMutationEntity<IMutablePlayerEntity> change
		, long millisUntilReady
) {}
