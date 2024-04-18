package com.jeffdisher.october.mutations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCommonChanges
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void moveSuccess()
	{
		// Check that the move works if the blocks are air.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ENV.blocks.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), (location.z() >= 0) ? air : stone)
				, null
				, null
				, null
		);
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = oldLocation;
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
				, null
		);
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = oldLocation;
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
				, null
		);
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = oldLocation;
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
				, null
		);
		// We start with a zero z-vector since we should start falling.
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = oldLocation;
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
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ENV.blocks.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), (location.z() >= 0) ? air : stone)
				, null
				, null
				, null
		);
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = oldLocation;
		
		EntityChangeJump jump = new EntityChangeJump();
		boolean didApply = jump.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		
		// The jump doesn't move, just sets the vector.
		Assert.assertEquals(EntityChangeJump.JUMP_FORCE, newEntity.newZVelocityPerSecond, 0.01f);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
		
		// Try a few falling steps to see how we sink back to the ground.
		// (we will use 50ms updates to see the more detailed arc)
		for (int i = 0; i < 18; ++i)
		{
			EntityChangeMove fall = new EntityChangeMove(newEntity.newLocation, 50L, 0.0f, 0.0f);
			didApply = fall.applyChange(context, newEntity);
			Assert.assertTrue(didApply);
			Assert.assertTrue(newEntity.newLocation.z() > 0.0f);
		}
		// The next step puts us back on the ground.
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
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		
		// We will create a bogus context which just says that they are standing in a wall so they don't try to move.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
				, null
		);
		
		// Give the entity some items and verify that they default to selected.
		EntityChangeAcceptItems accept = new EntityChangeAcceptItems(new Items(ENV.items.LOG, 1));
		Assert.assertTrue(accept.applyChange(context, newEntity));
		Assert.assertEquals(ENV.items.LOG, newEntity.freeze().selectedItem());
		
		// Craft some items to use these up and verify that the selection is cleared.
		EntityChangeCraft craft = new EntityChangeCraft(ENV.crafting.LOG_TO_PLANKS, ENV.crafting.LOG_TO_PLANKS.millisPerCraft);
		Assert.assertTrue(craft.applyChange(context, newEntity));
		Assert.assertNull(newEntity.freeze().selectedItem());
		
		// Actively select the type and verify it is selected.
		MutationEntitySelectItem select = new MutationEntitySelectItem(ENV.items.PLANK);
		Assert.assertTrue(select.applyChange(context, newEntity));
		Assert.assertEquals(ENV.items.PLANK, newEntity.freeze().selectedItem());
		
		// Demonstrate that we can't select something we don't have.
		MutationEntitySelectItem select2 = new MutationEntitySelectItem(ENV.items.LOG);
		Assert.assertFalse(select2.applyChange(context, newEntity));
		Assert.assertEquals(ENV.items.PLANK, newEntity.freeze().selectedItem());
		
		// Show that we can unselect.
		MutationEntitySelectItem select3 = new MutationEntitySelectItem(null);
		Assert.assertTrue(select3.applyChange(context, newEntity));
		Assert.assertNull(newEntity.freeze().selectedItem());
	}

	@Test
	public void placeBlock() throws Throwable
	{
		// Create the entity in an air block so we can place this (give us a starter inventory).
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(ENV.items.LOG, 1);
		newEntity.newSelectedItem = ENV.items.LOG;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						holder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
		);
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
		Assert.assertEquals(ENV.items.LOG.number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(0, newEntity.freeze().inventory().sortedItems().size());
		Assert.assertNull(newEntity.freeze().selectedItem());
	}

	@Test
	public void pickUpItems() throws Throwable
	{
		// Create an air cuboid with items in an inventory slot and then pick it up.
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.create(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress(), Inventory.start(InventoryAspect.CAPACITY_BLOCK_EMPTY).add(ENV.items.STONE, 2).finish());
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		IMutationEntity[] entityHolder = new IMutationEntity[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(blockHolder[0]);
						blockHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IMutationEntity change)
					{
						Assert.assertEquals(entityId, targetEntityId);
						Assert.assertNull(entityHolder[0]);
						entityHolder[0] = change;
					}
					@Override
					public void future(int targetEntityId, IMutationEntity change, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
		);
		
		// This is a multi-step process which starts by asking the entity to attempt the pick-up.
		MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(targetLocation, new Items(ENV.items.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(request.applyChange(context, newEntity));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockExtractItems);
		AbsoluteLocation location = blockHolder[0].getAbsoluteLocation();
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		
		// By this point, the entity shouldn't yet have changed.
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(ENV.items.STONE));
		Assert.assertEquals(0, newEntity.newInventory.getCount(ENV.items.STONE));
		Assert.assertNull(newEntity.newSelectedItem);
		
		// We should see the request to store data, now.
		Assert.assertTrue(entityHolder[0] instanceof MutationEntityStoreToInventory);
		Assert.assertTrue(entityHolder[0].applyChange(context, newEntity));
		
		// We can now verify the final result of this - we should see the one item moved and selected since nothing else was.
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(ENV.items.STONE));
		Assert.assertEquals(1, newEntity.newInventory.getCount(ENV.items.STONE));
		Assert.assertEquals(ENV.items.STONE, newEntity.newSelectedItem);
		
		// Run the process again to pick up the last item and verify that the inventory is now null.
		blockHolder[0] = null;
		entityHolder[0] = null;
		request = new MutationEntityRequestItemPickUp(targetLocation, new Items(ENV.items.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(request.applyChange(context, newEntity));
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		Assert.assertTrue(entityHolder[0].applyChange(context, newEntity));
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertNull(blockInventory);
		Assert.assertEquals(2, newEntity.newInventory.getCount(ENV.items.STONE));
		Assert.assertEquals(ENV.items.STONE, newEntity.newSelectedItem);
	}

	@Test
	public void dropItems() throws Throwable
	{
		// Create an air cuboid and an entity with some items, then try to drop them onto a block.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.create(entityId);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		mutable.newInventory.addAllItems(ENV.items.STONE, 2);
		mutable.newSelectedItem = ENV.items.STONE;
		Entity original = mutable.freeze();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		// We need to make sure that there is a solid block under the target location so it doesn't just fall.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), ENV.items.STONE.number());
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(blockHolder[0]);
						blockHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
		);
		
		// This is a multi-step process which starts by asking the entity to start the drop.
		MutableEntity newEntity = MutableEntity.existing(original);
		MutationEntityPushItems push = new MutationEntityPushItems(targetLocation, new Items(ENV.items.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(context, newEntity));
		
		// We can now verify that the entity has lost the item but the block is unchanged.
		Assert.assertEquals(1, newEntity.newInventory.getCount(ENV.items.STONE));
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
		Assert.assertEquals(1, blockInventory.getCount(ENV.items.STONE));
		Entity freeze = newEntity.freeze();
		Assert.assertEquals(1, freeze.inventory().getCount(ENV.items.STONE));
		Assert.assertEquals(ENV.items.STONE, freeze.selectedItem());
		
		// Drop again to verify that this correctly handles dropping the last selected item.
		push = new MutationEntityPushItems(targetLocation, new Items(ENV.items.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(context, newEntity));
		Assert.assertTrue(blockHolder[0].applyMutation(context, newBlock));
		newBlock.writeBack(cuboid);
		
		// By this point, we should be able to verify both the entity and the block.
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		freeze = newEntity.freeze();
		Assert.assertEquals(2, blockInventory.getCount(ENV.items.STONE));
		Assert.assertEquals(0, freeze.inventory().sortedItems().size());
		Assert.assertNull(freeze.selectedItem());
	}

	@Test
	public void invalidPlacements() throws Throwable
	{
		// We will try to place a block colliding with the entity or too far from them to verify that this fails.
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(ENV.items.LOG, 1);
		newEntity.newSelectedItem = ENV.items.LOG;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						holder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
		);
		
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
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = new EntityLocation(6.0f - newEntity.original.volume().width(), 0.0f, 10.0f);
		
		AbsoluteLocation tooFar = new AbsoluteLocation(7, 0, 10);
		AbsoluteLocation wrongType = new AbsoluteLocation(5, 0, 10);
		AbsoluteLocation reasonable = new AbsoluteLocation(6, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, tooFar.getBlockAddress(), ENV.items.STONE.number());
		cuboid.setData15(AspectRegistry.BLOCK, wrongType.getBlockAddress(), ENV.items.PLANK.number());
		Assert.assertEquals(ENV.items.PLANK.number(), cuboid.getData15(AspectRegistry.BLOCK, wrongType.getBlockAddress()));
		cuboid.setData15(AspectRegistry.BLOCK, reasonable.getBlockAddress(), ENV.items.STONE.number());
		// (we also need to make sure that we are standing on something)
		cuboid.setData15(AspectRegistry.BLOCK, newEntity.newLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), ENV.items.PLANK.number());
		
		IMutationEntity[] holder = new IMutationEntity[1];
		boolean[] didSchedule = new boolean[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						didSchedule[0] = true;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IMutationEntity change)
					{
						holder[0] = change;
					}
					@Override
					public void future(int targetEntityId, IMutationEntity change, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
		);
		
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
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = new EntityLocation(16.0f, 16.0f, 20.0f);
		newEntity.newInventory.addAllItems(ENV.items.LOG, 1);
		newEntity.newSelectedItem = ENV.items.LOG;
		
		// We will create a bogus context which just says that they are floating in the air so they can drop.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
				, null
		);
		
		// Craft some items to use these up and verify that we also moved.
		EntityChangeCraft craft = new EntityChangeCraft(ENV.crafting.LOG_TO_PLANKS, ENV.crafting.LOG_TO_PLANKS.millisPerCraft);
		Assert.assertTrue(craft.applyChange(context, newEntity));
		Assert.assertEquals(10.2f, newEntity.newLocation.z(), 0.01f);
		Assert.assertEquals(-9.8, newEntity.newZVelocityPerSecond, 0.01f);
	}

	@Test
	public void nonBlockUsage() throws Throwable
	{
		// Show that a non-block item cannot be placed in the world, but can be placed in inventories.
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(ENV.items.LOG, 1);
		newEntity.newInventory.addAllItems(ENV.items.CHARCOAL, 2);
		newEntity.newSelectedItem = ENV.items.CHARCOAL;
		AbsoluteLocation furnace = new AbsoluteLocation(2, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		MutableBlockProxy proxy = new MutableBlockProxy(furnace, cuboid);
		proxy.setBlockAndClear(ENV.blocks.FURNACE);
		proxy.writeBack(cuboid);
		
		IMutationBlock[] holder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						holder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
		);
		
		// Fail to place the charcoal item on the ground.
		AbsoluteLocation air = new AbsoluteLocation(1, 0, 10);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(air);
		Assert.assertFalse(place.applyChange(context, newEntity));
		
		// Change the selection to the log and prove that this works.
		MutationEntitySelectItem select = new MutationEntitySelectItem(ENV.items.LOG);
		Assert.assertTrue(select.applyChange(context, newEntity));
		Assert.assertTrue(place.applyChange(context, newEntity));
		
		// Verify that we can store the charcoal into the furnace inventory or fuel inventory.
		MutationEntityPushItems pushInventory = new MutationEntityPushItems(furnace, new Items(ENV.items.CHARCOAL, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		MutationEntityPushItems pushFuel = new MutationEntityPushItems(furnace, new Items(ENV.items.CHARCOAL, 1), Inventory.INVENTORY_ASPECT_FUEL);
		Assert.assertTrue(pushInventory.applyChange(context, newEntity));
		Assert.assertTrue(pushFuel.applyChange(context, newEntity));
		
		// Verify that their inventory is now empty.
		Assert.assertEquals(0, newEntity.newInventory.getCurrentEncumbrance());
	}

	@Test
	public void duplicateItemOverfill() throws Throwable
	{
		// Fill a block to only have space for 1 item left, then show that 2 entities storing the item in the same tick still cause the block to become over-full.
		int entityId1 = 1;
		MutableEntity mutable1 = MutableEntity.create(entityId1);
		mutable1.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		mutable1.newInventory.addAllItems(ENV.items.STONE, 1);
		mutable1.newSelectedItem = ENV.items.STONE;
		int entityId2 = 2;
		MutableEntity mutable2 = MutableEntity.create(entityId2);
		mutable2.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		mutable2.newInventory.addAllItems(ENV.items.STONE, 1);
		mutable2.newSelectedItem = ENV.items.STONE;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 9);
		// We need to make sure that there is a solid block under the target location so it doesn't just fall.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), ENV.items.STONE.number());
		// Fill the inventory.
		MutableBlockProxy proxy = new MutableBlockProxy(targetLocation, cuboid);
		MutableInventory mutInv = new MutableInventory(proxy.getInventory());
		int added = mutInv.addItemsBestEfforts(ENV.items.STONE, 50);
		Assert.assertTrue(added < 50);
		mutInv.removeItems(ENV.items.STONE, 1);
		proxy.setInventory(mutInv.freeze());
		proxy.writeBack(cuboid);
		
		List<IMutationBlock> blockHolder = new ArrayList<>();
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						blockHolder.add(mutation);
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
		);
		
		// This is a multi-step process which starts by asking the entity to start the drop.
		MutationEntityPushItems push = new MutationEntityPushItems(targetLocation, new Items(ENV.items.STONE, 1), Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(context, mutable1));
		Assert.assertTrue(push.applyChange(context, mutable2));
		Assert.assertEquals(added - 1, cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()).getCount(ENV.items.STONE));
		
		// Apply the secondary mutations.
		Assert.assertEquals(2, blockHolder.size());
		for (IMutationBlock mutation : blockHolder)
		{
			Assert.assertTrue(mutation instanceof MutationBlockStoreItems);
			AbsoluteLocation location = mutation.getAbsoluteLocation();
			MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
			Assert.assertTrue(mutation.applyMutation(context, newBlock));
			newBlock.writeBack(cuboid);
		}
		
		// Verify that this is over-full.
		Inventory inventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(added + 1, inventory.getCount(ENV.items.STONE));
		Assert.assertTrue(inventory.currentEncumbrance > inventory.maxEncumbrance);
	}

	@Test
	public void itemDropOverfill() throws Throwable
	{
		// Fill 2 blocks in the column and show that the top falls into the bottom, even making it over-full.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 9);
		// We need to make sure that there is a solid block under the target location so it doesn't just fall.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), ENV.items.STONE.number());
		// Fill the inventory.
		MutableBlockProxy proxy = new MutableBlockProxy(targetLocation, cuboid);
		MutableInventory mutInv = new MutableInventory(proxy.getInventory());
		int added = mutInv.addItemsBestEfforts(ENV.items.STONE, 50);
		Assert.assertTrue(added < 50);
		proxy.setInventory(mutInv.freeze());
		proxy.writeBack(cuboid);
		
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(blockHolder[0]);
						blockHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
		);
		
		// Just directly create the mutation to push items into the block above this one.
		AbsoluteLocation dropLocation = targetLocation.getRelative(0, 0, 1);
		MutationBlockStoreItems mutations = new MutationBlockStoreItems(dropLocation, new Items(ENV.items.STONE, added), Inventory.INVENTORY_ASPECT_INVENTORY);
		proxy = new MutableBlockProxy(dropLocation, cuboid);
		Assert.assertTrue(mutations.applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// This should cause a follow-up so run that.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		AbsoluteLocation mutationTarget = blockHolder[0].getAbsoluteLocation();
		Assert.assertEquals(targetLocation, mutationTarget);
		proxy = new MutableBlockProxy(mutationTarget, cuboid);
		Assert.assertTrue(blockHolder[0].applyMutation(context, proxy));
		proxy.writeBack(cuboid);
		
		// Verify we see only the one inventory, and over-filled.
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, dropLocation.getBlockAddress()));
		Assert.assertEquals(2 * added, cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()).getCount(ENV.items.STONE));
	}

	@Test
	public void storeItemInFullInventory() throws Throwable
	{
		// Normally, we won't try to store an item if the inventory is already full but something like breaking a block bypasses that check so see what happens when the inventory is full.
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.create(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		int stored = newEntity.newInventory.addItemsBestEfforts(ENV.items.STONE, 100);
		Assert.assertTrue(stored < 100);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		IMutationEntity[] entityHolder = new IMutationEntity[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(blockHolder[0]);
						blockHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IMutationEntity change)
					{
						Assert.assertEquals(entityId, targetEntityId);
						Assert.assertNull(entityHolder[0]);
						entityHolder[0] = change;
					}
					@Override
					public void future(int targetEntityId, IMutationEntity change, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
		);
		
		// Create the change.
		MutationEntityStoreToInventory store = new MutationEntityStoreToInventory(new Items(ENV.items.STONE, 1));
		Assert.assertTrue(store.applyChange(context, newEntity));
		
		// We should see the attempt to drop this item onto the ground since it won't fit.
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
		Assert.assertNull(entityHolder[0]);
	}

	@Test
	public void attackEntity() throws Throwable
	{
		// Verify the range check and correct follow-up, on hit.
		int attackerId = 1;
		int targetId = 2;
		int missId = 3;
		MutableEntity attacker = MutableEntity.create(attackerId);
		attacker.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		MutableEntity target = MutableEntity.create(targetId);
		target.newLocation = new EntityLocation(9.0f, 9.0f, 0.0f);
		target.newInventory.addAllItems(ENV.items.STONE, 2);
		target.newSelectedItem = ENV.items.STONE;
		MutableEntity miss = MutableEntity.create(missId);
		miss.newLocation = new EntityLocation(12.0f, 10.0f, 0.0f);
		
		Map<Integer, Entity> targetsById = Map.of(targetId, target.freeze(), missId, miss.freeze());
		int[] targetHolder = new int[1];
		IMutationEntity[] changeHolder = new IMutationEntity[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, null
				, (Integer thisId) -> targetsById.get(thisId)
				, null
				, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IMutationEntity change)
					{
						Assert.assertNull(changeHolder[0]);
						targetHolder[0] = targetEntityId;
						changeHolder[0] = change;
					}
					@Override
					public void future(int targetEntityId, IMutationEntity change, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
		);
		
		// Check the miss.
		Assert.assertFalse(new EntityChangeAttackEntity(missId).applyChange(context, attacker));
		Assert.assertNull(changeHolder[0]);
		
		// Check the hit.
		Assert.assertTrue(new EntityChangeAttackEntity(targetId).applyChange(context, attacker));
		Assert.assertEquals(targetId, targetHolder[0]);
		Assert.assertTrue(changeHolder[0] instanceof EntityChangeTakeDamage);
	}

	@Test
	public void takeDamage() throws Throwable
	{
		// Verify that damage is correctly applied, as well as the "respawn" mechanic.
		int attackerId = 1;
		int targetId = 2;
		MutableEntity attacker = MutableEntity.create(attackerId);
		attacker.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		MutableEntity target = MutableEntity.create(targetId);
		target.newLocation = new EntityLocation(9.0f, 9.0f, 0.0f);
		target.newInventory.addAllItems(ENV.items.STONE, 2);
		target.newSelectedItem = ENV.items.STONE;
		
		// We need to make sure that there is a solid block under the entities so nothing falls.
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.blocks.AIR);
		CuboidAddress stoneAddress = new CuboidAddress((short)0, (short)0, (short)-1);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(stoneAddress, ENV.blocks.STONE);
		
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) ->
				{
					CuboidAddress address = location.getCuboidAddress();
					BlockAddress block = location.getBlockAddress();
					BlockProxy proxy;
					if (address.equals(airAddress))
					{
						proxy = new BlockProxy(block, airCuboid);
					}
					else
					{
						Assert.assertTrue(address.equals(stoneAddress));
						proxy = new BlockProxy(block, stoneCuboid);
					}
					return proxy;
				}
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(blockHolder[0]);
						blockHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
		);
		
		// Now, we will attack in 2 swipes to verify damage is taken but also the respawn logic works.
		EntityChangeTakeDamage takeDamage = new EntityChangeTakeDamage((byte) 60);
		Assert.assertTrue(takeDamage.applyChange(context, target));
		Assert.assertEquals((byte)40, target.newHealth);
		Assert.assertNull(blockHolder[0]);
		
		Assert.assertTrue(takeDamage.applyChange(context, target));
		Assert.assertEquals(MutableEntity.DEFAULT_HEALTH, target.newHealth);
		Assert.assertEquals(MutableEntity.DEFAULT_FOOD, target.newFood);
		Assert.assertEquals(0, target.newInventory.freeze().sortedItems().size());
		Assert.assertEquals(null, target.newSelectedItem);
		Assert.assertEquals(MutableEntity.DEFAULT_LOCATION, target.newLocation);
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
	}

	@Test
	public void entityPeriodic()
	{
		CommonChangeSink changeSink = new CommonChangeSink();
		TickProcessingContext context = new TickProcessingContext(0L
				, null
				, null
				, null
				, changeSink
		);
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.create(entityId);
		EntityChangePeriodic periodic = new EntityChangePeriodic();
		Assert.assertTrue(periodic.applyChange(context, newEntity));
		Assert.assertEquals((byte)99, newEntity.newFood);
		newEntity.newHealth = 99;
		Assert.assertTrue(periodic.applyChange(context, newEntity));
		Assert.assertEquals((byte)98, newEntity.newFood);
		Assert.assertEquals((byte)100, newEntity.newHealth);
		newEntity.newFood = 0;
		Assert.assertTrue(periodic.applyChange(context, newEntity));
		Assert.assertEquals((byte)0, newEntity.newFood);
		// The health change will be applied by the TakeDamage change.
		Assert.assertEquals((byte)100, newEntity.newHealth);
		
		// We should see one call enqueued for each call except the starving one, where we should see 2.
		Assert.assertEquals(3 + 1, changeSink.takeExportedChanges().get(entityId).size());
	}

	@Test
	public void eatBread() throws Throwable
	{
		// Show that we can eat bread, but not stone, and that the bread increases our food level.
		Item bread = ENV.items.getItemById("op.bread");
		MutableEntity newEntity = MutableEntity.create(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(ENV.items.STONE, 1);
		newEntity.newInventory.addAllItems(bread, 1);
		newEntity.newSelectedItem = ENV.items.LOG;
		newEntity.newFood = 90;
		TickProcessingContext context = new TickProcessingContext(0L
				, null
				, null
				, null
				, null
		);
		
		// We will fail to eat the log.
		EntityChangeEatSelectedItem eat = new EntityChangeEatSelectedItem();
		Assert.assertFalse(eat.applyChange(context, newEntity));
		Assert.assertEquals(90, newEntity.newFood);
		
		// We should succeed in eating the bread, though.
		newEntity.newSelectedItem = bread;
		Assert.assertTrue(eat.applyChange(context, newEntity));
		
		Assert.assertNull(newEntity.newSelectedItem);
		Assert.assertEquals(1, newEntity.newInventory.getCount(ENV.items.STONE));
		Assert.assertEquals(0, newEntity.newInventory.getCount(bread));
		Assert.assertEquals(100, newEntity.newFood);
	}
}
