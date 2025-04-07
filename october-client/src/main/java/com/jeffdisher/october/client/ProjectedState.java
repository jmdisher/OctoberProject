package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
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
	public Map<AbsoluteLocation, MutationBlockSetBlock> projectedBlockChanges;
	public Set<CuboidAddress> projectedUnsafeLight;

	public ProjectedState(Entity projectedLocalEntity
			, Map<CuboidAddress, IReadOnlyCuboidData> projectedWorld
			, Map<CuboidAddress, CuboidHeightMap> projectedHeightMap
			, Map<AbsoluteLocation, MutationBlockSetBlock> projectedBlockChanges
			, Set<CuboidAddress> projectedUnsafeLight
	)
	{
		this.projectedLocalEntity = projectedLocalEntity;
		this.projectedWorld = new HashMap<>(projectedWorld);
		this.projectedHeightMap = new HashMap<>(projectedHeightMap);
		this.projectedBlockChanges = new HashMap<>(projectedBlockChanges);
		this.projectedUnsafeLight = new HashSet<>(projectedUnsafeLight);
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
