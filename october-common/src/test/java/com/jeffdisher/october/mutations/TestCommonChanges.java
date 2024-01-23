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
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null);
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
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null);
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
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null);
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
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null);
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
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null);
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
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null);
		MutableEntity newEntity = new MutableEntity(original);
		
		// Give the entity some items and verify that they default to selected.
		EntityChangeAcceptItems accept = new EntityChangeAcceptItems(new Items(ItemRegistry.LOG, 1));
		Assert.assertTrue(accept.applyChange(null, newEntity));
		Assert.assertEquals(ItemRegistry.LOG, newEntity.freeze().selectedItem());
		
		// Craft some items to use these up and verify that the selection is cleared.
		EntityChangeCraft craft = new EntityChangeCraft(Craft.LOG_TO_PLANKS);
		Assert.assertTrue(craft.applyChange(null, newEntity));
		Assert.assertNull(newEntity.freeze().selectedItem());
		
		// Actively select the type and verify it is selected.
		MutationEntitySelectItem select = new MutationEntitySelectItem(ItemRegistry.PLANK);
		Assert.assertTrue(select.applyChange(null, newEntity));
		Assert.assertEquals(ItemRegistry.PLANK, newEntity.freeze().selectedItem());
		
		// Demonstrate that we can't select something we don't have.
		MutationEntitySelectItem select2 = new MutationEntitySelectItem(ItemRegistry.LOG);
		Assert.assertFalse(select2.applyChange(null, newEntity));
		Assert.assertEquals(ItemRegistry.PLANK, newEntity.freeze().selectedItem());
		
		// Show that we can unselect.
		MutationEntitySelectItem select3 = new MutationEntitySelectItem(null);
		Assert.assertTrue(select3.applyChange(null, newEntity));
		Assert.assertNull(newEntity.freeze().selectedItem());
	}

	@Test
	public void placeBlock() throws Throwable
	{
		// Create the entity in an air block so we can place this (give us a starter inventory).
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).add(ItemRegistry.LOG, 1).finish(), ItemRegistry.LOG);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, (IMutationBlock newMutation) -> holder[0] = newMutation
				, null
		);
		MutableEntity newEntity = new MutableEntity(original);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 1);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(target);
		Assert.assertTrue(place.applyChange(context, newEntity));
		
		// We also need to apply the actual mutation.
		Assert.assertTrue(holder[0] instanceof MutationBlockOverwrite);
		Assert.assertTrue(holder[0].applyMutation(context, new MutableBlockProxy(holder[0].getAbsoluteLocation().getBlockAddress(), cuboid)));
		
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
		Entity original = new Entity(entityId, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, Inventory.start(10).finish(), null);
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
		MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(targetLocation, new Items(ItemRegistry.STONE, 1));
		Assert.assertTrue(request.applyChange(context, newEntity));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockExtractItems);
		MutableBlockProxy newBlock = new MutableBlockProxy(blockHolder[0].getAbsoluteLocation().getBlockAddress(), cuboid);
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		
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
		request = new MutationEntityRequestItemPickUp(targetLocation, new Items(ItemRegistry.STONE, 1));
		Assert.assertTrue(request.applyChange(context, newEntity));
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		Assert.assertTrue(entityHolder[0].applyChange(context, newEntity));
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertNull(blockInventory);
		Assert.assertEquals(2, newEntity.newInventory.getCount(ItemRegistry.STONE));
		Assert.assertEquals(ItemRegistry.STONE, newEntity.newSelectedItem);
	}
}
