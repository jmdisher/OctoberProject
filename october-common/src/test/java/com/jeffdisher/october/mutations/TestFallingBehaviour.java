package com.jeffdisher.october.mutations;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.subactions.MutationEntityPushItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * Note that this test suite is being removed as it tests empty inventory falling behaviours but empty inventories are
 * being removed in favour of ItemSlot PassiveEntity instances.
 */
public class TestFallingBehaviour
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
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


	private static TickProcessingContext _createTestContext(CuboidData cuboid
			, IMutationBlock[] blockHolder
			, AbsoluteLocation... blockLocation
	)
	{
		int[] index = new int[] {0};
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
					, null
					, null
				)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public boolean next(IMutationBlock mutation)
						{
							int thisIndex = 0;
							while (null != blockHolder[thisIndex])
							{
								thisIndex += 1;
							}
							Assert.assertNull(blockHolder[thisIndex]);
							blockHolder[thisIndex] = mutation;
							return true;
						}
						@Override
						public boolean future(IMutationBlock mutation, long millisToDelay)
						{
							int thisIndex = 0;
							while (null != blockHolder[thisIndex])
							{
								thisIndex += 1;
							}
							Assert.assertNull(blockHolder[thisIndex]);
							blockHolder[thisIndex] = mutation;
							return true;
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
