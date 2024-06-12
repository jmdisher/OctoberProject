package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
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
	public void breakBlockSuccess()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidAddress cuboidAddress = target.getCuboidAddress();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, (short)2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		TickProcessingContext context = new TickProcessingContext(1L
				, (AbsoluteLocation location) -> {
					return cuboidAddress.equals(location.getCuboidAddress())
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
					;
				}
				, null
				, null
				, null
				, null
				, null
				, Difficulty.HOSTILE
		);
		boolean didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(cuboid);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Inventory inv = proxy.getInventory();
		Assert.assertEquals(1, inv.sortedKeys().size());
		Assert.assertEquals(1, inv.getCount(ENV.items.STONE));
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
		Entity entity = MutableEntity.create(clientId).freeze();
		
		// Without a tool, this will take 5 hits.
		MutableBlockProxy proxy = null;
		for (int i = 0; i < 5; ++i)
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
		MutableEntity mutable = MutableEntity.create(clientId);
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
		
		// We should see the applied damage at 5x the time since we were using the pickaxe.
		Assert.assertEquals(STONE, proxy.getBlock());
		Assert.assertEquals((short)50, proxy.getDamage());
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
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(-1, 0, 1).getBlockAddress(), ENV.items.WATER_STRONG.number());
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, 1).getBlockAddress(), ENV.items.WATER_WEAK.number());
		cuboid.setData15(AspectRegistry.BLOCK, down.getBlockAddress(), ENV.items.AIR.number());
		cuboid.setData15(AspectRegistry.BLOCK, downOver.getBlockAddress(), ENV.items.AIR.number());
		TickProcessingContext context = new TickProcessingContext(1L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
				, null
				, null
				, Difficulty.HOSTILE
		);
		
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
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(1L
				, (AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.fail("Not expected in tets");
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						// We should see a delayed growth mutation.
						Assert.assertEquals(10000L, millisToDelay);
						Assert.assertNull(holder[0]);
						holder[0] = mutation;
					}
				}
				, null
				, null
				, null
				, Difficulty.HOSTILE
		);
		boolean didApply = mutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertTrue(holder[0] instanceof MutationBlockGrow);
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
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(ENV.items.CHARCOAL, 1).finish());
		
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(target, wheatSeedling);
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		TickProcessingContext context = new TickProcessingContext(1L
				, (AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.fail("Not expected in tets");
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						// We ignore this.
					}
				}
				, null
				, null
				, null
				, Difficulty.HOSTILE
		);
		Assert.assertTrue(mutation.applyMutation(context, proxy));
		Assert.assertTrue(proxy.didChange());
		Assert.assertEquals(wheatSeedling, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.CHARCOAL));
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
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, source.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(ENV.items.CHARCOAL, 2).finish());
		cuboid.setData15(AspectRegistry.BLOCK, sink.getBlockAddress(), chest.item().number());
		
		IMutationBlock[] outMutation = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(1L
				, (AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(outMutation[0]);
						outMutation[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
				, null
				, null
				, Difficulty.HOSTILE
		);
		
		MutationBlockPushToBlock mutation = new MutationBlockPushToBlock(source, 1, 1, Inventory.INVENTORY_ASPECT_INVENTORY, sink);
		MutableBlockProxy sourceProxy = new MutableBlockProxy(source, cuboid);
		MutableBlockProxy sinkProxy = new MutableBlockProxy(sink, cuboid);
		Assert.assertTrue(mutation.applyMutation(context, sourceProxy));
		Assert.assertTrue(sourceProxy.didChange());
		Assert.assertEquals(1, sourceProxy.getInventory().getCount(ENV.items.CHARCOAL));
		
		Assert.assertTrue(outMutation[0].applyMutation(context, sinkProxy));
		Assert.assertTrue(sinkProxy.didChange());
		Assert.assertEquals(1, sinkProxy.getInventory().getCount(ENV.items.CHARCOAL));
	}


	private static class ProcessingSinks
	{
		public IMutationBlock nextMutation;
		public int nextTargetEntityId;
		public IMutationEntity<IMutablePlayerEntity> nextChange;
		
		public TickProcessingContext createBoundContext(CuboidData cuboid)
		{
			return new TickProcessingContext(1L
					, (AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}
					, null
					, new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							ProcessingSinks.this.nextMutation = mutation;
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not expected in tets");
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
					}
					, null
					, null
					, Difficulty.HOSTILE
			);
		}
	}
}
