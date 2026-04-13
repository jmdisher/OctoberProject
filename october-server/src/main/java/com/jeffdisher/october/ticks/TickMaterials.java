package com.jeffdisher.october.ticks;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PassiveEntity;


public record TickMaterials(long thisGameTick
	// Read-only versions of the cuboids produced by the previous tick (by address).
	, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
	, Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps
	, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
	// Read-only versions of the Entities produced by the previous tick (by ID).
	, Map<Integer, Entity> completedEntities
	// Read-only versions of the creatures from the previous tick (by ID).
	, Map<Integer, CreatureEntity> completedCreatures
	// Read-only versions of the passives from the previous tick (by ID).
	, Map<Integer, PassiveEntity> completedPassives
	// Never null but typically empty.
	, List<IEntityAction<IMutablePlayerEntity>> operatorChanges
	// The blocks modified in the last tick, represented as a list per cuboid where they originate.
	, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
	// The blocks which were modified in such a way that they may require a lighting update.
	, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
	// The blocks which were modified in such a way that they may have changed the logic aspect which needs to
	// be propagated.
	, Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid
	// The set of addresses loaded in this tick (they are present in this tick, but for the first time).
	, Set<CuboidAddress> cuboidsLoadedThisTick
	
	// Information used to build the BlockFetcher for each thread in parallel phase.
	, Map<AbsoluteLocation, BlockProxy> previousProxyCache
	, Set<AbsoluteLocation> forceMissBlocksPreviousCache
	
	// Higher-level data associated with the materials.
	, EntityCollection entityCollection
	, TickInput highLevel
	
	// Data related to internal statistics to be passed back at the end of the tick.
	, long nanosInPreamble
	, long nanosInPreambleIncoming
	, long nanosInPreamblePreTick
	, long nanosInPreamblePackage
	, long nanosAtPreambleEnd
)
{
}
