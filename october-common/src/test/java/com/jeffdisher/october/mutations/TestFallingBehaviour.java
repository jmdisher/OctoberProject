package com.jeffdisher.october.mutations;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestFallingBehaviour
{
	@Test
	public void dropItemsFalling() throws Throwable
	{
		// Create an air cuboid and an entity with some items, then try to drop them onto a block and observe that they fall through.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		int entityId = 1;
		Entity original = new Entity(entityId, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).add(ItemRegistry.STONE, 2).finish(), ItemRegistry.STONE, null);
		CuboidAddress cuboidAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 3);
		// Create a solid block a little below this so we can watch it fall down.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -3).getBlockAddress(), ItemRegistry.STONE.number());
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> cuboidAddress.equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, (IMutationBlock newMutation) -> {
					Assert.assertNull(blockHolder[0]);
					blockHolder[0] = newMutation;
				}
				, null
		);
		
		// This is a multi-step process which starts by asking the entity to start the drop.
		MutableEntity newEntity = new MutableEntity(original);
		MutationEntityPushItems push = new MutationEntityPushItems(targetLocation, new Items(ItemRegistry.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
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
		Assert.assertEquals(1, blockInventory.items.get(ItemRegistry.STONE).count());
	}

	@Test
	public void breakBottomFirst() throws Throwable
	{
		// Verify that breaking the bottom block first results in the top inventory falling.
		// We will just generate a solid stone cuboid so we don't worry about things falling due to empty space.
		CuboidAddress cuboidAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.STONE);
		AbsoluteLocation bottomLocation = new AbsoluteLocation(0, 0, 3);
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> cuboidAddress.equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, (IMutationBlock newMutation) -> {
					Assert.assertNull(blockHolder[0]);
					blockHolder[0] = newMutation;
				}
				, null
		);
		
		// Break the bottom block.
		MutableBlockProxy bottomBlock = new MutableBlockProxy(bottomLocation, cuboid);
		MutationBlockIncrementalBreak breaking = new MutationBlockIncrementalBreak(bottomLocation, (short) 2000);
		Assert.assertTrue(breaking.applyMutation(context, bottomBlock));
		bottomBlock.writeBack(cuboid);
		
		// We should see nothing scheduled and the inventory on the ground.
		Assert.assertNull(blockHolder[0]);
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, bottomLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.items.get(ItemRegistry.STONE).count());
		
		// Now, break the top block.
		AbsoluteLocation topLocation = bottomLocation.getRelative(0, 0, 1);
		MutableBlockProxy topBlock = new MutableBlockProxy(topLocation, cuboid);
		breaking = new MutationBlockIncrementalBreak(topLocation, (short) 2000);
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
		Assert.assertEquals(2, blockInventory.items.get(ItemRegistry.STONE).count());
	}

	@Test
	public void breakTopFirst() throws Throwable
	{
		// Verify that breaking the top block first results in it still floating after breaking the bottom.
		// (this requires generalized block update logic).
		// We will just generate a solid stone cuboid so we don't worry about things falling due to empty space.
		CuboidAddress cuboidAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.STONE);
		AbsoluteLocation topLocation = new AbsoluteLocation(0, 0, 4);
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> cuboidAddress.equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, (IMutationBlock newMutation) -> {
					Assert.assertNull(blockHolder[0]);
					blockHolder[0] = newMutation;
				}
				, null
		);
		
		// Break the top block.
		MutableBlockProxy topBlock = new MutableBlockProxy(topLocation, cuboid);
		MutationBlockIncrementalBreak breaking = new MutationBlockIncrementalBreak(topLocation, (short) 2000);
		Assert.assertTrue(breaking.applyMutation(context, topBlock));
		topBlock.writeBack(cuboid);
		
		// We should see nothing scheduled and the inventory on the ground.
		Assert.assertNull(blockHolder[0]);
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, topLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.items.get(ItemRegistry.STONE).count());
		
		// Now, break the bottom block.
		AbsoluteLocation bottomLocation = topLocation.getRelative(0, 0, -1);
		MutableBlockProxy bottomBlock = new MutableBlockProxy(bottomLocation, cuboid);
		breaking = new MutationBlockIncrementalBreak(bottomLocation, (short) 2000);
		Assert.assertTrue(breaking.applyMutation(context, bottomBlock));
		bottomBlock.writeBack(cuboid);
		
		// We should see nothing scheduled and the inventory on the ground.
		Assert.assertNull(blockHolder[0]);
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, bottomLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.items.get(ItemRegistry.STONE).count());
		
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
		Assert.assertEquals(2, blockInventory.items.get(ItemRegistry.STONE).count());
	}
}
