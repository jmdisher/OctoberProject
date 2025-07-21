package com.jeffdisher.october.logic;

import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;


/**
 * This is just the common type exported by both CrowdProcessor and WorldProcessor for the delayed entity changes.
 */
public record ScheduledChange(IEntityAction<IMutablePlayerEntity> change
		, long millisUntilReady
) {}
