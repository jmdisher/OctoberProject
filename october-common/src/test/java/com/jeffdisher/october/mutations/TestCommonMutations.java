package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.CompositeHelpers;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * A test suite for the basic behaviours of the common mutations (those which are part of the system).
 * The tests are combined here since each mutation is fundamentally simple and this is mostly just to demonstrate that
 * they can be called.
 */
public class TestCommonMutations
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item CHARCOAL_ITEM;
	private static Block STONE;
	private static Block WATER_SOURCE;
	private static Block WATER_STRONG;
	private static Block WATER_WEAK;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		CHARCOAL_ITEM = ENV.items.getItemById("op.charcoal");
		WATER_SOURCE = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.water_source"));
		WATER_STRONG = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.water_strong"));
		WATER_WEAK = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.water_weak"));
		STONE = ENV.blocks.fromItem(STONE_ITEM);
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void breakBlockSuccess()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidAddress cuboidAddress = target.getCuboidAddress();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, (short)2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		_Events events = new _Events();
		List<PassiveEntity> out_passives = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
						return cuboidAddress.equals(location.getCuboidAddress())
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
						;
					}, null, null)
				.eventSink(events)
				.passive((PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
					long lastAliveMillis = 1000L;
					PassiveEntity passive = new PassiveEntity(out_passives.size() + 1
						, type
						, location
						, velocity
						, extendedData
						, lastAliveMillis
					);
					out_passives.add(passive);
				})
				.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, target, 0, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY));
		boolean didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(1, out_passives.size());
		Assert.assertEquals(target.toEntityLocation(), out_passives.get(0).location());
		ItemSlot firstSlot = (ItemSlot)out_passives.get(0).extendedData();
		Assert.assertEquals(STONE_ITEM, firstSlot.getType());
		Assert.assertEquals(1, firstSlot.getCount());
	}

	@Test
	public void breakBlockFailure()
	{
		// Just shows that nothing happens when we try to break an unbreakable block.
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, (short)1000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		boolean didApply = mutation.applyMutation(null, proxy);
		Assert.assertFalse(didApply);
		Assert.assertFalse(proxy.didChange());
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
	}

	@Test
	public void endBlockBreakSuccess()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), STONE);
		ProcessingSinks sinks = new ProcessingSinks();
		TickProcessingContext context = sinks.createBoundContext(cuboid);
		EntityChangeIncrementalBlockBreak longRunningChange = new EntityChangeIncrementalBlockBreak(target);
		
		// We will need an entity so that phase1 can ask to schedule the follow-up against it.
		int clientId = 1;
		Entity entity = MutableEntity.createForTest(clientId).freeze();
		
		// Without a tool, this will take 20 hits (ticks).
		MutableBlockProxy proxy = null;
		sinks.events.expected(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, target, 0, clientId));
		for (int i = 0; i < 20; ++i)
		{
			// Check that once we run this change, it requests the appropriate mutation.
			boolean didApply = longRunningChange.applyChange(context, MutableEntity.existing(entity));
			Assert.assertTrue(didApply);
			
			// Check that the final mutation to actually break the block is as expected and then run it.
			proxy = new MutableBlockProxy(target, cuboid);
			Assert.assertNotNull(sinks.nextMutation);
			didApply = sinks.nextMutation.applyMutation(context, proxy);
			Assert.assertTrue(didApply);
			Assert.assertTrue(proxy.didChange());
			proxy.writeBack(cuboid);
			
			// Verify that we see the damage on the first hit.
			if (0 == i)
			{
				Assert.assertEquals(STONE, proxy.getBlock());
				Assert.assertEquals((short)context.millisPerTick, proxy.getDamage());
			}
		}
		
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(clientId, sinks.nextTargetEntityId);
		Assert.assertTrue(sinks.nextChange instanceof EntityActionStoreToInventory);
	}

	@Test
	public void mineWithTool()
	{
		// Show that the mining damage applied to a block is higher with a pickaxe.
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), STONE);
		ProcessingSinks sinks = new ProcessingSinks();
		TickProcessingContext context = sinks.createBoundContext(cuboid);
		EntityChangeIncrementalBlockBreak longRunningChange = new EntityChangeIncrementalBlockBreak(target);
		
		// We will need an entity so that phase1 can ask to schedule the follow-up against it.
		int clientId = 1;
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		Item pickaxe = ENV.items.getItemById("op.iron_pickaxe");
		mutable.newInventory.addNonStackableBestEfforts(PropertyHelpers.newItemWithDefaults(ENV, pickaxe));
		mutable.setSelectedKey(1);
		Entity entity = mutable.freeze();
		
		// Just make 1 hit and see the damage apply.
		// Check that once we run this change, it requests the appropriate mutation.
		boolean didApply = longRunningChange.applyChange(context, MutableEntity.existing(entity));
		Assert.assertTrue(didApply);
		
		// Check that the final mutation to actually break the block is as expected and then run it.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertNotNull(sinks.nextMutation);
		didApply = sinks.nextMutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		
		// We should see the applied damage at 10x the time since we were using the pickaxe (hard-coded since we are partially validating the env lookup).
		Assert.assertEquals(STONE, proxy.getBlock());
		Assert.assertEquals((short)1000, proxy.getDamage());
	}

	@Test
	public void breakBlockPartial()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), STONE);
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, (short)500, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		boolean didApply = mutation.applyMutation(null, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(STONE, proxy.getBlock());
		Assert.assertNull(proxy.getInventory());
		Assert.assertEquals((short) 500, proxy.getDamage());
	}

	@Test
	public void waterBehaviour()
	{
		// We want to verify what happens in a situation where we expect the water to flow into some gaps after breaking a block.
		AbsoluteLocation target = new AbsoluteLocation(15, 15, 15);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), STONE);
		AbsoluteLocation down = target.getRelative(0, 0, -1);
		AbsoluteLocation downOver = target.getRelative(1, 0, -1);
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(-1, 0, 1).getBlockAddress(), WATER_STRONG.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, 1).getBlockAddress(), WATER_WEAK.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, down.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, downOver.getBlockAddress(), ENV.special.AIR.item().number());
		
		_Events events = new _Events();
		List<IMutationBlock> out_mutation = new ArrayList<>();
		List<PassiveEntity> out_passives = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public boolean next(IMutationBlock mutation)
						{
							out_mutation.add(mutation);
							return true;
						}
						@Override
						public boolean future(IMutationBlock mutation, long millisToDelay)
						{
							out_mutation.add(mutation);
							return true;
						}
					}, null)
				.passive((PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
					long lastAliveMillis = 1000L;
					PassiveEntity passive = new PassiveEntity(out_passives.size() + 1
						, type
						, location
						, velocity
						, extendedData
						, lastAliveMillis
					);
					out_passives.add(passive);
				})
				.eventSink(events)
				.finish()
		;
		
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, (short)2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		events.expected(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, target, 0, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY));
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		
		// This should cause a delayed water flow so we should see air there until the next update.
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(1, out_mutation.size());
		Assert.assertEquals(1, out_passives.size());
		Assert.assertTrue(out_mutation.get(0) instanceof MutationBlockLiquidFlowInto);
		IMutationBlock internal = out_mutation.get(0);
		out_mutation.clear();
		proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(internal.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		
		// If we break a block under any strength of flow, we expect it to be weak flow.
		Assert.assertEquals(WATER_WEAK, proxy.getBlock());
		
		// Run an update on the other blocks below to verify it flows through them, creating strong flow when it touches the solid block.
		proxy = new MutableBlockProxy(down, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(down).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// This should cause a delayed water flow so we should see air there until the next update.
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(1, out_mutation.size());
		Assert.assertTrue(out_mutation.get(0) instanceof MutationBlockLiquidFlowInto);
		internal = out_mutation.get(0);
		out_mutation.clear();
		proxy = new MutableBlockProxy(down, cuboid);
		Assert.assertTrue(internal.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(WATER_STRONG, proxy.getBlock());
		
		proxy = new MutableBlockProxy(downOver, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(downOver).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(1, out_mutation.size());
		Assert.assertTrue(out_mutation.get(0) instanceof MutationBlockLiquidFlowInto);
		internal = out_mutation.get(0);
		out_mutation.clear();
		proxy = new MutableBlockProxy(downOver, cuboid);
		Assert.assertTrue(internal.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(WATER_WEAK, proxy.getBlock());
	}

	@Test
	public void overwriteBoundaryCheckSupport()
	{
		// This is verify a bug fix when planting seeds at the bottom of a cuboid, when the below is not loaded, would NPE.
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		
		Block wheatSeedling = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_seedling"));
		int entityId = 1;
		MutationBlockOverwriteByEntity mutation = new MutationBlockOverwriteByEntity(target, wheatSeedling, null, entityId);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public boolean next(IMutationBlock mutation)
						{
							throw new AssertionError("Not expected in test");
						}
						@Override
						public boolean future(IMutationBlock mutation, long millisToDelay)
						{
							throw new AssertionError("Not expected in test");
						}
					}, null)
				.eventSink(events)
				.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, target, 0, entityId));
		boolean didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(10000L, proxy.periodicDelayMillis);
	}

	@Test
	public void replaceSerialization()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		MutationBlockReplace replace = new MutationBlockReplace(target, ENV.special.AIR, WATER_SOURCE);
		ByteBuffer buffer = ByteBuffer.allocate(64);
		replace.serializeToBuffer(buffer);
		buffer.flip();
		MutationBlockReplace test = MutationBlockReplace.deserialize(new DeserializationContext(ENV
			, buffer
			, 0L
			, false
		));
		Assert.assertNotNull(test);
	}

	@Test
	public void transferBetweenInventory()
	{
		// Test that we can transfer items between blocks with MutationBlockPushToBlock.
		Block chest = ENV.blocks.fromItem(ENV.items.getItemById("op.chest"));
		AbsoluteLocation source = new AbsoluteLocation(3, 5, 5);
		AbsoluteLocation sink = new AbsoluteLocation(5, 5, 5);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(source.getCuboidAddress(), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, source.getBlockAddress(), chest.item().number());
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, source.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(CHARCOAL_ITEM, 2).finish());
		cuboid.setData15(AspectRegistry.BLOCK, sink.getBlockAddress(), chest.item().number());
		
		IMutationBlock[] outMutation = new IMutationBlock[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public boolean next(IMutationBlock mutation)
						{
							Assert.assertNull(outMutation[0]);
							outMutation[0] = mutation;
							return true;
						}
						@Override
						public boolean future(IMutationBlock mutation, long millisToDelay)
						{
							throw new AssertionError("Not expected in test");
						}
					}, null)
				.finish()
		;
		
		MutationBlockPushToBlock mutation = new MutationBlockPushToBlock(source, 1, 1, Inventory.INVENTORY_ASPECT_INVENTORY, sink);
		MutableBlockProxy sourceProxy = new MutableBlockProxy(source, cuboid);
		MutableBlockProxy sinkProxy = new MutableBlockProxy(sink, cuboid);
		Assert.assertTrue(mutation.applyMutation(context, sourceProxy));
		Assert.assertTrue(sourceProxy.didChange());
		Assert.assertEquals(1, sourceProxy.getInventory().getCount(CHARCOAL_ITEM));
		
		Assert.assertTrue(outMutation[0].applyMutation(context, sinkProxy));
		Assert.assertTrue(sinkProxy.didChange());
		Assert.assertEquals(1, sinkProxy.getInventory().getCount(CHARCOAL_ITEM));
	}

	@Test
	public void placeDuringLogicHigh()
	{
		// Place a lamp next to an activated switch to show that it turns into the "high" variant (since we only place "low" variants).
		Block switc = ENV.blocks.fromItem(ENV.items.getItemById("op.switch"));
		Block lamp = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp"));
		int entityId = 1;
		AbsoluteLocation switchLocation = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation lampLocation = switchLocation.getRelative(1, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(switchLocation.getCuboidAddress(), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress(), switc.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, switchLocation.getBlockAddress(), FlagsAspect.FLAG_ACTIVE);
		
		ProcessingSinks sinks = new ProcessingSinks();
		TickProcessingContext context = sinks.createBoundContext(cuboid);
		
		sinks.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, lampLocation, 0, entityId));
		MutationBlockOverwriteByEntity mutation = new MutationBlockOverwriteByEntity(lampLocation, lamp, null, entityId);
		MutableBlockProxy proxy = new MutableBlockProxy(lampLocation, cuboid);
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(lamp, proxy.getBlock());
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, proxy.getFlags());
	}

	@Test
	public void wheatGrowth()
	{
		// We will place a wheat seedling and run a MutationBlockGrow mutation against it to verify it checks the right things with/without light.
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Block wheatSeedling = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_seedling"));
		Block wheatYoung = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_young"));
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 0), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), wheatSeedling.item().number());
		
		// First, we want to make sure that the wheat fails to grow due to darkness.
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}, null, null)
				.skyLight((AbsoluteLocation blockLocation) -> (byte)0)
				.sinks(new TickProcessingContext.IMutationSink() {
							@Override
							public boolean next(IMutationBlock mutation)
							{
								throw new AssertionError("Not expected in test");
							}
							@Override
							public boolean future(IMutationBlock mutation, long millisToDelay)
							{
								throw new AssertionError("Not expected in test");
							}
						}
						, null)
				.fixedRandom(1)
				.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockPeriodic mutation = new MutationBlockPeriodic(target);
		boolean didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertFalse(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS, proxy.periodicDelayMillis);
		
		// Now, show that it works if there is light.
		proxy.periodicDelayMillis = 0L;
		context = ContextBuilder.nextTick(context, 1L)
				.skyLight((AbsoluteLocation blockLocation) -> PlantHelpers.MIN_LIGHT)
				.finish()
		;
		didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(wheatYoung, proxy.getBlock());
		Assert.assertEquals(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS, proxy.periodicDelayMillis);
	}

	@Test
	public void futureSchedulingDetail()
	{
		// We will just show how requesting a future update deals with the cases where there already is one.
		// We should always result in the "soonest" being kept.
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Block wheatSeedling = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_seedling"));
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 0), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), wheatSeedling.item().number());
		
		// First, we want to make sure that the wheat fails to grow due to darkness.
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}, null, null)
				.skyLight((AbsoluteLocation blockLocation) -> (byte)0)
				.fixedRandom(1)
				.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockPeriodic mutation = new MutationBlockPeriodic(target);
		boolean didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertFalse(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS, proxy.periodicDelayMillis);
		
		// Now change the update delay to a later one and observe that re-running this will cause it to update it.
		proxy.periodicDelayMillis = 2 * MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS;
		didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertFalse(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS, proxy.periodicDelayMillis);
		
		// We can also show that a sooner value will not be updated.
		proxy.periodicDelayMillis = MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS / 2;
		didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertFalse(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS / 2, proxy.periodicDelayMillis);
	}

	@Test
	public void blockUpdateHopper()
	{
		// Show that a block update delivered to a hopper will cause it to schedule an update event.
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Block hopper = ENV.blocks.fromItem(ENV.items.getItemById("op.hopper"));
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 0), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), hopper.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, BlockAddress.fromInt(1, 1, 1), OrientationAspect.directionToByte(OrientationAspect.Direction.DOWN));
		
		// First, we want to make sure that the wheat fails to grow due to darkness.
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}, null, null)
				.skyLight((AbsoluteLocation blockLocation) -> (byte)0)
				.fixedRandom(1)
				.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockUpdate update = new MutationBlockUpdate(target);
		boolean didApply = update.applyMutation(context, proxy);
		// This should cause no change.
		Assert.assertFalse(didApply);
		Assert.assertFalse(proxy.didChange());
		Assert.assertEquals(hopper, proxy.getBlock());
		Assert.assertEquals(MutationBlockPeriodic.MILLIS_BETWEEN_HOPPER_CALLS, proxy.periodicDelayMillis);
	}

	@Test
	public void furnaceExhaustFuel()
	{
		// Create a furnace and load its inventory and fuel, but not enough fuel to complete the craft, and verify that it fails.
		AbsoluteLocation target = new AbsoluteLocation(15, 15, 15);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), ENV.items.getItemById("op.furnace").number());
		Item log = ENV.items.getItemById("op.log");
		Inventory inv = Inventory.start(50).addStackable(log, 1).finish();
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress(), inv);
		MutationBlockFurnaceCraft[] holder = new MutationBlockFurnaceCraft[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public boolean next(IMutationBlock mutation)
						{
							holder[0] = (MutationBlockFurnaceCraft) mutation;
							return true;
						}
						@Override
						public boolean future(IMutationBlock mutation, long millisToDelay)
						{
							throw new AssertionError("Not expected in test");
						}
					}, null)
				.finish()
		;
		
		// We store the fuel in to kick this off the normal way.
		MutationBlockStoreItems storeFuel = new MutationBlockStoreItems(target, new Items(ENV.items.getItemById("op.sapling"), 1), null, Inventory.INVENTORY_ASPECT_FUEL);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(storeFuel.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		
		// This shouldn't yet be using the fuel, but it should be present.
		FuelState fuel = proxy.getFuel();
		Assert.assertEquals(0, fuel.millisFuelled());
		Assert.assertEquals(2, fuel.fuelInventory().currentEncumbrance);
		CraftOperation runningCraft = proxy.getCrafting();
		Assert.assertNull(runningCraft);
		
		// Now apply the craft call (this should need to be done 5 times with the current configuration).
		for (int i = 0; i < 5; ++i)
		{
			MutationBlockFurnaceCraft craft = holder[0];
			holder[0] = null;
			proxy = new MutableBlockProxy(target, cuboid);
			Assert.assertTrue(craft.applyMutation(context, proxy));
			Assert.assertTrue(proxy.didChange());
			proxy.writeBack(cuboid);
			
			fuel = proxy.getFuel();
			runningCraft = proxy.getCrafting();
		}
		
		// Now we should be done with fuel and the crafting should abort without completing the craft.
		fuel = proxy.getFuel();
		Assert.assertEquals(0, fuel.millisFuelled());
		Assert.assertEquals(0, fuel.fuelInventory().currentEncumbrance);
		Assert.assertNull(holder[0]);
		runningCraft = proxy.getCrafting();
		Assert.assertNull(runningCraft);
		inv = proxy.getInventory();
		Assert.assertEquals(1, inv.getCount(log));
	}

	@Test
	public void testSuffocation()
	{
		// We will invoke the TickUtils a few ways to test what happens with suffocation.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), WATER_SOURCE);
		int entityId = 1;
		MutableEntity entity = MutableEntity.createForTest(entityId);
		entity.setBreath((byte)1);
		entity.setHealth((byte)(2 * MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND));
		
		// Run a tick which isn't end of the second - should change nothing.
		TickProcessingContext context = ContextBuilder.build()
				.tick(1L)
				.millisPerTick(20L)
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null, null)
				.eventSink(new _Events())
				.finish();
		
		if (TickUtils.canApplyEnvironmentalDamageInTick(context))
		{
			TickUtils.applyEnvironmentalDamage(context, entity);
		}
		Assert.assertEquals((byte)1, entity.getBreath());
		Assert.assertEquals((byte)(2 * MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND), entity.getHealth());
		
		// Now, do one at the end of the second to see the breath run out.
		_Events events = new _Events();
		context = ContextBuilder.build()
				.tick(50L)
				.millisPerTick(20L)
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null, null)
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
					{
						throw new AssertionError("Not expected in test");
					}
					@Override
					public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
					{
						throw new AssertionError("Not expected in test");
					}
					@Override
					public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
					{
						throw new AssertionError("Not expected in test");
					}
					@Override
					public boolean passive(int targetPassiveId, IPassiveAction action)
					{
						throw new AssertionError("Not expected in test");
					}
				})
				.eventSink(events)
				.finish();
		
		if (TickUtils.canApplyEnvironmentalDamageInTick(context))
		{
			TickUtils.applyEnvironmentalDamage(context, entity);
		}
		Assert.assertEquals((byte)0, entity.getBreath());
		Assert.assertEquals((byte)(2 * MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND), entity.getHealth());
		
		// Run again to show the damage taken.
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.SUFFOCATION, entity.newLocation.getBlockLocation(), entity.getId(), 0));
		if (TickUtils.canApplyEnvironmentalDamageInTick(context))
		{
			TickUtils.applyEnvironmentalDamage(context, entity);
		}
		Assert.assertEquals((byte)0, entity.getBreath());
		Assert.assertEquals(MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND, entity.getHealth());
	}

	@Test
	public void repairCases()
	{
		// Try to repair a damaged block, an undamaged block, and a block of the wrong type.
		AbsoluteLocation wrongType = new AbsoluteLocation(5, 0, 10);
		AbsoluteLocation noDamage = new AbsoluteLocation(5, 1, 10);
		AbsoluteLocation valid = new AbsoluteLocation(6, 0, 10);
		short damaged = 150;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, noDamage.getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, valid.getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.DAMAGE, valid.getBlockAddress(), damaged);
		
		short repairMillis = 100;
		MutableBlockProxy wrongProxy = new MutableBlockProxy(wrongType, cuboid);
		MutableBlockProxy noDamangeProxy = new MutableBlockProxy(noDamage, cuboid);
		MutableBlockProxy validProxy = new MutableBlockProxy(valid, cuboid);
		
		// Try wrong type.
		MutationBlockIncrementalRepair repairWrongType = new MutationBlockIncrementalRepair(wrongType, repairMillis);
		Assert.assertFalse(repairWrongType.applyMutation(null, wrongProxy));
		
		// Try undamaged
		MutationBlockIncrementalRepair repairNoDamange = new MutationBlockIncrementalRepair(noDamage, repairMillis);
		Assert.assertFalse(repairNoDamange.applyMutation(null, noDamangeProxy));
		
		// Try valid
		MutationBlockIncrementalRepair repairValid = new MutationBlockIncrementalRepair(valid, repairMillis);
		Assert.assertTrue(repairValid.applyMutation(null, validProxy));
		Assert.assertEquals((short)50, validProxy.getDamage());
		Assert.assertTrue(repairValid.applyMutation(null, validProxy));
		Assert.assertEquals((short)0, validProxy.getDamage());
		Assert.assertFalse(repairValid.applyMutation(null, validProxy));
	}

	@Test
	public void infiniteWaterSource()
	{
		// Show that removing a block from an infinite water source reschedules a flow action.
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), STONE);
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), waterSource.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 1, 0).getBlockAddress(), waterSource.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 0).getBlockAddress(), waterSource.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 0).getBlockAddress(), waterSource.item().number());
		
		// First, we want to make sure that the wheat fails to grow due to darkness.
		IMutationBlock[] out_mutation = new IMutationBlock[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}, null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public boolean next(IMutationBlock mutation)
					{
						throw new AssertionError("Not expected in test");
					}
					@Override
					public boolean future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.assertNotNull(mutation);
						Assert.assertTrue(millisToDelay > 0L);
						Assert.assertNull(out_mutation[0]);
						out_mutation[0] = mutation;
						return true;
					}
				}, null)
				.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockReplace replace = new MutationBlockReplace(target, waterSource, ENV.special.AIR);
		boolean didApply = replace.applyMutation(context, proxy);
		
		// This should schedule a flow operation for the future.
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		Assert.assertTrue(out_mutation[0] instanceof MutationBlockLiquidFlowInto);
	}

	@Test
	public void waterFlowBreaksItems()
	{
		// We want to verify what happens when a block adjacent to a liquid source, which should be broken by it, will cause it to flow and break the block.
		Block lavaSource = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.lava_source"));
		Block tilledBlock = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.tilled_soil"));
		Block wheatMatureBlock = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.wheat_mature"));
		AbsoluteLocation water = new AbsoluteLocation(15, 15, 15);
		AbsoluteLocation lava = water.getRelative(2, 0, 0);
		AbsoluteLocation wheat1 = water.getRelative( 1, 0, 0);
		AbsoluteLocation wheat2 = water.getRelative(-1, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(water.getCuboidAddress(), tilledBlock);
		cuboid.setData15(AspectRegistry.BLOCK, water.getBlockAddress(), WATER_SOURCE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, lava.getBlockAddress(), lavaSource.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, wheat1.getBlockAddress(), wheatMatureBlock.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, wheat2.getBlockAddress(), wheatMatureBlock.item().number());
		
		_Events events = new _Events();
		IMutationBlock[] out_mutation = new IMutationBlock[1];
		List<PassiveEntity> out_passives = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.fixedRandom(0)
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public boolean next(IMutationBlock mutation)
						{
							throw new AssertionError("Not expected in test");
						}
						@Override
						public boolean future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.assertNull(out_mutation[0]);
							out_mutation[0] = mutation;
							return true;
						}
					}, null)
				.eventSink(events)
				.passive((PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
					long lastAliveMillis = 1000L;
					PassiveEntity passive = new PassiveEntity(out_passives.size() + 1
						, type
						, location
						, velocity
						, extendedData
						, lastAliveMillis
					);
					out_passives.add(passive);
				})
				.finish()
		;
		
		// We should see the block between the water and lava replaced by water.
		MutableBlockProxy proxy1 = new MutableBlockProxy(wheat1, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(wheat1).applyMutation(context, proxy1));
		proxy1.writeBack(cuboid);
		Assert.assertEquals(wheatMatureBlock, proxy1.getBlock());
		MutationBlockLiquidFlowInto internal = (MutationBlockLiquidFlowInto)out_mutation[0];
		Assert.assertTrue(internal instanceof MutationBlockLiquidFlowInto);
		out_mutation[0] = null;
		Assert.assertEquals(0, out_passives.size());
		Assert.assertTrue(internal.applyMutation(context, proxy1));
		proxy1.writeBack(cuboid);
		Assert.assertNull(out_mutation[0]);
		Assert.assertEquals(2, out_passives.size());
		Assert.assertNull(proxy1.getInventory());
		Assert.assertEquals(STONE, proxy1.getBlock());
		
		// We should see the block next to to the water contain the dropped items.
		MutableBlockProxy proxy2 = new MutableBlockProxy(wheat2, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(wheat2).applyMutation(context, proxy2));
		proxy2.writeBack(cuboid);
		Assert.assertEquals(wheatMatureBlock, proxy2.getBlock());
		internal = (MutationBlockLiquidFlowInto)out_mutation[0];
		Assert.assertTrue(internal instanceof MutationBlockLiquidFlowInto);
		out_mutation[0] = null;
		Assert.assertEquals(2, out_passives.size());
		Assert.assertTrue(internal.applyMutation(context, proxy2));
		proxy2.writeBack(cuboid);
		Assert.assertNull(out_mutation[0]);
		Assert.assertEquals(4, out_passives.size());
		Assert.assertEquals(WATER_STRONG, proxy2.getBlock());
	}

	@Test
	public void lavaStartsFires()
	{
		// Place a lava source, much like a bucket would, and observe the progression of fires.
		AbsoluteLocation lavaSpot = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation woodSpot1 = new AbsoluteLocation(6, 5, 5);
		AbsoluteLocation woodSpot2 = new AbsoluteLocation(7, 5, 5);
		Item log = ENV.items.getItemById("op.log");
		Block lavaSource = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.lava_source"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, lavaSpot.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, woodSpot1.getBlockAddress(), log.number());
		cuboid.setData15(AspectRegistry.BLOCK, woodSpot2.getBlockAddress(), log.number());
		
		// Create the context we want to use.
		MutationBlockStartFire[] out_startFire = new MutationBlockStartFire[1];
		MutationBlockBurnDown[] out_burnDown = new MutationBlockBurnDown[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public boolean next(IMutationBlock mutation)
					{
						throw new AssertionError("Not expected in test");
					}
					@Override
					public boolean future(IMutationBlock mutation, long millisToDelay)
					{
						// We only expect one of each.
						if (mutation instanceof MutationBlockStartFire)
						{
							Assert.assertNull(out_startFire[0]);
							Assert.assertEquals(MutationBlockStartFire.IGNITION_DELAY_MILLIS, millisToDelay);
							out_startFire[0] = (MutationBlockStartFire) mutation;
						}
						else if (mutation instanceof MutationBlockBurnDown)
						{
							Assert.assertNull(out_burnDown[0]);
							Assert.assertEquals(MutationBlockBurnDown.BURN_DELAY_MILLIS, millisToDelay);
							out_burnDown[0] = (MutationBlockBurnDown) mutation;
						}
						else
						{
							// In this case, we expect the liquid movement but we don't actually want to apply that.
							Assert.assertTrue(mutation instanceof MutationBlockLiquidFlowInto);
						}
						return true;
					}
				}, null)
				.finish()
		;
		
		// Place the source and observe the ignition mutation scheduled.
		MutableBlockProxy proxy = new MutableBlockProxy(lavaSpot, cuboid);
		MutationBlockReplace placeLava = new MutationBlockReplace(lavaSpot, ENV.special.AIR, lavaSource);
		Assert.assertTrue(placeLava.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Apply the ignition mutation.
		MutationBlockStartFire ignite = out_startFire[0];
		out_startFire[0] = null;
		proxy = new MutableBlockProxy(ignite.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(ignite.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Capture the first burn down.
		MutationBlockBurnDown burnDown = out_burnDown[0];
		out_burnDown[0] = null;
		
		// Apply the next ignition mutation.
		ignite = out_startFire[0];
		out_startFire[0] = null;
		proxy = new MutableBlockProxy(ignite.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(ignite.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Apply the first burn down.
		proxy = new MutableBlockProxy(burnDown.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(burnDown.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Apply the second burn down.
		burnDown = out_burnDown[0];
		out_burnDown[0] = null;
		proxy = new MutableBlockProxy(burnDown.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(burnDown.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Verify final state.
		Assert.assertNull(out_burnDown[0]);
		Assert.assertNull(out_startFire[0]);
		Assert.assertEquals(lavaSource.item().number(), cuboid.getData15(AspectRegistry.BLOCK, lavaSpot.getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, lavaSpot.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, woodSpot1.getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, woodSpot1.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, woodSpot2.getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, woodSpot2.getBlockAddress()));
	}

	@Test
	public void extinguishFire()
	{
		// Create a burning block with a water source on top of it and show that a block update in the fire block extinguishes it.
		AbsoluteLocation burning = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation water = burning.getRelative(0, 0, 1);
		Item log = ENV.items.getItemById("op.log");
		Item waterSource = ENV.items.getItemById("op.water_source");
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, burning.getBlockAddress(), log.number());
		cuboid.setData7(AspectRegistry.FLAGS, burning.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		cuboid.setData15(AspectRegistry.BLOCK, water.getBlockAddress(), waterSource.number());
		
		// Create the context we want to use.
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		
		// We apply an update mutation to the log as though the water was just placed.
		MutableBlockProxy proxy = new MutableBlockProxy(burning, cuboid);
		MutationBlockUpdate update = new MutationBlockUpdate(burning);
		Assert.assertTrue(update.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Verify final state.
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, burning.getBlockAddress()));
		Assert.assertEquals(log.number(), cuboid.getData15(AspectRegistry.BLOCK, burning.getBlockAddress()));
	}

	@Test
	public void setLogicState()
	{
		// We will show that we can change the logic state of something like a door but not a lamp (since it isn't manual).
		AbsoluteLocation doorLocation = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation lampLocation = new AbsoluteLocation(6, 6, 6);
		Item door = ENV.items.getItemById("op.door");
		Item lamp = ENV.items.getItemById("op.lamp");
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, doorLocation.getBlockAddress(), door.number());
		cuboid.setData15(AspectRegistry.BLOCK, lampLocation.getBlockAddress(), lamp.number());
		
		// Test with the door.
		MutableBlockProxy proxy = new MutableBlockProxy(doorLocation, cuboid);
		MutationBlockSetLogicState mutation = new MutationBlockSetLogicState(doorLocation, true);
		Assert.assertTrue(mutation.applyMutation(null, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(door.number(), cuboid.getData15(AspectRegistry.BLOCK, doorLocation.getBlockAddress()));
		Assert.assertTrue(FlagsAspect.isSet(cuboid.getData7(AspectRegistry.FLAGS, doorLocation.getBlockAddress()), FlagsAspect.FLAG_ACTIVE));
		
		// Show that it fails if the value is already high.
		Assert.assertFalse(mutation.applyMutation(null, proxy));
		
		// Test with the lamp and show that it doesn't pass.
		proxy = new MutableBlockProxy(lampLocation, cuboid);
		mutation = new MutationBlockSetLogicState(lampLocation, true);
		Assert.assertFalse(mutation.applyMutation(null, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(lamp.number(), cuboid.getData15(AspectRegistry.BLOCK, lampLocation.getBlockAddress()));
		Assert.assertFalse(FlagsAspect.isSet(cuboid.getData7(AspectRegistry.FLAGS, lampLocation.getBlockAddress()), FlagsAspect.FLAG_ACTIVE));
	}

	@Test
	public void setLogicStateMultiBlock()
	{
		// Place a multi-block door and show that it responds to a logic update signal in the root block only (using synthetic MutationBlockLogicChange).
		AbsoluteLocation doorLocation = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation nonBaseLocation = doorLocation.getRelative(1, 0, 0);
		Item door = ENV.items.getItemById("op.double_door_base");
		Item logicWire = ENV.items.getItemById("op.logic_wire");
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, doorLocation.getBlockAddress(), door.number());
		cuboid.setData7(AspectRegistry.ORIENTATION, doorLocation.getBlockAddress(), OrientationAspect.directionToByte(OrientationAspect.Direction.NORTH));
		cuboid.setData15(AspectRegistry.BLOCK, doorLocation.getRelative(0, 0, 1).getBlockAddress(), door.number());
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, doorLocation.getRelative(0, 0, 1).getBlockAddress(), doorLocation);
		cuboid.setData15(AspectRegistry.BLOCK, doorLocation.getRelative(1, 0, 1).getBlockAddress(), door.number());
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, doorLocation.getRelative(1, 0, 1).getBlockAddress(), doorLocation);
		cuboid.setData15(AspectRegistry.BLOCK, nonBaseLocation.getBlockAddress(), door.number());
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, nonBaseLocation.getBlockAddress(), doorLocation);
		
		// Create the context.
		List<IMutationBlock> out_mutations = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public boolean next(IMutationBlock mutation)
					{
						out_mutations.add(mutation);
						return true;
					}
					@Override
					public boolean future(IMutationBlock mutation, long millisToDelay)
					{
						throw new AssertionError("Not expected in test");
					}
				}, null)
				.finish()
		;
		
		// Test the logic update in a non-root block.
		AbsoluteLocation conduitLocation = nonBaseLocation.getRelative(1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, conduitLocation.getBlockAddress(), logicWire.number());
		cuboid.setData7(AspectRegistry.LOGIC, conduitLocation.getBlockAddress(), LogicAspect.MAX_LEVEL);
		MutableBlockProxy proxy = new MutableBlockProxy(nonBaseLocation, cuboid);
		MutationBlockLogicChange mutation = new MutationBlockLogicChange(nonBaseLocation);
		Assert.assertFalse(mutation.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		// This shouldn't change anything.
		Assert.assertEquals(door.number(), cuboid.getData15(AspectRegistry.BLOCK, nonBaseLocation.getBlockAddress()));
		Assert.assertEquals(0, out_mutations.size());
		
		// Test the logic update in a root block.
		AbsoluteLocation rootConduitLocation = doorLocation.getRelative(-1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, rootConduitLocation.getBlockAddress(), logicWire.number());
		cuboid.setData7(AspectRegistry.LOGIC, rootConduitLocation.getBlockAddress(), LogicAspect.MAX_LEVEL);
		proxy = new MutableBlockProxy(doorLocation, cuboid);
		mutation = new MutationBlockLogicChange(doorLocation);
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// This should enqueue an update to each block in the multi-block.
		Assert.assertEquals(4, out_mutations.size());
		for (IMutationBlock one : out_mutations)
		{
			proxy = new MutableBlockProxy(one.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(one.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
		}
		// All the blocks should now be open.
		Assert.assertEquals(door.number(), cuboid.getData15(AspectRegistry.BLOCK, doorLocation.getBlockAddress()));
		Assert.assertTrue(FlagsAspect.isSet(cuboid.getData7(AspectRegistry.FLAGS, doorLocation.getBlockAddress()), FlagsAspect.FLAG_ACTIVE));
		Assert.assertEquals(door.number(), cuboid.getData15(AspectRegistry.BLOCK, doorLocation.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertTrue(FlagsAspect.isSet(cuboid.getData7(AspectRegistry.FLAGS, doorLocation.getRelative(0, 0, 1).getBlockAddress()), FlagsAspect.FLAG_ACTIVE));
		Assert.assertEquals(door.number(), cuboid.getData15(AspectRegistry.BLOCK, doorLocation.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertTrue(FlagsAspect.isSet(cuboid.getData7(AspectRegistry.FLAGS, doorLocation.getRelative(1, 0, 1).getBlockAddress()), FlagsAspect.FLAG_ACTIVE));
		Assert.assertEquals(door.number(), cuboid.getData15(AspectRegistry.BLOCK, nonBaseLocation.getBlockAddress()));
		Assert.assertTrue(FlagsAspect.isSet(cuboid.getData7(AspectRegistry.FLAGS, nonBaseLocation.getBlockAddress()), FlagsAspect.FLAG_ACTIVE));
	}

	@Test
	public void groundCoverChanges()
	{
		// We will show a few of the ground cover interactions (these are similar so we will combine them here to avoid duplicated boiler-plate).
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		Block grass = ENV.blocks.fromItem(ENV.items.getItemById("op.grass"));
		Block torch = ENV.blocks.fromItem(ENV.items.getItemById("op.torch"));
		AbsoluteLocation startDirt = new AbsoluteLocation(2, 2, 2);
		AbsoluteLocation startGrass = new AbsoluteLocation(22, 22, 22);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, startDirt.getBlockAddress(), dirt.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, startGrass.getBlockAddress(), grass.item().number());
		
		MutationBlockGrowGroundCover[] out_mutations = new MutationBlockGrowGroundCover[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public boolean next(IMutationBlock mutation)
					{
						throw new AssertionError("Not expected in test");
					}
					@Override
					public boolean future(IMutationBlock mutation, long millisToDelay)
					{
						// These are only for this one purpose.
						Assert.assertEquals(MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS, millisToDelay);
						Assert.assertNull(out_mutations[0]);
						out_mutations[0] = (MutationBlockGrowGroundCover) mutation;
						return true;
					}
				}, null)
				.eventSink((EventRecord event) -> {})
				.finish()
		;
		
		// Test placing dirt next to existing grass.
		AbsoluteLocation besideGrass = startGrass.getRelative(1, 0, 0);
		MutableBlockProxy proxy = new MutableBlockProxy(besideGrass, cuboid);
		MutationBlockOverwriteByEntity write = new MutationBlockOverwriteByEntity(besideGrass, dirt, null, 1);
		Assert.assertTrue(write.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		MutationBlockGrowGroundCover update = out_mutations[0];
		out_mutations[0] = null;
		AbsoluteLocation updateLocation = update.getAbsoluteLocation();
		Assert.assertEquals(besideGrass, updateLocation);
		proxy = new MutableBlockProxy(updateLocation, cuboid);
		Assert.assertTrue(update.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(grass.item().number(), cuboid.getData15(AspectRegistry.BLOCK, besideGrass.getBlockAddress()));
		
		// Test placing grass next to existing dirt.
		AbsoluteLocation besideDirt = startDirt.getRelative(1, 0, 0);
		proxy = new MutableBlockProxy(besideDirt, cuboid);
		write = new MutationBlockOverwriteByEntity(besideDirt, grass, null, 1);
		Assert.assertTrue(write.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		update = out_mutations[0];
		out_mutations[0] = null;
		updateLocation = update.getAbsoluteLocation();
		Assert.assertEquals(startDirt, updateLocation);
		proxy = new MutableBlockProxy(updateLocation, cuboid);
		Assert.assertTrue(update.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(grass.item().number(), cuboid.getData15(AspectRegistry.BLOCK, startDirt.getBlockAddress()));
		
		// Test placing dirt on top of existing grass.
		AbsoluteLocation aboveGrass = startGrass.getRelative(0, 0, 1);
		cuboid.setData15(AspectRegistry.BLOCK, aboveGrass.getBlockAddress(), dirt.item().number());
		proxy = new MutableBlockProxy(startGrass, cuboid);
		MutationBlockUpdate blockUpdate = new MutationBlockUpdate(startGrass);
		Assert.assertTrue(blockUpdate.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(dirt.item().number(), cuboid.getData15(AspectRegistry.BLOCK, startGrass.getBlockAddress()));
		
		// Test removing this dirt block to show that we re-spread it from besideGrass.
		cuboid.setData15(AspectRegistry.BLOCK, aboveGrass.getBlockAddress(), ENV.special.AIR.item().number());
		proxy = new MutableBlockProxy(startGrass, cuboid);
		blockUpdate = new MutationBlockUpdate(startGrass);
		Assert.assertTrue(blockUpdate.applyMutation(context, proxy));
		update = out_mutations[0];
		out_mutations[0] = null;
		Assert.assertTrue(update.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(grass.item().number(), cuboid.getData15(AspectRegistry.BLOCK, startGrass.getBlockAddress()));
		
		// Test placing a torch on top of existing grass since it should have no effect.
		cuboid.setData15(AspectRegistry.BLOCK, aboveGrass.getBlockAddress(), torch.item().number());
		proxy = new MutableBlockProxy(startGrass, cuboid);
		blockUpdate = new MutationBlockUpdate(startGrass);
		// This should do nothing.
		Assert.assertFalse(blockUpdate.applyMutation(context, proxy));
		Assert.assertNull(out_mutations[0]);
		proxy.writeBack(cuboid);
		Assert.assertEquals(grass.item().number(), cuboid.getData15(AspectRegistry.BLOCK, startGrass.getBlockAddress()));
	}

	@Test
	public void replaceGrassWithFarm()
	{
		// This is a basic test to find a specific bug related to hoe usage.
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), STONE);
		Block grassBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.grass"));
		Block farmBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.tilled_soil"));
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), grassBlock.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 1, 0).getBlockAddress(), grassBlock.item().number());
		
		// Just run the operation, basically.
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}, null, null)
				.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockReplace replace = new MutationBlockReplace(target, grassBlock, farmBlock);
		boolean didApply = replace.applyMutation(context, proxy);
		
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(farmBlock.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
	}

	@Test
	public void breakPedestal()
	{
		// A test to show that the special slot contents of a pedestal drop in the world.
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Item pedestalItem = ENV.items.getItemById("op.pedestal");
		Block pedestalBlock = ENV.blocks.fromItem(pedestalItem);
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), pedestalBlock.item().number());
		cuboid.setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, target.getBlockAddress(), ItemSlot.fromStack(new Items(STONE_ITEM, 2)));
		
		List<PassiveEntity> out_passives = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation blockLocation) -> {
				return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}, null, null)
			.eventSink(new TickProcessingContext.IEventSink() {
				@Override
				public void post(EventRecord event)
				{
				}
			})
			.passive((PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
				long lastAliveMillis = 1000L;
				PassiveEntity passive = new PassiveEntity(out_passives.size() + 1
					, type
					, location
					, velocity
					, extendedData
					, lastAliveMillis
				);
				out_passives.add(passive);
			})
			.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockIncrementalBreak breaking = new MutationBlockIncrementalBreak(target, ENV.damage.getToughness(pedestalBlock), 0);
		boolean didApply = breaking.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(2, out_passives.size());
		Assert.assertEquals(target.toEntityLocation(), out_passives.get(0).location());
		Assert.assertEquals(target.toEntityLocation(), out_passives.get(1).location());
		ItemSlot firstSlot = (ItemSlot)out_passives.get(0).extendedData();
		ItemSlot secondSlot = (ItemSlot)out_passives.get(1).extendedData();
		Assert.assertEquals(STONE_ITEM, firstSlot.getType());
		Assert.assertEquals(2, firstSlot.getCount());
		Assert.assertEquals(pedestalItem, secondSlot.getType());
		Assert.assertEquals(1, secondSlot.getCount());
	}

	@Test
	public void pedestalSwap()
	{
		// A test to show special slot interactions with a pedestal block.
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Block pedestalBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.pedestal"));
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), pedestalBlock.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation blockLocation) -> {
				return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}, null, null)
			.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		ItemSlot slotToStore = ItemSlot.fromStack(new Items(STONE.item(), 5));
		MutationBlockSwapSpecialSlot swap = new MutationBlockSwapSpecialSlot(target, slotToStore, 0);
		boolean didApply = swap.applyMutation(context, proxy);
		
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(pedestalBlock.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(slotToStore, cuboid.getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, target.getBlockAddress()));
		
		// Now, swap it out.
		proxy = new MutableBlockProxy(target, cuboid);
		swap = new MutationBlockSwapSpecialSlot(target, null, 0);
		didApply = swap.applyMutation(context, proxy);
		
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(pedestalBlock.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(null, cuboid.getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, target.getBlockAddress()));
	}

	@Test
	public void placePortalCornerstone()
	{
		// A test to show how portals become active or inactive.
		AbsoluteLocation target = new AbsoluteLocation(5, 5, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Block portalBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_keystone"));
		Block voidBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.void_stone"));
		Set<AbsoluteLocation> outline = _getEastFacingPortalVoidStones(target);
		for (AbsoluteLocation location : outline)
		{
			cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), voidBlock.item().number());
		}
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation blockLocation) -> {
				return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}, null, null)
			.eventSink(new TickProcessingContext.IEventSink() {
				@Override
				public void post(EventRecord event)
				{
					// Do nothing.
				}
			})
			.finish()
		;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockOverwriteByEntity overwrite = new MutationBlockOverwriteByEntity(target, portalBlock, OrientationAspect.Direction.EAST, 1);
		boolean didApply = overwrite.applyMutation(context, proxy);
		
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		
		// Verify that it is active and requested an update check.
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getBlockAddress()));
		Assert.assertEquals(CompositeHelpers.COMPOSITE_CHECK_FREQUENCY, proxy.periodicDelayMillis);
		
		// Invalidate one of the corners and run the re-check.
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 2, 4).getBlockAddress(), STONE.item().number());
		MutationBlockPeriodic periodic = new MutationBlockPeriodic(target);
		proxy = new MutableBlockProxy(target, cuboid);
		didApply = periodic.applyMutation(context, proxy);
		Assert.assertFalse(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		
		// Verify that it is inactive but requested an update check.
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, target.getBlockAddress()));
		Assert.assertEquals(CompositeHelpers.COMPOSITE_CHECK_FREQUENCY, proxy.periodicDelayMillis);
	}

	@Test
	public void blockUpdatePortalSurface()
	{
		// Show what happens when we perform block updates on parts of the portal which must be supported by the keystone versus the rest.
		AbsoluteLocation target = new AbsoluteLocation(5, 5, 1);
		AbsoluteLocation corner = target.getRelative(0, -1, 0);
		AbsoluteLocation keystoneLocation = target.getRelative(0, 0, -1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Block portalBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_keystone"));
		Block surfaceBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_surface"));
		Set<AbsoluteLocation> outline = Set.of(
				corner
				, target.getRelative(0,  1, 0)
				, target.getRelative(0,  0, 1)
				, target.getRelative(0, -1, 1)
				, target.getRelative(0,  1, 1)
				, target.getRelative(0,  0, 2)
				, target.getRelative(0, -1, 2)
				, target.getRelative(0,  1, 2)
		);
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), portalBlock.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), surfaceBlock.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, target.getBlockAddress(), OrientationAspect.directionToByte(OrientationAspect.Direction.EAST));
		for (AbsoluteLocation location : outline)
		{
			cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), surfaceBlock.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, location.getBlockAddress(), target);
		}
		
		Set<AbsoluteLocation> replaceLocations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation blockLocation) -> {
				return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}, null, null)
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					Assert.assertTrue(mutation instanceof MutationBlockReplace);
					boolean didAdd = replaceLocations.add(mutation.getAbsoluteLocation());
					Assert.assertTrue(didAdd);
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					throw new AssertionError("Not expected in test");
				}
			}, null)
			.finish()
		;
		
		// Check the base when there is a keystone.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		MutationBlockUpdate update = new MutationBlockUpdate(target);
		boolean didApply = update.applyMutation(context, proxy);
		Assert.assertFalse(didApply);
		Assert.assertFalse(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(0, replaceLocations.size());
		
		// Check the corner.
		proxy = new MutableBlockProxy(corner, cuboid);
		update = new MutationBlockUpdate(corner);
		didApply = update.applyMutation(context, proxy);
		Assert.assertFalse(didApply);
		Assert.assertFalse(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(0, replaceLocations.size());
		
		// Check the base when there is a no keystone.
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), ENV.special.AIR.item().number());
		proxy = new MutableBlockProxy(target, cuboid);
		update = new MutationBlockUpdate(target);
		didApply = update.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertFalse(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(9, replaceLocations.size());
		Assert.assertTrue(replaceLocations.contains(target));
		for (AbsoluteLocation location : outline)
		{
			Assert.assertTrue(replaceLocations.contains(location));
		}
	}

	@Test
	public void multiBlockSupport()
	{
		// Show that multi-block support only matters for the root.
		AbsoluteLocation keystoneLocation = new AbsoluteLocation(5, 5, 1);
		AbsoluteLocation aboveKeystone = keystoneLocation.getRelative(0, 0, 1);
		AbsoluteLocation notAboveKeystone = keystoneLocation.getRelative(1, 0, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(keystoneLocation.getCuboidAddress(), ENV.special.AIR);
		Block portalBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_keystone"));
		Block surfaceBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_surface"));
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), portalBlock.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation blockLocation) -> {
				return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}, null, null)
			.finish()
		;
		
		// Show that the root cares about this.
		MutationBlockPlaceMultiBlock rootPass = new MutationBlockPlaceMultiBlock(aboveKeystone, surfaceBlock, aboveKeystone, OrientationAspect.Direction.NORTH, 0);
		MutableBlockProxy proxy = new MutableBlockProxy(aboveKeystone, cuboid);
		boolean didApply = rootPass.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		MutationBlockPlaceMultiBlock rootFail = new MutationBlockPlaceMultiBlock(notAboveKeystone, surfaceBlock, notAboveKeystone, OrientationAspect.Direction.NORTH, 0);
		proxy = new MutableBlockProxy(notAboveKeystone, cuboid);
		didApply = rootFail.applyMutation(context, proxy);
		Assert.assertFalse(didApply);
		Assert.assertFalse(proxy.didChange());
		
		// But that the extensions do not.
		MutationBlockPlaceMultiBlock extensionPass = new MutationBlockPlaceMultiBlock(aboveKeystone, surfaceBlock, keystoneLocation, OrientationAspect.Direction.NORTH, 0);
		proxy = new MutableBlockProxy(aboveKeystone, cuboid);
		didApply = extensionPass.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		MutationBlockPlaceMultiBlock extensionFail = new MutationBlockPlaceMultiBlock(notAboveKeystone, surfaceBlock, keystoneLocation, OrientationAspect.Direction.NORTH, 0);
		proxy = new MutableBlockProxy(notAboveKeystone, cuboid);
		didApply = extensionFail.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
	}

	@Test
	public void periodicPortalUpdates()
	{
		// Show that a valid portal frame will generate a surface, on periodic update, and destroy it if the frame is damaged.
		AbsoluteLocation keystoneLocation = new AbsoluteLocation(5, 5, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(keystoneLocation.getCuboidAddress(), ENV.special.AIR);
		Block portalBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_keystone"));
		Block voidBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.void_stone"));
		Block surfaceBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_surface"));
		NonStackableItem portalOrb = new NonStackableItem(ENV.items.getItemById("op.portal_orb"), Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(10, 10, 10)));
		Set<AbsoluteLocation> outline = _getEastFacingPortalVoidStones(keystoneLocation);
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), portalBlock.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, keystoneLocation.getBlockAddress(), OrientationAspect.directionToByte(OrientationAspect.Direction.EAST));
		cuboid.setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, keystoneLocation.getBlockAddress(), ItemSlot.fromNonStack(portalOrb));
		for (AbsoluteLocation location : outline)
		{
			cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), voidBlock.item().number());
		}
		
		List<MutationBlockPlaceMultiBlock> phase1 = new ArrayList<>();
		List<MutationBlockPhase2Multi> phase2 = new ArrayList<>();
		List<MutationBlockReplace> replace = new ArrayList<>();
		Set<AbsoluteLocation> nextLocations = new HashSet<>();
		Set<AbsoluteLocation> futureLocations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation blockLocation) -> {
				return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}, null, null)
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					// This is used for both phase1 and replace mutations.
					if (mutation instanceof MutationBlockPlaceMultiBlock)
					{
						phase1.add((MutationBlockPlaceMultiBlock) mutation);
					}
					else
					{
						replace.add((MutationBlockReplace) mutation);
					}
					boolean didAdd = nextLocations.add(mutation.getAbsoluteLocation());
					Assert.assertTrue(didAdd);
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					// This test only uses this for phase2 mutations.
					phase2.add((MutationBlockPhase2Multi) mutation);
					Assert.assertEquals(ContextBuilder.DEFAULT_MILLIS_PER_TICK, millisToDelay);
					boolean didAdd = futureLocations.add(mutation.getAbsoluteLocation());
					Assert.assertTrue(didAdd);
					return true;
				}
			}, null)
			.finish()
		;
		
		// Show that a periodic update will cause it to create the portal surface.
		MutableBlockProxy proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		MutationBlockPeriodic periodic = new MutationBlockPeriodic(keystoneLocation);
		periodic.applyMutation(context, proxy);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, keystoneLocation.getBlockAddress()));
		Assert.assertEquals(9, nextLocations.size());
		Assert.assertEquals(9, futureLocations.size());
		
		// Now, run these other mutations to build the portal.
		nextLocations.clear();
		futureLocations.clear();
		for (MutationBlockPlaceMultiBlock mutation : phase1)
		{
			proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			mutation.applyMutation(context, proxy);
			Assert.assertTrue(proxy.didChange());
			proxy.writeBack(cuboid);
		}
		for (MutationBlockPhase2Multi mutation : phase2)
		{
			proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			mutation.applyMutation(context, proxy);
			Assert.assertFalse(proxy.didChange());
			proxy.writeBack(cuboid);
		}
		Assert.assertEquals(surfaceBlock.item().number(), cuboid.getData15(AspectRegistry.BLOCK, keystoneLocation.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(0, nextLocations.size());
		Assert.assertEquals(0, futureLocations.size());
		
		// Run another periodic and show that it changes nothing.
		proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		periodic = new MutationBlockPeriodic(keystoneLocation);
		periodic.applyMutation(context, proxy);
		Assert.assertFalse(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, keystoneLocation.getBlockAddress()));
		Assert.assertEquals(0, nextLocations.size());
		Assert.assertEquals(0, futureLocations.size());
		
		// Destroy the corner and show that the the periodic update destroys the portal surface.
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getRelative(0, 2, 0).getBlockAddress(), ENV.special.AIR.item().number());
		proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		periodic = new MutationBlockPeriodic(keystoneLocation);
		periodic.applyMutation(context, proxy);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, keystoneLocation.getBlockAddress()));
		Assert.assertEquals(9, nextLocations.size());
		Assert.assertEquals(0, futureLocations.size());
		
		// Now, run these replacement mutations to destroy the portal.
		nextLocations.clear();
		futureLocations.clear();
		for (MutationBlockReplace mutation : replace)
		{
			proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			mutation.applyMutation(context, proxy);
			Assert.assertTrue(proxy.didChange());
			proxy.writeBack(cuboid);
		}
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, keystoneLocation.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(0, nextLocations.size());
		Assert.assertEquals(0, futureLocations.size());
	}

	@Test
	public void revertMultiBlock()
	{
		AbsoluteLocation target = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation root = target.getRelative(0, 0, -1);
		Block surfaceBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_surface"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation blockLocation) -> {
				return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}, null, null)
			.finish()
		;
		
		// Place only the single block.
		MutationBlockPlaceMultiBlock place = new MutationBlockPlaceMultiBlock(target, surfaceBlock, root, OrientationAspect.Direction.NORTH, 0);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		boolean didApply = place.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(surfaceBlock.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		
		// Show that we fail and revert on phase2.
		MutationBlockPhase2Multi fail = new MutationBlockPhase2Multi(target, root, OrientationAspect.Direction.NORTH, surfaceBlock, ENV.special.AIR);
		proxy = new MutableBlockProxy(target, cuboid);
		didApply = fail.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
	}


	private static Set<AbsoluteLocation> _getEastFacingPortalVoidStones(AbsoluteLocation keystoneLocation)
	{
		Set<AbsoluteLocation> outline = Set.of(
				keystoneLocation.getRelative(0, -1, 0)
				, keystoneLocation.getRelative(0, -2, 0)
				, keystoneLocation.getRelative(0, -2, 1)
				, keystoneLocation.getRelative(0, -2, 2)
				, keystoneLocation.getRelative(0, -2, 3)
				, keystoneLocation.getRelative(0, -2, 4)
				, keystoneLocation.getRelative(0, 1, 0)
				, keystoneLocation.getRelative(0, 2, 0)
				, keystoneLocation.getRelative(0, 2, 1)
				, keystoneLocation.getRelative(0, 2, 2)
				, keystoneLocation.getRelative(0, 2, 3)
				, keystoneLocation.getRelative(0, 2, 4)
				, keystoneLocation.getRelative(0, -1, 4)
				, keystoneLocation.getRelative(0, 0, 4)
				, keystoneLocation.getRelative(0, 1, 4)
		);
		return outline;
	}


	private static class ProcessingSinks
	{
		public IMutationBlock nextMutation;
		public int nextTargetEntityId;
		public IEntityAction<IMutablePlayerEntity> nextChange;
		public final _Events events = new _Events();
		
		public TickProcessingContext createBoundContext(CuboidData cuboid)
		{
			return ContextBuilder.build()
					.lookups((AbsoluteLocation blockLocation) -> {
							return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
						}, null, null)
					.sinks(new TickProcessingContext.IMutationSink() {
							@Override
							public boolean next(IMutationBlock mutation)
							{
								ProcessingSinks.this.nextMutation = mutation;
								return true;
							}
							@Override
							public boolean future(IMutationBlock mutation, long millisToDelay)
							{
								throw new AssertionError("Not expected in test");
							}
						}
						, new TickProcessingContext.IChangeSink() {
							@Override
							public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
							{
								Assert.assertEquals(0, ProcessingSinks.this.nextTargetEntityId);
								Assert.assertNull(ProcessingSinks.this.nextChange);
								ProcessingSinks.this.nextTargetEntityId = targetEntityId;
								ProcessingSinks.this.nextChange = change;
								return true;
							}
							@Override
							public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
							{
								throw new AssertionError("Not expected in test");
							}
							@Override
							public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
							{
								throw new AssertionError("Not expected in test");
							}
							@Override
							public boolean passive(int targetPassiveId, IPassiveAction action)
							{
								throw new AssertionError("Not expected in test");
							}
						})
					.eventSink(this.events)
					.finish()
			;
		}
	}

	private static class _Events implements TickProcessingContext.IEventSink
	{
		private EventRecord _expected;
		public void expected(EventRecord expected)
		{
			Assert.assertNull(_expected);
			_expected = expected;
		}
		@Override
		public void post(EventRecord event)
		{
			Assert.assertEquals(_expected, event);
			_expected = null;
		}
	}
}
