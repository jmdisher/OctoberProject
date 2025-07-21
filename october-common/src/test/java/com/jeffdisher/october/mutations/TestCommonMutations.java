package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.Deprecated_EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.actions.MutationEntityStoreToInventory;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
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
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
						return cuboidAddress.equals(location.getCuboidAddress())
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
						;
					}, null)
				.eventSink(events)
				.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, target, 0, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY));
		boolean didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Inventory inv = proxy.getInventory();
		Assert.assertEquals(1, inv.sortedKeys().size());
		Assert.assertEquals(1, inv.getCount(STONE_ITEM));
	}

	@Test
	public void breakBlockFailure()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, (short)1000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		boolean didApply = mutation.applyMutation(null, proxy);
		Assert.assertFalse(didApply);
		Assert.assertFalse(proxy.didChange());
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Inventory inv = proxy.getInventory();
		Assert.assertEquals(0, inv.currentEncumbrance);
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
		// There should be nothing in the block inventory but we should see a change scheduled to store the item into the entity.
		Inventory inv = proxy.getInventory();
		Assert.assertEquals(0, inv.sortedKeys().size());
		Assert.assertEquals(clientId, sinks.nextTargetEntityId);
		Assert.assertTrue(sinks.nextChange instanceof MutationEntityStoreToInventory);
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
		mutable.newInventory.addNonStackableBestEfforts(new NonStackableItem(pickaxe, ENV.durability.getDurability(pickaxe)));
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
		// We will also store inventory in one of these blocks to show that flowing water doesn't break it.
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, downOver.getBlockAddress(), Inventory.start(10).addStackable(CHARCOAL_ITEM, 2).finish());
		
		_Events events = new _Events();
		List<IMutationBlock> out_mutation = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							out_mutation.add(mutation);
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							out_mutation.add(mutation);
						}
					}, null)
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
		Assert.assertEquals(2, out_mutation.size());
		Assert.assertTrue(out_mutation.get(0) instanceof MutationBlockLiquidFlowInto);
		Assert.assertTrue(out_mutation.get(1) instanceof MutationBlockStoreItems);
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
		Assert.assertEquals(2, proxy.getInventory().getCount(CHARCOAL_ITEM));
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
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.fail("Not expected in test");
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
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
	public void overwriteExistingInventory()
	{
		// This is to verify that we don't destroy an existing inventory in this block if we replace it with something which allows entity movement (air-equivalent).
		Block tilled = ENV.blocks.fromItem(ENV.items.getItemById("op.tilled_soil"));
		Block wheatSeedling = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_seedling"));
		int entityId = 1;
		AbsoluteLocation target = new AbsoluteLocation(5, 5, 5);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), tilled.item().number());
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(CHARCOAL_ITEM, 1).finish());
		
		MutationBlockOverwriteByEntity mutation = new MutationBlockOverwriteByEntity(target, wheatSeedling, null, entityId);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.fail("Not expected in tets");
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
						}
					}, null)
				.eventSink(events)
				.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, target, 0, entityId));
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(CHARCOAL_ITEM));
	}

	@Test
	public void replaceSerialization()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		MutationBlockReplace replace = new MutationBlockReplace(target, ENV.special.AIR, WATER_SOURCE);
		ByteBuffer buffer = ByteBuffer.allocate(64);
		replace.serializeToBuffer(buffer);
		buffer.flip();
		MutationBlockReplace test = MutationBlockReplace.deserializeFromBuffer(buffer);
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
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.assertNull(outMutation[0]);
							outMutation[0] = mutation;
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
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
		// We will also place inventory in that block to show that growth doesn't destroy it.
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, BlockAddress.fromInt(1, 1, 1), Inventory.start(10).addStackable(CHARCOAL_ITEM, 2).finish());
		
		// First, we want to make sure that the wheat fails to grow due to darkness.
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}, null)
				.skyLight((AbsoluteLocation blockLocation) -> (byte)0)
				.sinks(new TickProcessingContext.IMutationSink() {
							@Override
							public void next(IMutationBlock mutation)
							{
								Assert.fail("Not expected in test");
							}
							@Override
							public void future(IMutationBlock mutation, long millisToDelay)
							{
								Assert.fail("Not used in test");
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
		Assert.assertEquals(2, proxy.getInventory().getCount(CHARCOAL_ITEM));
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
					}, null)
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
					}, null)
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
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							holder[0] = (MutationBlockFurnaceCraft) mutation;
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
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
				}, null)
				.eventSink(new _Events())
				.finish();
		
		TickUtils.endOfTick(context, entity);
		Assert.assertEquals((byte)1, entity.getBreath());
		Assert.assertEquals((byte)(2 * MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND), entity.getHealth());
		
		// Now, do one at the end of the second to see the breath run out.
		@SuppressWarnings("unchecked")
		Deprecated_EntityChangeTakeDamageFromOther<IMutablePlayerEntity>[] holder = new Deprecated_EntityChangeTakeDamageFromOther[1];
		_Events events = new _Events();
		context = ContextBuilder.build()
				.tick(50L)
				.millisPerTick(20L)
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null)
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
					{
						Assert.assertNull(holder[0]);
						holder[0] = (Deprecated_EntityChangeTakeDamageFromOther<IMutablePlayerEntity>) change;
					}
					@Override
					public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
					{
						Assert.fail();
					}
					@Override
					public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
					{
						Assert.fail();
					}
				})
				.eventSink(events)
				.finish();
		
		TickUtils.endOfTick(context, entity);
		Assert.assertEquals((byte)0, entity.getBreath());
		Assert.assertEquals((byte)(2 * MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND), entity.getHealth());
		Assert.assertNull(holder[0]);
		
		// Run again to show the damage taken.
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.SUFFOCATION, entity.newLocation.getBlockLocation(), entity.getId(), 0));
		TickUtils.endOfTick(context, entity);
		Assert.assertEquals((byte)0, entity.getBreath());
		Assert.assertEquals(MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND, entity.getHealth());
		Assert.assertNull(holder[0]);
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
	public void inventoryMovesOnOverwrite()
	{
		// This is to demonstrate that the inventory in an empty block will move to the block above (on the next tick) when a solid block is written in place.
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		int entityId = 1;
		AbsoluteLocation target = new AbsoluteLocation(5, 5, 5);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), dirt.item().number());
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(CHARCOAL_ITEM, 1).finish());
		
		MutationBlockOverwriteByEntity mutation = new MutationBlockOverwriteByEntity(target, dirt, null, entityId);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		_Events events = new _Events();
		IMutationBlock[] out_mutation = new IMutationBlock[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.assertNull(out_mutation[0]);
							out_mutation[0] = mutation;
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
						}
					}, null)
				.eventSink(events)
				.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, target, 0, entityId));
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(dirt, proxy.getBlock());
		Assert.assertNull(proxy.getInventory());
		proxy.writeBack(cuboid);
		
		AbsoluteLocation above = target.getRelative(0, 0, 1);
		MutationBlockStoreItems followUp = (MutationBlockStoreItems)out_mutation[0];
		out_mutation[0] = null;
		Assert.assertEquals(above, followUp.getAbsoluteLocation());
		proxy = new MutableBlockProxy(above, cuboid);
		Assert.assertTrue(followUp.applyMutation(context, proxy));
		Assert.assertNull(out_mutation[0]);
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(CHARCOAL_ITEM));
	}

	@Test
	public void treeGrowthOverInventory()
	{
		// Show that tree growth will force its inventory to the north.
		AbsoluteLocation target = new AbsoluteLocation(5, 5, 5);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		Block sapling = ENV.blocks.fromItem(ENV.items.getItemById("op.sapling"));
		Block log = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), dirt.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 1, -1).getBlockAddress(), dirt.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), sapling.item().number());
		// We will also place inventory in that block to show that growth doesn't destroy it.
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress(), Inventory.start(10).addStackable(CHARCOAL_ITEM, 2).finish());
		
		IMutationBlock[] out_mutation = new IMutationBlock[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}, null)
				.skyLight((AbsoluteLocation blockLocation) -> PlantHelpers.MIN_LIGHT)
				.sinks(new TickProcessingContext.IMutationSink() {
							@Override
							public void next(IMutationBlock mutation)
							{
								if (mutation instanceof MutationBlockOverwriteInternal)
								{
									// Note that we will see lots of over-write calls for the rest of the tree but ignore them.
								}
								else
								{
									Assert.assertNull(out_mutation[0]);
									out_mutation[0] = mutation;
								}
							}
							@Override
							public void future(IMutationBlock mutation, long millisToDelay)
							{
								Assert.fail("Not used in test");
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
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(log, proxy.getBlock());
		proxy.writeBack(cuboid);
		
		AbsoluteLocation north = target.getRelative(0, 1, 0);
		MutationBlockStoreItems followUp = (MutationBlockStoreItems)out_mutation[0];
		out_mutation[0] = null;
		Assert.assertEquals(north, followUp.getAbsoluteLocation());
		proxy = new MutableBlockProxy(north, cuboid);
		Assert.assertTrue(followUp.applyMutation(context, proxy));
		Assert.assertNull(out_mutation[0]);
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().getCount(CHARCOAL_ITEM));
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
					}, null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.fail("Not in test");
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.assertNotNull(mutation);
						Assert.assertTrue(millisToDelay > 0L);
						Assert.assertNull(out_mutation[0]);
						out_mutation[0] = mutation;
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
	public void lavaFlowDestroysItems()
	{
		// We want to verify what happens in a situation where we expect the water to flow into some gaps after breaking a block.
		Block lavaSource = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.lava_source"));
		Block lavaStrong = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.lava_strong"));
		AbsoluteLocation source = new AbsoluteLocation(15, 15, 15);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(source.getCuboidAddress(), STONE);
		AbsoluteLocation items = source.getRelative(1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, source.getBlockAddress(), lavaSource.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, items.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, items.getBlockAddress(), Inventory.start(10).addStackable(CHARCOAL_ITEM, 2).finish());
		
		_Events events = new _Events();
		List<IMutationBlock> out_mutation = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.fail();
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							out_mutation.add(mutation);
						}
					}, null)
				.eventSink(events)
				.finish()
		;
		
		MutableBlockProxy proxy = new MutableBlockProxy(items, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(items).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().getCount(CHARCOAL_ITEM));
		Assert.assertEquals(1, out_mutation.size());
		Assert.assertTrue(out_mutation.get(0) instanceof MutationBlockLiquidFlowInto);
		MutationBlockLiquidFlowInto internal = (MutationBlockLiquidFlowInto)out_mutation.get(0);
		out_mutation.clear();
		Assert.assertTrue(internal.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(lavaStrong, proxy.getBlock());
		Assert.assertEquals(0, proxy.getInventory().getCount(CHARCOAL_ITEM));
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
		TickProcessingContext context = ContextBuilder.build()
				.fixedRandom(0)
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.fail();
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.assertNull(out_mutation[0]);
							out_mutation[0] = mutation;
						}
					}, null)
				.eventSink(events)
				.finish()
		;
		
		// We should see the block between the water and lava replaced by water, thus destroying the block inventory.
		MutableBlockProxy proxy1 = new MutableBlockProxy(wheat1, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(wheat1).applyMutation(context, proxy1));
		proxy1.writeBack(cuboid);
		Assert.assertEquals(wheatMatureBlock, proxy1.getBlock());
		MutationBlockLiquidFlowInto internal = (MutationBlockLiquidFlowInto)out_mutation[0];
		Assert.assertTrue(internal instanceof MutationBlockLiquidFlowInto);
		out_mutation[0] = null;
		Assert.assertTrue(internal.applyMutation(context, proxy1));
		proxy1.writeBack(cuboid);
		Assert.assertNull(out_mutation[0]);
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
		Assert.assertTrue(internal.applyMutation(context, proxy2));
		proxy2.writeBack(cuboid);
		Assert.assertNull(out_mutation[0]);
		Assert.assertEquals(6, proxy2.getInventory().currentEncumbrance);
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
				}, null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.fail("Not used");
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
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
				}, null)
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
	public void burningDestroysItems()
	{
		// We want to verify that items are destroyed when dropped on a burning block or when a block starts on fire but not if in a chest.
		Item logItem = ENV.items.getItemById("op.log");
		Block log = ENV.blocks.getAsPlaceableBlock(logItem);
		Block chest = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.chest"));
		AbsoluteLocation centre = new AbsoluteLocation(15, 15, 15);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(centre.getCuboidAddress(), ENV.special.AIR);
		// 1) A block which starts on fire and we drop items on top.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), log.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, centre.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		// 2) A block with items on it which we then start on fire.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(1, 0, 0).getBlockAddress(), log.item().number());
		// 3) A block which starts on fire and we put items in a chest on top.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(2, 0, 0).getBlockAddress(), log.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, centre.getRelative(2, 0, 0).getBlockAddress(), FlagsAspect.FLAG_BURNING);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(2, 0, 1).getBlockAddress(), chest.item().number());
		// 4) A block with items in a chest on top which we then start on fire.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(3, 0, 0).getBlockAddress(), log.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(3, 0, 1).getBlockAddress(), chest.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.finish()
		;
		
		// Create the mutations to fill the inventories.
		MutableBlockProxy proxy = new MutableBlockProxy(centre.getRelative(0, 0, 1), cuboid);
		Assert.assertTrue(new MutationBlockStoreItems(centre.getRelative(0, 0, 1), new Items(logItem, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		proxy = new MutableBlockProxy(centre.getRelative(1, 0, 1), cuboid);
		Assert.assertTrue(new MutationBlockStoreItems(centre.getRelative(1, 0, 1), new Items(logItem, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		proxy = new MutableBlockProxy(centre.getRelative(2, 0, 1), cuboid);
		Assert.assertTrue(new MutationBlockStoreItems(centre.getRelative(2, 0, 1), new Items(logItem, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		proxy = new MutableBlockProxy(centre.getRelative(3, 0, 1), cuboid);
		Assert.assertTrue(new MutationBlockStoreItems(centre.getRelative(3, 0, 1), new Items(logItem, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Now, ignite the other blocks, verify that these changes should cause updates.
		context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.fail("Not in test");
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						// We only expect these to be to schedule the fire spreading or burning down.
						Assert.assertTrue((mutation instanceof MutationBlockStartFire) || (mutation instanceof MutationBlockBurnDown));
					}
				}, null)
				.finish()
		;
		proxy = new MutableBlockProxy(centre.getRelative(1, 0, 0), cuboid);
		Assert.assertTrue(new MutationBlockStartFire(centre.getRelative(1, 0, 0)).applyMutation(context, proxy));
		Assert.assertTrue(proxy.shouldTriggerUpdateEvent());
		proxy.writeBack(cuboid);
		proxy = new MutableBlockProxy(centre.getRelative(3, 0, 0), cuboid);
		Assert.assertTrue(new MutationBlockStartFire(centre.getRelative(3, 0, 0)).applyMutation(context, proxy));
		Assert.assertTrue(proxy.shouldTriggerUpdateEvent());
		proxy.writeBack(cuboid);
		
		// Now run the updates and show that they destroy the items, unless in a chest.
		proxy = new MutableBlockProxy(centre.getRelative(1, 0, 1), cuboid);
		Assert.assertTrue(new MutationBlockUpdate(centre.getRelative(1, 0, 1)).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		proxy = new MutableBlockProxy(centre.getRelative(3, 0, 1), cuboid);
		Assert.assertFalse(new MutationBlockUpdate(centre.getRelative(3, 0, 1)).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Verify that only the chest inventories survived.
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, centre.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, centre.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertNotNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, centre.getRelative(2, 0, 1).getBlockAddress()));
		Assert.assertNotNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, centre.getRelative(3, 0, 1).getBlockAddress()));
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
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						out_mutations.add(mutation);
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail();
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
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.fail();
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						// These are only for this one purpose.
						Assert.assertEquals(MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS, millisToDelay);
						Assert.assertNull(out_mutations[0]);
						out_mutations[0] = (MutationBlockGrowGroundCover) mutation;
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
	public void dropItemsInLava()
	{
		// We want to verify that items are destroyed when put in a lava block or when they fall into a lava block.
		Block lava = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.lava_source"));
		AbsoluteLocation centre = new AbsoluteLocation(15, 15, 15);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(centre.getCuboidAddress(), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), lava.item().number());
		
		IMutationBlock[] out = new IMutationBlock[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(out[0]);
						out[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not in test");
					}
				}, null)
				.finish()
		;
		
		// Create the mutations to fill the inventory of the lava block and the block above it.
		MutableBlockProxy proxy = new MutableBlockProxy(centre, cuboid);
		Assert.assertTrue(new MutationBlockStoreItems(centre, new Items(STONE_ITEM, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertNull(out[0]);
		proxy = new MutableBlockProxy(centre.getRelative(0, 0, 1), cuboid);
		Assert.assertTrue(new MutationBlockStoreItems(centre.getRelative(0, 0, 1), new Items(STONE_ITEM, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertNotNull(out[0]);
		
		IMutationBlock followUp = out[0];
		out[0] = null;
		proxy = new MutableBlockProxy(followUp.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(followUp.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertNull(out[0]);
		
		// Verify that only the chest inventories survived.
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, centre.getBlockAddress()));
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, centre.getRelative(0, 0, 1).getBlockAddress()));
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
					}, null)
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
						}, null)
					.sinks(new TickProcessingContext.IMutationSink() {
							@Override
							public void next(IMutationBlock mutation)
							{
								ProcessingSinks.this.nextMutation = mutation;
							}
							@Override
							public void future(IMutationBlock mutation, long millisToDelay)
							{
								Assert.fail("Not used in test");
							}
						}
						, new TickProcessingContext.IChangeSink() {
							@Override
							public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
							{
								Assert.assertEquals(0, ProcessingSinks.this.nextTargetEntityId);
								Assert.assertNull(ProcessingSinks.this.nextChange);
								ProcessingSinks.this.nextTargetEntityId = targetEntityId;
								ProcessingSinks.this.nextChange = change;
							}
							@Override
							public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
							{
								Assert.fail("Not expected in tets");
							}
							@Override
							public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
							{
								Assert.fail("Not expected in tets");
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
