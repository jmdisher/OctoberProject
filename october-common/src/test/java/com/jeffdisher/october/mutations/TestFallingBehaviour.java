package com.jeffdisher.october.mutations;

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
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestFallingBehaviour
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void dropItemsFalling() throws Throwable
	{
		// Create an air cuboid and an entity with some items, then try to drop them onto a block and observe that they fall through.
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 3.0f);
		newEntity.newInventory.addAllItems(STONE_ITEM, 2);
		newEntity.setSelectedKey(newEntity.newInventory.getIdOfStackableType(STONE_ITEM));
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 3);
		// Create a solid block a little below this so we can watch it fall down.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -3).getBlockAddress(), STONE_ITEM.number());
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = _createTestContext(cuboid, blockHolder, targetLocation);
		
		// This is a multi-step process which starts by asking the entity to start the drop.
		MutationEntityPushItems push = new MutationEntityPushItems(targetLocation, newEntity.newInventory.getIdOfStackableType(STONE_ITEM), 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(context, newEntity));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		MutationBlockStoreItems extracted = (MutationBlockStoreItems) blockHolder[0];
		blockHolder[0] = null;
		AbsoluteLocation location = extracted.getAbsoluteLocation();
		Assert.assertEquals(targetLocation, location);
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(extracted.applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		
		// We expect to see another falling mutation since this hasn't yet hit the ground.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		extracted = (MutationBlockStoreItems) blockHolder[0];
		blockHolder[0] = null;
		
		// Run this mutation and verify that we see it still falling since there is another block to fall.
		location = extracted.getAbsoluteLocation();
		Assert.assertEquals(targetLocation.getRelative(0, 0, -1), location);
		newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(extracted.applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		extracted = (MutationBlockStoreItems) blockHolder[0];
		blockHolder[0] = null;
		
		// Run this mutation and verify that the inventory has settled on the block below.
		location = extracted.getAbsoluteLocation();
		Assert.assertEquals(targetLocation.getRelative(0, 0, -2), location);
		newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(extracted.applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		
		// By this point, we should be able to verify that the items have settled.
		Assert.assertNull(blockHolder[0]);
		AbsoluteLocation finalLocation = targetLocation.getRelative(0, 0, -2);
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, finalLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(STONE_ITEM));
	}

	@Test
	public void breakBottomFirst() throws Throwable
	{
		// Verify that breaking the bottom block first results in the top inventory falling.
		// We will just generate a solid stone cuboid so we don't worry about things falling due to empty space.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		AbsoluteLocation bottomLocation = new AbsoluteLocation(0, 0, 3);
		AbsoluteLocation topLocation = bottomLocation.getRelative(0, 0, 1);
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = _createTestContext(cuboid, blockHolder, bottomLocation, topLocation);
		
		// Break the bottom block.
		MutableBlockProxy bottomBlock = new MutableBlockProxy(bottomLocation, cuboid);
		MutationBlockIncrementalBreak breaking = new MutationBlockIncrementalBreak(bottomLocation, (short) 2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		Assert.assertTrue(breaking.applyMutation(context, bottomBlock));
		bottomBlock.writeBack(cuboid);
		
		// We should see nothing scheduled and the inventory on the ground.
		Assert.assertNull(blockHolder[0]);
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, bottomLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(STONE_ITEM));
		
		// Now, break the top block.
		MutableBlockProxy topBlock = new MutableBlockProxy(topLocation, cuboid);
		breaking = new MutationBlockIncrementalBreak(topLocation, (short) 2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		Assert.assertTrue(breaking.applyMutation(context, topBlock));
		topBlock.writeBack(cuboid);
		
		// We should see the next push scheduled so run it and verify the inventory.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		MutationBlockStoreItems extracted = (MutationBlockStoreItems) blockHolder[0];
		blockHolder[0] = null;
		Assert.assertEquals(bottomLocation, extracted.getAbsoluteLocation());
		bottomBlock = new MutableBlockProxy(bottomLocation, cuboid);
		Assert.assertTrue(extracted.applyMutation(context, bottomBlock));
		bottomBlock.writeBack(cuboid);
		
		Assert.assertNull(blockHolder[0]);
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, bottomLocation.getBlockAddress());
		Assert.assertEquals(2, blockInventory.getCount(STONE_ITEM));
	}

	@Test
	public void breakTopFirst() throws Throwable
	{
		// Verify that breaking the top block first results in it still floating after breaking the bottom.
		// (this requires generalized block update logic).
		// We will just generate a solid stone cuboid so we don't worry about things falling due to empty space.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		AbsoluteLocation topLocation = new AbsoluteLocation(0, 0, 4);
		AbsoluteLocation bottomLocation = topLocation.getRelative(0, 0, -1);
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = _createTestContext(cuboid, blockHolder, topLocation, bottomLocation);
		
		// Break the top block.
		MutableBlockProxy topBlock = new MutableBlockProxy(topLocation, cuboid);
		MutationBlockIncrementalBreak breaking = new MutationBlockIncrementalBreak(topLocation, (short) 2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		Assert.assertTrue(breaking.applyMutation(context, topBlock));
		topBlock.writeBack(cuboid);
		
		// We should see nothing scheduled and the inventory on the ground.
		Assert.assertNull(blockHolder[0]);
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, topLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(STONE_ITEM));
		
		// Now, break the bottom block.
		MutableBlockProxy bottomBlock = new MutableBlockProxy(bottomLocation, cuboid);
		breaking = new MutationBlockIncrementalBreak(bottomLocation, (short) 2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		Assert.assertTrue(breaking.applyMutation(context, bottomBlock));
		bottomBlock.writeBack(cuboid);
		
		// We should see nothing scheduled and the inventory on the ground.
		Assert.assertNull(blockHolder[0]);
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, bottomLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(STONE_ITEM));
		
		// Now, we will synthesize the update event which would normally be scheduled against this and see it fall.
		topBlock = new MutableBlockProxy(topLocation, cuboid);
		MutationBlockUpdate update = new MutationBlockUpdate(topLocation);
		Assert.assertTrue(update.applyMutation(context, topBlock));
		topBlock.writeBack(cuboid);
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, topLocation.getBlockAddress()));
		
		// We should see this schedule the block move.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		MutationBlockStoreItems extracted = (MutationBlockStoreItems) blockHolder[0];
		blockHolder[0] = null;
		Assert.assertEquals(bottomLocation, extracted.getAbsoluteLocation());
		bottomBlock = new MutableBlockProxy(bottomLocation, cuboid);
		Assert.assertTrue(extracted.applyMutation(context, bottomBlock));
		bottomBlock.writeBack(cuboid);
		
		Assert.assertNull(blockHolder[0]);
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, bottomLocation.getBlockAddress());
		Assert.assertEquals(2, blockInventory.getCount(STONE_ITEM));
	}

	@Test
	public void bottomOfCuboid() throws Throwable
	{
		// Create an air cuboid with some items in the bottom block, then load another air cuboid and verify that they fall.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(STONE_ITEM, 2).finish());
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = _createTestContext(cuboid, blockHolder);
		
		// Send an update event and verify that nothing happens.
		MutationBlockUpdate update = new MutationBlockUpdate(targetLocation);
		MutableBlockProxy proxy = new MutableBlockProxy(targetLocation, cuboid);
		Assert.assertFalse(update.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertNull(blockHolder[0]);
		Assert.assertEquals(2, cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()).getCount(STONE_ITEM));
		
		// Use a new context with a new cuboid to show that the items fall if updated now.
		CuboidAddress cuboidAddress1 = CuboidAddress.fromInt(0, 0, -1);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboidAddress.equals(location.getCuboidAddress())
						? new BlockProxy(location.getBlockAddress(), cuboid)
						: cuboidAddress1.equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid1) : null
					, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.assertNull(blockHolder[0]);
							blockHolder[0] = mutation;
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
						}
					}, null)
				.finish()
		;
		proxy = new MutableBlockProxy(targetLocation, cuboid);
		Assert.assertTrue(update.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()));
	}

	@Test
	public void flowingWaterAfterBreak() throws Throwable
	{
		// Create a cuboid of stone with water sources around one block, air and inventory above it and below, then observe what happens when the block is broken.
		short waterSourceNumber = ENV.items.getItemById("op.water_source").number();
		short waterStrongNumber = ENV.items.getItemById("op.water_strong").number();
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		AbsoluteLocation targetLocation = new AbsoluteLocation(15, 15, 15);
		AbsoluteLocation eastLocation = targetLocation.getRelative(1, 0, 0);
		AbsoluteLocation westLocation = targetLocation.getRelative(-1, 0, 0);
		AbsoluteLocation northLocation = targetLocation.getRelative(0, 1, 0);
		AbsoluteLocation southLocation = targetLocation.getRelative(0, -1, 0);
		AbsoluteLocation upLocation = targetLocation.getRelative(0, 0, 1);
		AbsoluteLocation downLocation = targetLocation.getRelative(0, 0, -1);
		cuboid.setData15(AspectRegistry.BLOCK, eastLocation.getBlockAddress(), waterSourceNumber);
		cuboid.setData15(AspectRegistry.BLOCK, westLocation.getBlockAddress(), waterSourceNumber);
		cuboid.setData15(AspectRegistry.BLOCK, upLocation.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, downLocation.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, upLocation.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(STONE_ITEM, 2).finish());
		IMutationBlock[] blockHolder = new IMutationBlock[2];
		TickProcessingContext context = _createTestContext(cuboid, blockHolder, targetLocation);
		
		// Break the target block and observe the change.
		MutableBlockProxy targetBlock = new MutableBlockProxy(targetLocation, cuboid);
		MutationBlockIncrementalBreak breaking = new MutationBlockIncrementalBreak(targetLocation, (short) 2000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		Assert.assertTrue(breaking.applyMutation(context, targetBlock));
		targetBlock.writeBack(cuboid);
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockLiquidFlowInto);
		Assert.assertTrue(blockHolder[1] instanceof MutationBlockStoreItems);
		
		// This will start as air but let the liquid flow in.
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
		MutationBlockLiquidFlowInto flow = (MutationBlockLiquidFlowInto) blockHolder[0];
		Assert.assertTrue(flow.applyMutation(context, targetBlock));
		targetBlock.writeBack(cuboid);
		Assert.assertEquals(waterSourceNumber, cuboid.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
		
		// Run this store operation.
		MutationBlockStoreItems store = (MutationBlockStoreItems) blockHolder[1];
		Assert.assertEquals(downLocation, store.getAbsoluteLocation());
		blockHolder[0] = null;
		blockHolder[1] = null;
		MutableBlockProxy downBlock = new MutableBlockProxy(downLocation, cuboid);
		Assert.assertTrue(store.applyMutation(context, downBlock));
		downBlock.writeBack(cuboid);
		Assert.assertNull(blockHolder[0]);
		
		// Apply the block updates and see the changes in block types and the enqueue of the new store for falling items.
		downBlock = new MutableBlockProxy(downLocation, cuboid);
		Assert.assertTrue(new MutationBlockUpdate(downLocation).applyMutation(context, downBlock));
		downBlock.writeBack(cuboid);
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockLiquidFlowInto);
		Assert.assertNull(blockHolder[1]);
		
		// This will start as air but let the liquid flow in.
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, downLocation.getBlockAddress()));
		flow = (MutationBlockLiquidFlowInto) blockHolder[0];
		blockHolder[0] = null;
		Assert.assertTrue(flow.applyMutation(context, downBlock));
		downBlock.writeBack(cuboid);
		Assert.assertNull(blockHolder[0]);
		Assert.assertEquals(waterStrongNumber, cuboid.getData15(AspectRegistry.BLOCK, downLocation.getBlockAddress()));
		
		Assert.assertFalse(new MutationBlockUpdate(eastLocation).applyMutation(context, new MutableBlockProxy(eastLocation, cuboid)));
		Assert.assertFalse(new MutationBlockUpdate(westLocation).applyMutation(context, new MutableBlockProxy(westLocation, cuboid)));
		Assert.assertFalse(new MutationBlockUpdate(northLocation).applyMutation(context, new MutableBlockProxy(northLocation, cuboid)));
		Assert.assertFalse(new MutationBlockUpdate(southLocation).applyMutation(context, new MutableBlockProxy(southLocation, cuboid)));
		Assert.assertTrue(new MutationBlockUpdate(upLocation).applyMutation(context, new MutableBlockProxy(upLocation, cuboid)));
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
	}


	private static TickProcessingContext _createTestContext(CuboidData cuboid
			, IMutationBlock[] blockHolder
			, AbsoluteLocation... blockLocation
	)
	{
		int[] index = new int[] {0};
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							int thisIndex = 0;
							while (null != blockHolder[thisIndex])
							{
								thisIndex += 1;
							}
							Assert.assertNull(blockHolder[thisIndex]);
							blockHolder[thisIndex] = mutation;
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							int thisIndex = 0;
							while (null != blockHolder[thisIndex])
							{
								thisIndex += 1;
							}
							Assert.assertNull(blockHolder[thisIndex]);
							blockHolder[thisIndex] = mutation;
						}
					}, null)
				.eventSink((EventRecord event) -> {
					// Note that entity 0 doesn't really make sense, in practice, but we use it for this test to avoid picking up the blocks and the event is still passed through.
					EventRecord expected = new EventRecord(EventRecord.Type.BLOCK_BROKEN
							, EventRecord.Cause.NONE
							, blockLocation[index[0]]
							, 0
							, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY
					);
					Assert.assertEquals(expected, event);
					index[0] += 1;
				})
				.finish()
		;
		return context;
	}
}
