package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
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

	public Map<CuboidColumnAddress, ColumnHeightMap> buildColumnMaps(Set<CuboidColumnAddress> columnsToGenerate)
	{
		Map<CuboidAddress, CuboidHeightMap> mapsToCoalesce = this.projectedHeightMap.entrySet().stream()
				.filter((Map.Entry<CuboidAddress, CuboidHeightMap> entry) -> columnsToGenerate.contains(entry.getKey().getColumn()))
				.collect(Collectors.toMap((Map.Entry<CuboidAddress, CuboidHeightMap> entry) -> entry.getKey(), (Map.Entry<CuboidAddress, CuboidHeightMap> entry) -> entry.getValue()))
		;
		Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps = HeightMapHelpers.buildColumnMaps(mapsToCoalesce);
		return columnHeightMaps;
	}
}
