package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.EntityChangeTakeDamage;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.TickProcessingContext.IMutationSink;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCreatureProcessor
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void singleChange()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0.0f, (byte)100, 0L);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		TickProcessingContext context = new TickProcessingContext(1
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid)
				, null
				, null
				, null
		);
		long millisSinceLastTick = 100L;
		EntityChangeTakeDamage<IMutableMinimalEntity> change = new EntityChangeTakeDamage<>(BodyPart.FEET, (byte)10);
		Map<Integer, List<IMutationEntity<IMutableMinimalEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, millisSinceLastTick
				, changesToRun
		);
		
		Assert.assertEquals(1, group.committedMutationCount());
		Assert.assertEquals(1, group.updatedCreatures().size());
		Assert.assertEquals(0, group.deadCreatureIds().size());
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals((byte)90, updated.health());
	}

	@Test
	public void killEntity()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0.0f, (byte)50, 0L);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		IMutationBlock[] mutationHolder = new IMutationBlock[1];
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		TickProcessingContext context = new TickProcessingContext(1
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid)
				, null
				, new IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(mutationHolder[0]);
						mutationHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail();
					}}
				, null
		);
		long millisSinceLastTick = 100L;
		EntityChangeTakeDamage<IMutableMinimalEntity> change = new EntityChangeTakeDamage<>(BodyPart.FEET, (byte)120);
		Map<Integer, List<IMutationEntity<IMutableMinimalEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, millisSinceLastTick
				, changesToRun
		);
		
		Assert.assertEquals(1, group.committedMutationCount());
		Assert.assertEquals(0, group.updatedCreatures().size());
		Assert.assertEquals(1, group.deadCreatureIds().size());
		Assert.assertEquals(creature.id(), group.deadCreatureIds().get(0).intValue());
		// This is a cow so we should see it drop an item.
		Assert.assertTrue(mutationHolder[0] instanceof MutationBlockStoreItems);
	}
}
