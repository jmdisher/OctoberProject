package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;


/**
 * The speculative client-side state created by applying the local, not-yet-committed changes to the shadow state.
 * This is mostly just a transparent container of this state since SpeculativeProjection operates on these, directly.
 */
public class ProjectedState
{
	public Entity projectedLocalEntity;
	public Map<CuboidAddress, IReadOnlyCuboidData> projectedWorld;
	public Map<CuboidAddress, CuboidHeightMap> projectedHeightMap;
	public Map<AbsoluteLocation, BlockProxy> projectedBlockChanges;

	public ProjectedState(Entity projectedLocalEntity
			, Map<CuboidAddress, IReadOnlyCuboidData> projectedWorld
			, Map<CuboidAddress, CuboidHeightMap> projectedHeightMap
	)
	{
		this.projectedLocalEntity = projectedLocalEntity;
		this.projectedWorld = projectedWorld;
		this.projectedHeightMap = projectedHeightMap;
		this.projectedBlockChanges = new HashMap<>();
	}
}
