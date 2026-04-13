package com.jeffdisher.october.ticks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.SpatialIndex;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;


public class TickContextBuilder
{
	// Raw inputs carried into TickProcessingContext.
	private final TickMaterials _materials;
	private final long _millisPerTick;
	private final IntUnaryOperator _random;
	private final WorldConfig _config;

	// Data stores which hold the out-data from the context.
	public final List<CreatureEntity> creaturesSpawned;
	public final List<PassiveEntity> passivesSpawned;
	public final List<EventRecord> eventsPosted;
	public final Set<CuboidAddress> cuboidsMarkedAliveInternally;

	// Derived components used to build the context.
	public final BlockFetcher previousBlockLookUp;
	private final TickProcessingContext.IPassiveSearch _passiveSearch;
	public final CommonMutationSink mutationSink;
	public final CommonChangeSink changeSink;
	private final TickProcessingContext.ICreatureSpawner _spawnConsumer;
	private final TickProcessingContext.IPassiveSpawner _passiveConsumer;

	public TickContextBuilder(TickMaterials materials
		, long millisPerTick
		, CreatureIdAssigner idAssigner
		, PassiveIdAssigner passiveIdAssigner
		, IntUnaryOperator random
		, WorldConfig config
	)
	{
		_materials = materials;
		_millisPerTick = millisPerTick;
		_random = random;
		_config = config;
		
		this.creaturesSpawned = new ArrayList<>();
		this.passivesSpawned = new ArrayList<>();
		this.eventsPosted = new ArrayList<>();
		this.cuboidsMarkedAliveInternally = new HashSet<>();
		
		// Create the BlockProxy loader for the read-only state from the previous tick.
		// WARNING:  This block cache is used for everything this thread does and we may want to provide a flushing mechanism.
		this.previousBlockLookUp = new BlockFetcher(materials.previousProxyCache()
			, materials.forceMissBlocksPreviousCache()
			, materials.completedCuboids()
		);
		_passiveSearch = new TickProcessingContext.IPassiveSearch() {
			private SpatialIndex _passiveSpatialIndex;
			@Override
			public PartialPassive getById(int id)
			{
				PassiveEntity passive = materials.completedPassives().get(id);
				return (null != passive)
					? PartialPassive.fromPassive(passive)
					: null
				;
			}
			@Override
			public PartialPassive[] findPassiveItemSlotsInRegion(EntityLocation base, EntityLocation edge)
			{
				// The _passiveSpatialIndex is lazily constructed since it is only used by hoppers and player entities.
				if (null == _passiveSpatialIndex)
				{
					// NOTE:  We only expose passive entities in the interface since we only have a use-case for them, at the moment.
					SpatialIndex.Builder builder = new SpatialIndex.Builder();
					for (PassiveEntity passive : materials.completedPassives().values())
					{
						if (PassiveType.ITEM_SLOT == passive.type())
						{
							builder.add(passive.id(), passive.location());
						}
					}
					_passiveSpatialIndex = builder.finish(PassiveType.ITEM_SLOT.volume());
				}
				return _passiveSpatialIndex.idsIntersectingRegion(base, edge).stream()
					.map((Integer id) -> {
						PassiveEntity passive = materials.completedPassives().get(id);
						return PartialPassive.fromPassive(passive);
					})
					.toArray((int size) -> new PartialPassive[size])
				;
			}
		};
		
		this.mutationSink = new CommonMutationSink(materials.completedCuboids().keySet());
		this.changeSink = new CommonChangeSink(materials.completedEntities().keySet(), materials.completedCreatures().keySet(), materials.completedPassives().keySet());
		
		// On the server, we just generate the tick time as purely abstract monotonic value.
		long currentTickTimeMillis = (_materials.thisGameTick() * _millisPerTick);
		
		_spawnConsumer = (EntityType type, EntityLocation location) -> {
			int id = idAssigner.next();
			CreatureEntity entity = CreatureEntity.create(id, type, location, currentTickTimeMillis);
			this.creaturesSpawned.add(entity);
		};
		// Same with passives.
		_passiveConsumer = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			int id = passiveIdAssigner.next();
			PassiveEntity entity = new PassiveEntity(id, type, location, velocity, extendedData, currentTickTimeMillis);
			this.passivesSpawned.add(entity);
		};
	}

	public TickProcessingContext buildContext()
	{
		long gameTick = _materials.thisGameTick();
		long currentTickTimeMillis = (gameTick * _millisPerTick);
		
		return new TickProcessingContext(gameTick
			, this.previousBlockLookUp
			, (Integer entityId) -> (entityId > 0)
				? MinimalEntity.fromEntity(_materials.completedEntities().get(entityId))
				: MinimalEntity.fromCreature(_materials.completedCreatures().get(entityId))
			, _passiveSearch
			, (AbsoluteLocation blockLocation) -> {
				CuboidColumnAddress column = blockLocation.getCuboidAddress().getColumn();
				BlockAddress blockAddress = blockLocation.getBlockAddress();
				ColumnHeightMap map = _materials.completedHeightMaps().get(column);
				
				byte skyLight;
				if (null != map)
				{
					int highestBlock = map.getHeight(blockAddress.x(), blockAddress.y());
					// If this is the highest block, return the light, otherwise 0.
					skyLight = (blockLocation.z() == highestBlock)
							? PropagationHelpers.currentSkyLightValue(gameTick, _config.ticksPerDay, _config.dayStartTick)
							: 0
					;
				}
				else
				{
					// Note that the map may be null if this is just during start-up so just say that the light is 0, in that case.
					skyLight = 0;
				}
				return skyLight;
			}
			, this.mutationSink
			, this.changeSink
			, _spawnConsumer
			, _passiveConsumer
			, _random
			, (EventRecord event) -> this.eventsPosted.add(event)
			, (CuboidAddress address) -> this.cuboidsMarkedAliveInternally.add(address)
			, _config
			, _millisPerTick
			, currentTickTimeMillis
		);
	}
}
