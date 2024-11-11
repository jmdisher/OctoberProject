package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


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
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		CHARCOAL_ITEM = ENV.items.getItemById("op.charcoal");
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
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
						return cuboidAddress.equals(location.getCuboidAddress())
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
						;
					}, null)
				.finish()
		;
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
		EntityChangeIncrementalBlockBreak longRunningChange = new EntityChangeIncrementalBlockBreak(target, (short)200);
		
		// We will need an entity so that phase1 can ask to schedule the follow-up against it.
		int clientId = 1;
		Entity entity = MutableEntity.createForTest(clientId).freeze();
		
		// Without a tool, this will take 10 hits.
		MutableBlockProxy proxy = null;
		for (int i = 0; i < 10; ++i)
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
				Assert.assertEquals((short)200, proxy.getDamage());
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
		EntityChangeIncrementalBlockBreak longRunningChange = new EntityChangeIncrementalBlockBreak(target, (short)10);
		
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
		Assert.assertEquals((short)100, proxy.getDamage());
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
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(-1, 0, 1).getBlockAddress(), ENV.special.WATER_STRONG.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, 1).getBlockAddress(), ENV.special.WATER_WEAK.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, down.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, downOver.getBlockAddress(), ENV.special.AIR.item().number());
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
						}
					}, null)
				.finish()
		;
		
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, (short)2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		// If we break a block under weak flow, we expect it to be weak unless it lands on a solid block.
		Assert.assertEquals(ENV.special.WATER_WEAK, proxy.getBlock());
		
		// Run an update on the other blocks below to verify it flows through them, creating strong flow when it touches the solid block.
		proxy = new MutableBlockProxy(down, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(down).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.WATER_STRONG, proxy.getBlock());
		
		proxy = new MutableBlockProxy(downOver, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(downOver).applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.WATER_WEAK, proxy.getBlock());
	}

	@Test
	public void overwriteBoundaryCheckSupport()
	{
		// This is verify a bug fix when planting seeds at the bottom of a cuboid, when the below is not loaded, would NPE.
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		
		Block wheatSeedling = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_seedling"));
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(target, wheatSeedling);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.fail("Not expected in test");
						}
					}, null)
				.finish()
		;
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
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		Block wheatSeedling = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_seedling"));
		AbsoluteLocation target = new AbsoluteLocation(5, 5, 5);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), dirt.item().number());
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(CHARCOAL_ITEM, 1).finish());
		
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(target, wheatSeedling);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.fail("Not expected in tets");
						}
					}, null)
				.finish()
		;
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(CHARCOAL_ITEM));
	}

	@Test
	public void replaceSerialization()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		MutationBlockReplace replace = new MutationBlockReplace(target, ENV.special.AIR, ENV.special.WATER_SOURCE);
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
		Block switchOn = ENV.blocks.fromItem(ENV.items.getItemById("op.switch_on"));
		Block lampOff = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp_off"));
		Block lampOn = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp_on"));
		AbsoluteLocation switchLocation = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation lampLocation = switchLocation.getRelative(1, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(switchLocation.getCuboidAddress(), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress(), switchOn.item().number());
		cuboid.setData7(AspectRegistry.LOGIC, switchLocation.getBlockAddress(), LogicAspect.MAX_LEVEL);
		
		ProcessingSinks sinks = new ProcessingSinks();
		TickProcessingContext context = sinks.createBoundContext(cuboid);
		
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(lampLocation, lampOff);
		MutableBlockProxy proxy = new MutableBlockProxy(lampLocation, cuboid);
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(lampOn, proxy.getBlock());
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
					}, null)
				.skyLight((AbsoluteLocation blockLocation) -> (byte)0)
				.sinks(new TickProcessingContext.IMutationSink() {
							@Override
							public void next(IMutationBlock mutation)
							{
								Assert.fail("Not expected in test");
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
		Block hopper = ENV.blocks.fromItem(ENV.items.getItemById("op.hopper_down"));
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 0), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), hopper.item().number());
		
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.WATER_SOURCE);
		MutableEntity entity = MutableEntity.createForTest(1);
		entity.setBreath((byte)1);
		entity.setHealth((byte)(2 * EntityConstants.SUFFOCATION_DAMAGE_PER_SECOND));
		
		// Run a tick which isn't end of the second - should change nothing.
		TickProcessingContext context = ContextBuilder.build()
				.tick(1L)
				.millisPerTick(20L)
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null)
				.finish();
		
		TickUtils.endOfTick(context, entity);
		Assert.assertEquals((byte)1, entity.getBreath());
		Assert.assertEquals((byte)(2 * EntityConstants.SUFFOCATION_DAMAGE_PER_SECOND), entity.getHealth());
		
		// Now, do one at the end of the second to see the breath run out.
		@SuppressWarnings("unchecked")
		EntityChangeTakeDamage<IMutablePlayerEntity>[] holder = new EntityChangeTakeDamage[1];
		context = ContextBuilder.build()
				.tick(50L)
				.millisPerTick(20L)
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null)
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
					{
						Assert.assertNull(holder[0]);
						holder[0] = (EntityChangeTakeDamage<IMutablePlayerEntity>) change;
					}
					@Override
					public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
					{
						Assert.fail();
					}
					@Override
					public void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change)
					{
						Assert.fail();
					}
				})
				.finish();
		
		TickUtils.endOfTick(context, entity);
		Assert.assertEquals((byte)0, entity.getBreath());
		Assert.assertEquals((byte)(2 * EntityConstants.SUFFOCATION_DAMAGE_PER_SECOND), entity.getHealth());
		Assert.assertNull(holder[0]);
		
		// Run again to show the damage taken.
		TickUtils.endOfTick(context, entity);
		Assert.assertEquals((byte)0, entity.getBreath());
		Assert.assertEquals((byte)(2 * EntityConstants.SUFFOCATION_DAMAGE_PER_SECOND), entity.getHealth());
		Assert.assertNotNull(holder[0]);
		
		// Run this mutation.
		holder[0].applyChange(context, entity);
		Assert.assertEquals((byte)0, entity.getBreath());
		Assert.assertEquals(EntityConstants.SUFFOCATION_DAMAGE_PER_SECOND, entity.getHealth());
	}


	private static class ProcessingSinks
	{
		public IMutationBlock nextMutation;
		public int nextTargetEntityId;
		public IMutationEntity<IMutablePlayerEntity> nextChange;
		
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
						}
						, new TickProcessingContext.IChangeSink() {
							@Override
							public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
							{
								Assert.assertEquals(0, ProcessingSinks.this.nextTargetEntityId);
								Assert.assertNull(ProcessingSinks.this.nextChange);
								ProcessingSinks.this.nextTargetEntityId = targetEntityId;
								ProcessingSinks.this.nextChange = change;
							}
							@Override
							public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
							{
								Assert.fail("Not expected in tets");
							}
							@Override
							public void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change)
							{
								Assert.fail("Not expected in tets");
							}
						})
					.finish()
			;
		}
	}
}
