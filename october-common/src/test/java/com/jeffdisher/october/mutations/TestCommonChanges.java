package com.jeffdisher.october.mutations;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.Craft;
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


public class TestCommonChanges
{
	@Test
	public void moveSuccess()
	{
		// Check that the move works if the blocks are air.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), (location.z() >= 0) ? air : stone)
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(newLocation, newEntity.newLocation);
	}

	@Test
	public void moveBarrier()
	{
		// Check that the move fails if the blocks are stone.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertFalse(didApply);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
	}

	@Test
	public void moveMissing()
	{
		// Check that the move fails if the target cuboid is missing.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> null
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertFalse(didApply);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
	}

	@Test
	public void fallingThroughAir()
	{
		// Position us in an air block and make sure that we fall.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
		);
		// We start with a zero z-vector since we should start falling.
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		// We expect that we fell for 100 ms so we would have applied acceleration for 1/10 second.
		float expectedZVector = -0.98f;
		// This movement would then be applied for 1/10 second.
		EntityLocation expectedLocation = new EntityLocation(oldLocation.x() + 0.4f, oldLocation.y(), oldLocation.z() + (expectedZVector / 10.0f));
		Assert.assertEquals(expectedZVector, newEntity.newZVelocityPerSecond, 0.01f);
		Assert.assertEquals(expectedLocation, newEntity.newLocation);
	}

	@Test
	public void jumpAndFall()
	{
		// Jump into the air and fall back down.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), (location.z() >= 0) ? air : stone)
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		MutableEntity newEntity = new MutableEntity(original);
		
		EntityChangeJump jump = new EntityChangeJump();
		boolean didApply = jump.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		
		// The jump doesn't move, just sets the vector.
		Assert.assertEquals(5.88f, newEntity.newZVelocityPerSecond, 0.01f);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
		
		// Try a few falling steps to see how we sink back to the ground.
		for (int i = 0; i < 10; ++i)
		{
			EntityChangeMove fall = new EntityChangeMove(newEntity.newLocation, 100L, 0.0f, 0.0f);
			didApply = fall.applyChange(context, newEntity);
			Assert.assertTrue(didApply);
			Assert.assertTrue(newEntity.newLocation.z() > 0.0f);
		}
		// The 11th step puts us back on the ground.
		EntityChangeMove fall = new EntityChangeMove(newEntity.newLocation, 100L, 0.0f, 0.0f);
		didApply = fall.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertTrue(0.0f == newEntity.newLocation.z());
		// However, the vector is still drawing us down (since the vector is updated at the beginning of the move, not the end).
		Assert.assertEquals(-4.9f, newEntity.newZVelocityPerSecond, 0.01f);
		
		// Fall one last time to finalize "impact".
		fall = new EntityChangeMove(newEntity.newLocation, 100L, 0.0f, 0.0f);
		didApply = fall.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertTrue(0.0f == newEntity.newLocation.z());
		Assert.assertEquals(0.0f, newEntity.newZVelocityPerSecond, 0.01f);
	}

	@Test
	public void selection() throws Throwable
	{
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		MutableEntity newEntity = new MutableEntity(original);
		
		// We will create a bogus context which just says that they are standing in a wall so they don't try to move.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
		);
		
		// Give the entity some items and verify that they default to selected.
		EntityChangeAcceptItems accept = new EntityChangeAcceptItems(new Items(ItemRegistry.LOG, 1));
		Assert.assertTrue(accept.applyChange(context, newEntity));
		Assert.assertEquals(ItemRegistry.LOG, newEntity.freeze().selectedItem());
		
		// Craft some items to use these up and verify that the selection is cleared.
		EntityChangeCraft craft = new EntityChangeCraft(Craft.LOG_TO_PLANKS, Craft.LOG_TO_PLANKS.millisPerCraft);
		Assert.assertTrue(craft.applyChange(context, newEntity));
		Assert.assertNull(newEntity.freeze().selectedItem());
		
		// Actively select the type and verify it is selected.
		MutationEntitySelectItem select = new MutationEntitySelectItem(ItemRegistry.PLANK);
		Assert.assertTrue(select.applyChange(context, newEntity));
		Assert.assertEquals(ItemRegistry.PLANK, newEntity.freeze().selectedItem());
		
		// Demonstrate that we can't select something we don't have.
		MutationEntitySelectItem select2 = new MutationEntitySelectItem(ItemRegistry.LOG);
		Assert.assertFalse(select2.applyChange(context, newEntity));
		Assert.assertEquals(ItemRegistry.PLANK, newEntity.freeze().selectedItem());
		
		// Show that we can unselect.
		MutationEntitySelectItem select3 = new MutationEntitySelectItem(null);
		Assert.assertTrue(select3.applyChange(context, newEntity));
		Assert.assertNull(newEntity.freeze().selectedItem());
	}

	@Test
	public void placeBlock() throws Throwable
	{
		// Create the entity in an air block so we can place this (give us a starter inventory).
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).add(ItemRegistry.LOG, 1).finish(), ItemRegistry.LOG, null);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, (IMutationBlock newMutation) -> holder[0] = newMutation
				, null
		);
		MutableEntity newEntity = new MutableEntity(original);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 10);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(target);
		Assert.assertTrue(place.applyChange(context, newEntity));
		
		// We also need to apply the actual mutation.
		Assert.assertTrue(holder[0] instanceof MutationBlockOverwrite);
		AbsoluteLocation location = holder[0].getAbsoluteLocation();
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(holder[0].applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// We expect that the block will be placed and our selection and inventory will be cleared.
		Assert.assertEquals(ItemRegistry.LOG.number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(0, newEntity.freeze().inventory().items.size());
		Assert.assertNull(newEntity.freeze().selectedItem());
	}

	@Test
	public void pickUpItems() throws Throwable
	{
		// Create an air cuboid with items in an inventory slot and then pick it up.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		int entityId = 1;
		Entity original = new Entity(entityId, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress(), Inventory.start(InventoryAspect.CAPACITY_AIR).add(ItemRegistry.STONE, 2).finish());
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		IMutationEntity[] entityHolder = new IMutationEntity[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, (IMutationBlock newMutation) -> {
					Assert.assertNull(blockHolder[0]);
					blockHolder[0] = newMutation;
				}
				, (int targetEntityId, IMutationEntity change) -> {
					Assert.assertEquals(entityId, targetEntityId);
					Assert.assertNull(entityHolder[0]);
					entityHolder[0] = change;
				}
		);
		
		// This is a multi-step process which starts by asking the entity to attempt the pick-up.
		MutableEntity newEntity = new MutableEntity(original);
		MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(targetLocation, new Items(ItemRegistry.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(request.applyChange(context, newEntity));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockExtractItems);
		AbsoluteLocation location = blockHolder[0].getAbsoluteLocation();
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		
		// By this point, the entity shouldn't yet have changed.
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.items.get(ItemRegistry.STONE).count());
		Assert.assertEquals(0, newEntity.newInventory.getCount(ItemRegistry.STONE));
		Assert.assertNull(newEntity.newSelectedItem);
		
		// We should see the request to store data, now.
		Assert.assertTrue(entityHolder[0] instanceof MutationEntityStoreToInventory);
		Assert.assertTrue(entityHolder[0].applyChange(context, newEntity));
		
		// We can now verify the final result of this - we should see the one item moved and selected since nothing else was.
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.items.get(ItemRegistry.STONE).count());
		Assert.assertEquals(1, newEntity.newInventory.getCount(ItemRegistry.STONE));
		Assert.assertEquals(ItemRegistry.STONE, newEntity.newSelectedItem);
		
		// Run the process again to pick up the last item and verify that the inventory is now null.
		blockHolder[0] = null;
		entityHolder[0] = null;
		request = new MutationEntityRequestItemPickUp(targetLocation, new Items(ItemRegistry.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(request.applyChange(context, newEntity));
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		Assert.assertTrue(entityHolder[0].applyChange(context, newEntity));
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertNull(blockInventory);
		Assert.assertEquals(2, newEntity.newInventory.getCount(ItemRegistry.STONE));
		Assert.assertEquals(ItemRegistry.STONE, newEntity.newSelectedItem);
	}

	@Test
	public void dropItems() throws Throwable
	{
		// Create an air cuboid and an entity with some items, then try to drop them onto a block.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		int entityId = 1;
		Entity original = new Entity(entityId, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).add(ItemRegistry.STONE, 2).finish(), ItemRegistry.STONE, null);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
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
		
		// We can now verify that the entity has lost the item but the block is unchanged.
		Assert.assertEquals(1, newEntity.newInventory.getCount(ItemRegistry.STONE));
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		AbsoluteLocation location = blockHolder[0].getAbsoluteLocation();
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		blockHolder[0] = null;
		
		// By this point, we should be able to verify both the entity and the block.
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.items.get(ItemRegistry.STONE).count());
		Entity freeze = newEntity.freeze();
		Assert.assertEquals(1, freeze.inventory().items.get(ItemRegistry.STONE).count());
		Assert.assertEquals(ItemRegistry.STONE, freeze.selectedItem());
		
		// Drop again to verify that this correctly handles dropping the last selected item.
		push = new MutationEntityPushItems(targetLocation, new Items(ItemRegistry.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(context, newEntity));
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		
		// By this point, we should be able to verify both the entity and the block.
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		freeze = newEntity.freeze();
		Assert.assertEquals(2, blockInventory.items.get(ItemRegistry.STONE).count());
		Assert.assertEquals(0, freeze.inventory().items.size());
		Assert.assertNull(freeze.selectedItem());
	}

	@Test
	public void invalidPlacements() throws Throwable
	{
		// We will try to place a block colliding with the entity or too far from them to verify that this fails.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).add(ItemRegistry.LOG, 1).finish(), ItemRegistry.LOG, null);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, (IMutationBlock newMutation) -> holder[0] = newMutation
				, null
		);
		MutableEntity newEntity = new MutableEntity(original);
		
		// Try too close (colliding).
		AbsoluteLocation tooClose = new AbsoluteLocation(0, 0, 10);
		MutationPlaceSelectedBlock placeTooClose = new MutationPlaceSelectedBlock(tooClose);
		Assert.assertFalse(placeTooClose.applyChange(context, newEntity));
		
		// Try too far.
		AbsoluteLocation tooFar = new AbsoluteLocation(0, 0, 15);
		MutationPlaceSelectedBlock placeTooFar = new MutationPlaceSelectedBlock(tooFar);
		Assert.assertFalse(placeTooFar.applyChange(context, newEntity));
		
		// Try reasonable location.
		AbsoluteLocation reasonable = new AbsoluteLocation(1, 1, 8);
		MutationPlaceSelectedBlock placeReasonable = new MutationPlaceSelectedBlock(reasonable);
		Assert.assertTrue(placeReasonable.applyChange(context, newEntity));
		
		// Make sure we fail if there is no selection.
		reasonable = new AbsoluteLocation(1, 1, 8);
		placeReasonable = new MutationPlaceSelectedBlock(reasonable);
		newEntity.newSelectedItem = null;
		Assert.assertFalse(placeReasonable.applyChange(context, newEntity));
	}

	@Test
	public void invalidBreak() throws Throwable
	{
		// We will try to place a breaking a block of the wrong type or too far away.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null, null);
		
		AbsoluteLocation tooFar = new AbsoluteLocation(3, 0, 10);
		AbsoluteLocation wrongType = new AbsoluteLocation(0, 0, 10);
		AbsoluteLocation reasonable = new AbsoluteLocation(1, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, tooFar.getBlockAddress(), ItemRegistry.STONE.number());
		cuboid.setData15(AspectRegistry.BLOCK, wrongType.getBlockAddress(), ItemRegistry.PLANK.number());
		Assert.assertEquals(ItemRegistry.PLANK.number(), cuboid.getData15(AspectRegistry.BLOCK, wrongType.getBlockAddress()));
		cuboid.setData15(AspectRegistry.BLOCK, reasonable.getBlockAddress(), ItemRegistry.STONE.number());
		
		IMutationEntity[] holder = new IMutationEntity[1];
		boolean[] didSchedule = new boolean[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, (IMutationBlock newMutation) -> didSchedule[0] = true
				, (int targetEntityId, IMutationEntity change) -> holder[0] = change
		);
		MutableEntity newEntity = new MutableEntity(original);
		
		// Try too far.
		EntityChangeIncrementalBlockBreak breakTooFar = new EntityChangeIncrementalBlockBreak(tooFar, (short)100);
		Assert.assertFalse(breakTooFar.applyChange(context, newEntity));
		Assert.assertFalse(didSchedule[0]);
		
		// Try reasonable location.
		EntityChangeIncrementalBlockBreak breakReasonable = new EntityChangeIncrementalBlockBreak(reasonable, (short)100);
		Assert.assertTrue(breakReasonable.applyChange(context, newEntity));
		Assert.assertTrue(didSchedule[0]);
	}

	@Test
	public void fallAfterCraft() throws Throwable
	{
		// We want to run a basic craft operation and observe that we start falling when it completes.
		// (this will need to be adapted when the crafting system changes, later)
		EntityLocation oldLocation = new EntityLocation(16.0f, 16.0f, 20.0f);
		Entity original = new Entity(1
				, oldLocation
				, 0.0f
				, new EntityVolume(1.2f, 0.5f)
				, 0.4f
				, Inventory.start(10).add(ItemRegistry.LOG, 1).finish()
				, ItemRegistry.LOG
				, null
		);
		MutableEntity newEntity = new MutableEntity(original);
		
		// We will create a bogus context which just says that they are floating in the air so they can drop.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
		);
		
		// Craft some items to use these up and verify that we also moved.
		EntityChangeCraft craft = new EntityChangeCraft(Craft.LOG_TO_PLANKS, Craft.LOG_TO_PLANKS.millisPerCraft);
		Assert.assertTrue(craft.applyChange(context, newEntity));
		Assert.assertEquals(10.2f, newEntity.newLocation.z(), 0.01f);
		Assert.assertEquals(-9.8, newEntity.newZVelocityPerSecond, 0.01f);
	}

	@Test
	public void nonBlockUsage() throws Throwable
	{
		// Show that a non-block item cannot be placed in the world, but can be placed in inventories.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10)
				.add(ItemRegistry.LOG, 1)
				.add(ItemRegistry.CHARCOAL, 2)
				.finish(), ItemRegistry.CHARCOAL, null);
		
		AbsoluteLocation furnace = new AbsoluteLocation(2, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		MutableBlockProxy proxy = new MutableBlockProxy(furnace, cuboid);
		proxy.setBlockAndClear(ItemRegistry.FURNACE.asBlock());
		proxy.writeBack(cuboid);
		
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, (IMutationBlock newMutation) -> holder[0] = newMutation
				, null
		);
		MutableEntity newEntity = new MutableEntity(original);
		
		// Fail to place the charcoal item on the ground.
		AbsoluteLocation air = new AbsoluteLocation(1, 0, 10);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(air);
		Assert.assertFalse(place.applyChange(context, newEntity));
		
		// Change the selection to the log and prove that this works.
		MutationEntitySelectItem select = new MutationEntitySelectItem(ItemRegistry.LOG);
		Assert.assertTrue(select.applyChange(context, newEntity));
		Assert.assertTrue(place.applyChange(context, newEntity));
		
		// Verify that we can store the charcoal into the furnace inventory or fuel inventory.
		MutationEntityPushItems pushInventory = new MutationEntityPushItems(furnace, new Items(ItemRegistry.CHARCOAL, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		MutationEntityPushItems pushFuel = new MutationEntityPushItems(furnace, new Items(ItemRegistry.CHARCOAL, 1), Inventory.INVENTORY_ASPECT_FUEL);
		Assert.assertTrue(pushInventory.applyChange(context, newEntity));
		Assert.assertTrue(pushFuel.applyChange(context, newEntity));
		
		// Verify that their inventory is now empty.
		Assert.assertEquals(0, newEntity.newInventory.getCurrentEncumbrance());
	}
}
