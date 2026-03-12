package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * This is just a logical container of the MutationBlockSetBlock instances from a previous notification into the
 * higher-level client.
 * It mostly exists to introduce clarity into the complex logic of client-side notifications and may be further
 * refactored into something else, in the future.
 */
public class PreviousNotificationDetails
{
	/**
	 * We track the changes used in the previous notification so that we can strip them out of future notifications to
	 * avoid redundant notifications when the data didn't actually change between the notifications.
	 */
	private Map<AbsoluteLocation, MutationBlockSetBlock> _previousMap;
	/**
	 * We track the special unsafe lighting updates we notified so that we can skip them in later notifications if they
	 * would be redundant.
	 * Note that the actual data used in the unsafe updates is still tracked in SpeculativeProjection and this is just
	 * tracking where these were used in notifications so we can skip them.
	 */
	private Set<CuboidAddress> _unsafeLightingUpdates;

	public PreviousNotificationDetails()
	{
		// We always initialize the states to be empty.
		_previousMap = new HashMap<>();
		_unsafeLightingUpdates = new HashSet<>();
	}

	public Map<AbsoluteLocation, MutationBlockSetBlock> clearPreviousMap()
	{
		Assert.assertTrue(null != _previousMap);
		Map<AbsoluteLocation, MutationBlockSetBlock> toReturn = _previousMap;
		_previousMap = null;
		return toReturn;
	}

	public void storePreviousMap(Map<AbsoluteLocation, MutationBlockSetBlock> map)
	{
		Assert.assertTrue(null == _previousMap);
		Assert.assertTrue(null != map);
		_previousMap = map;
	}

	public Set<CuboidAddress> clearUnsafeLighting()
	{
		Assert.assertTrue(null != _unsafeLightingUpdates);
		Set<CuboidAddress> toReturn = _unsafeLightingUpdates;
		_unsafeLightingUpdates = null;
		return toReturn;
	}

	public void storeUnsafeLighting(Set<CuboidAddress> set)
	{
		Assert.assertTrue(null == _unsafeLightingUpdates);
		Assert.assertTrue(null != set);
		_unsafeLightingUpdates = set;
	}
}
