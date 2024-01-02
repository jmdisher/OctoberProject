package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.changes.EndBreakBlockChange;
import com.jeffdisher.october.changes.EntityChangeAcceptItems;
import com.jeffdisher.october.changes.EntityChangeCancel;
import com.jeffdisher.october.changes.EntityChangeCraft;
import com.jeffdisher.october.changes.EntityChangeMove;
import com.jeffdisher.october.changes.EntityChangeMutation;
import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.logic.EntityChangeReceiveItem;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.mutations.BreakBlockMutation;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.mutations.ReplaceBlockMutation;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestSpeculativeProjection
{
	@Test
	public void basicApplyMatching()
	{
		// We want to test that adding a few mutations as speculative, but then adding them as "committed" causes no problem.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(cuboid)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Apply a few local mutations.
		IMutation mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 0), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone1 = new EntityChangeMutation(mutation1);
		IMutation mutation2 = new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 1), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		long commit2 = projector.applyLocalChange(lone2, 1L);
		List<IMutation> mutationsToCommit = new ArrayList<>();
		Queue<IEntityChange> localEntityChangesToCommit = new LinkedList<>();
		long[] commitNumbers = new long[5];
		for (int i = 0; i < commitNumbers.length; ++i)
		{
			AbsoluteLocation location = new AbsoluteLocation(i, 0, 0);
			IMutation mutation = new ReplaceBlockMutation(location, BlockAspect.AIR, BlockAspect.STONE);
			IEntityChange entityChange = new EntityChangeMutation(mutation);
			localEntityChangesToCommit.add(entityChange);
			mutationsToCommit.add(mutation);
			commitNumbers[i] = projector.applyLocalChange(entityChange, 1L);
		}
		Assert.assertEquals(1 + 7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit the first 2, one at a time, and then the last ones at the same time.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone1)))
				, List.of(mutation1)
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		// Only the changes are in the speculative list:  We passed in 7 and committed 1.
		Assert.assertEquals(6, speculativeCount);
		Assert.assertEquals(1 + 7 + 1, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone2)))
				, List.of(mutation2)
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, 1L
		);
		// 5 changes left.
		Assert.assertEquals(5, speculativeCount);
		Assert.assertEquals(1 + 7 + 1 + 1, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, localEntityChangesToCommit)
				, mutationsToCommit
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumbers[commitNumbers.length - 1]
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1 + 7 + 1 + 1 + 1, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Now, unload.
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address)
				, commitNumbers[commitNumbers.length - 1]
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.unloadCount);
	}

	@Test
	public void unloadWithMutations()
	{
		// Test that unloading a cuboid with local mutations correctly purges them but can go on to commit other things.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		Assert.assertEquals(0, listener.changeCount);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ItemRegistry.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ItemRegistry.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(2, listener.loadCount);
		Assert.assertEquals(2, listener.changeCount);
		
		// Apply a few local mutations.
		IMutation mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone0 = new EntityChangeMutation(mutation0);
		IMutation mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		long commit1 = projector.applyLocalChange(lone1, 1L);
		Assert.assertEquals(2 + 2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit the other one.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone0)))
				, List.of(mutation0)
				, Collections.emptyList()
				, List.of(address1)
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2 + 2 + 1, listener.changeCount);
		Assert.assertEquals(1, listener.unloadCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Unload the other.
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address0)
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void applyWithConflicts()
	{
		// We want to test that adding a few mutations as speculative, and then committing a few conflicts to make sure that we drop the speculative mutaions which fail.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ItemRegistry.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ItemRegistry.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(2, listener.loadCount);
		Assert.assertEquals(2, listener.changeCount);
		
		// Apply a few local mutations.
		IMutation mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone0 = new EntityChangeMutation(mutation0);
		IMutation mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		long commit1 = projector.applyLocalChange(lone1, 1L);
		Assert.assertEquals(2 + 2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone0)))
				, List.of(mutation0)
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		// We should still see the other one.
		// Note that this is +2 since both entity changes stay in the list, despite both failing - we will still send them to the server unless they do pre-checking.
		Assert.assertEquals(2, speculativeCount);
		// We see another 2 changes due to the reverses.
		Assert.assertEquals(2 + 2 + 2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit the other one normally.
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone1)))
				, List.of(mutation1)
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		// This final +2 is because we applied both local changes, last time (even though 1 of the mutations failed to apply, we still create a new CuboidData instance - might be worth optimizing in the future).
		Assert.assertEquals(2 + 2 + 2 + 2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address0, address1)
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void applySecondaryMutations()
	{
		// We want to apply a few mutations which themselves cause secondary mutations, and observe what happens when some commit versus conflict.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ItemRegistry.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ItemRegistry.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(2, listener.loadCount);
		Assert.assertEquals(2, listener.changeCount);
		
		// Apply a few local mutations.
		IMutation mutation0 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 5), 2);
		IEntityChange lone0 = new EntityChangeMutation(mutation0);
		IMutation mutation1 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 37), 2);
		IEntityChange lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		Assert.assertEquals(2 + 2, listener.changeCount);
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone0)))
				, List.of(mutation0)
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		// We should still just see the initial changes in the speculative list.
		Assert.assertEquals(2, speculativeCount);
		// We see another 2 changes due to the reverses.
		Assert.assertEquals(2 + 2 + 2, listener.changeCount);
		
		// Commit the other one normally.
		speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone1)))
				, List.of(mutation1)
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		// This commit level change should cause them all to be retired.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2 + 2 + 2 + 2, listener.changeCount);
		
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address0, address1)
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void itemInventory()
	{
		// Test that we can apply inventory changes to speculative mutation.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(cuboid)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(1, listener.changeCount);
		
		// Try to drop a few items.
		int encumbrance = 2;
		Item stoneItem = ItemRegistry.STONE;
		AbsoluteLocation block1 = new AbsoluteLocation(1, 1, 1);
		IMutation mutation1 = new DropItemMutation(block1, stoneItem, 1);
		IEntityChange lone1 = new EntityChangeMutation(mutation1);
		AbsoluteLocation block2 = new AbsoluteLocation(3, 3, 3);
		IMutation mutation2 = new DropItemMutation(block2, stoneItem, 3);
		IEntityChange lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		long commit2 = projector.applyLocalChange(lone2, 1L);
		Assert.assertEquals(1 + 2, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Commit the first, then the second, making sure that things make sense at every point.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone1)))
				, List.of(mutation1)
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(1 + 2 + 1, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(lone2)))
				, List.of(mutation2)
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1 + 2 + 1 + 1, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Now, unload.
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address)
				, commit2
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.unloadCount);
	}

	@Test
	public void dependentEntityChanges()
	{
		// Test that we can enqueue new entity changes from within an entity change.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		
		// We need 2 entities for this but we will give one some items.
		Inventory startInventory = new Inventory(10, Map.of(ItemRegistry.STONE, new Items(ItemRegistry.STONE, 2)), 2 * ItemRegistry.STONE.encumbrance());
		projector.applyChangesForServerTick(0L
				, List.of(new Entity(0, EntityActionValidator.DEFAULT_LOCATION, EntityActionValidator.DEFAULT_VOLUME, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED, startInventory)
						, EntityActionValidator.buildDefaultEntity(1))
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		Assert.assertNotNull(listener.lastEntityStates.get(1));
		
		// Try to pass the items to the other entity.
		IEntityChange send = new EntityChangeSendItem(1, ItemRegistry.STONE);
		long commit1 = projector.applyLocalChange(send, 1L);
		
		// Check the values.
		Assert.assertTrue(listener.lastEntityStates.get(0).inventory().items.isEmpty());
		Assert.assertEquals(1, listener.lastEntityStates.get(1).inventory().items.size());
		Items update = listener.lastEntityStates.get(1).inventory().items.get(ItemRegistry.STONE);
		Assert.assertEquals(2, update.count());
		
		// Commit this and make sure the values are still correct.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(send)))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		// NOTE:  Inventory transfers are 2 changes and we expect to get them both from the server so send what we would expect it to create.
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(1, new LinkedList<>(List.of(new EntityChangeReceiveItem(ItemRegistry.STONE, 2))))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		
		Assert.assertTrue(listener.lastEntityStates.get(0).inventory().items.isEmpty());
		Assert.assertEquals(1, listener.lastEntityStates.get(1).inventory().items.size());
		update = listener.lastEntityStates.get(1).inventory().items.get(ItemRegistry.STONE);
		Assert.assertEquals(2, update.count());
	}

	@Test
	public void multiPhaseFullCommit()
	{
		// Test that we can apply a multi-phase change, observe it complete, and observe it be correctly merged with server changes in the simple case.
		CountingListener listener = new CountingListener();
		int entityId = 0;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(entityId))
				, List.of(cuboid)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.changeCount);
		
		// Enqueue a local change to break a block but observe that nothing has changed in the data.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		currentTimeMillis += 100L;
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE.number());
		long commitNumber = projector.applyLocalChange(longRunningChange, currentTimeMillis);
		Assert.assertEquals(1, commitNumber);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Allow time to pass in the local environment and observe that the change has happened.
		currentTimeMillis += 200L;
		projector.checkCurrentActivity(currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Check what happens if we commit all of this - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 100L;
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(entityId, new LinkedList<>(List.of(longRunningChange)))
				, List.of(new BreakBlockMutation(changeLocation, BlockAspect.STONE))
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumber
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
	}

	@Test
	public void multiPhaseInterruption()
	{
		// Test that we can apply a multi-phase change, but then interrupt it part way with another, observe that one complete, and observe it be correctly merged with server changes.
		CountingListener listener = new CountingListener();
		int entityId = 0;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(entityId))
				, List.of(cuboid)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.changeCount);
		
		// Enqueue a local change to break a block but observe that nothing has changed in the data.
		AbsoluteLocation changeLocation1 = new AbsoluteLocation(0, 0, 0);
		currentTimeMillis += 100L;
		EndBreakBlockChange interrupted = new EndBreakBlockChange(changeLocation1, ItemRegistry.STONE.number());
		long commitNumber = projector.applyLocalChange(interrupted, currentTimeMillis);
		Assert.assertEquals(1, commitNumber);
		Assert.assertEquals(1, listener.changeCount);
		
		// Allow a small amount of time to pass, cancel the previous, and put in the updated change.
		AbsoluteLocation changeLocation2 = new AbsoluteLocation(0, 0, 1);
		currentTimeMillis += 50L;
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation2, ItemRegistry.STONE.number());
		long cancelledCommit = projector.cancelCurrentActivity();
		Assert.assertEquals(commitNumber, cancelledCommit);
		commitNumber = projector.applyLocalChange(longRunningChange, currentTimeMillis);
		Assert.assertEquals(2, commitNumber);
		Assert.assertEquals(1, listener.changeCount);
		
		// Check what happens if we commit all of this - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 100L;
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(entityId, new LinkedList<>(List.of(interrupted
						, new EntityChangeCancel()
						, longRunningChange
				)))
				, List.of(new BreakBlockMutation(changeLocation2, BlockAspect.STONE))
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumber
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		// They should only see the later change modify the state.
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation1.getBlockAddress()));
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation2.getBlockAddress()));
	}

	@Test
	public void multiPhaseServerInvalidation()
	{
		// Test that we can apply a multi-phase change, observe it complete, and observe its side-effects being reverted by a conflicting server change.
		CountingListener listener = new CountingListener();
		int entityId = 0;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(entityId))
				, List.of(cuboid)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.changeCount);
		
		// Enqueue a local change to break a block but observe that nothing has changed in the data.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		currentTimeMillis += 100L;
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE.number());
		long commitNumber = projector.applyLocalChange(longRunningChange, currentTimeMillis);
		Assert.assertEquals(1, commitNumber);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Allow time to pass in the local environment and observe that the change has happened.
		currentTimeMillis += 200L;
		projector.checkCurrentActivity(currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Check what happens when the server sends us a change which already broke that block.
		currentTimeMillis += 100L;
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, List.of(new BreakBlockMutation(changeLocation, BlockAspect.STONE))
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumber
				, currentTimeMillis
		);
		// We should see this invalidated but the change from the server be applied.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Make sure nothing goes wrong when time advances.
		currentTimeMillis += 100L;
		projector.checkCurrentActivity(currentTimeMillis);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
	}

	@Test
	public void multiPhaseServerSheerDone()
	{
		// Test that we can apply a multi-phase change, observe it complete, and observe it be correctly merged with server changes when sheered such that the phase1 and phase2 arrive in 2 batches.
		// (in this case, we assume we locally applied phase2 before we see the partial commit)
		CountingListener listener = new CountingListener();
		int entityId = 0;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(entityId))
				, List.of(cuboid)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.changeCount);
		
		// Enqueue a local change to break a block but observe that nothing has changed in the data.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		currentTimeMillis += 100L;
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE.number());
		long commitNumber = projector.applyLocalChange(longRunningChange, currentTimeMillis);
		Assert.assertEquals(1, commitNumber);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Allow time to pass in the local environment and observe that the change has happened.
		currentTimeMillis += 200L;
		projector.checkCurrentActivity(currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Check what happens if we commit all of this - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 50L;
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumber
				, currentTimeMillis
		);
		// We have an orphan but nothing else in the speculative list.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Check what happens if we commit the rest of the faked-up updates from the server
		currentTimeMillis += 100L;
		speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(entityId, new LinkedList<>(List.of(longRunningChange)))
				, List.of(new BreakBlockMutation(changeLocation, BlockAspect.STONE))
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumber
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Make sure nothing goes wrong when time advances.
		currentTimeMillis += 100L;
		projector.checkCurrentActivity(currentTimeMillis);
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
	}

	@Test
	public void multiPhaseServerSheerNotDone()
	{
		// Test that we can apply a multi-phase change, observe it complete, and observe it be correctly merged with server changes when sheered such that the phase1 and phase2 arrive in 2 batches.
		// (in this case, we assume that the local phase2 hasn't yet been applied before we see the partial commit)
		CountingListener listener = new CountingListener();
		int entityId = 0;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(entityId))
				, List.of(cuboid)
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.changeCount);
		
		// Enqueue a local change to break a block but observe that nothing has changed in the data.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		currentTimeMillis += 100L;
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE.number());
		long commitNumber = projector.applyLocalChange(longRunningChange, currentTimeMillis);
		Assert.assertEquals(1, commitNumber);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// See what happens when the server responds with phase1 before we apply phase2.
		// Check what happens if we commit all of this - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 50L;
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumber
				, currentTimeMillis
		);
		// We have an orphan but nothing else in the speculative list.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Allow time to pass in the local environment and observe that the change has happened.
		currentTimeMillis += 200L;
		projector.checkCurrentActivity(currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Check what happens if we commit the rest of the faked-up updates from the server
		currentTimeMillis += 100L;
		speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(entityId, new LinkedList<>(List.of(longRunningChange)))
				, List.of(new BreakBlockMutation(changeLocation, BlockAspect.STONE))
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumber
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Make sure nothing goes wrong when time advances.
		currentTimeMillis += 100L;
		projector.checkCurrentActivity(currentTimeMillis);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
	}

	@Test
	public void movePartialRejection()
	{
		// We want to test that 2 move changes, where the first is rejected by the server, the second is rejected locally.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Apply the 2 steps of the move, locally.
		// (we size these to 0.2 in each direction since 0.4 is the limit for one tick)
		EntityLocation startLocation = listener.lastEntityStates.get(0).location();
		EntityLocation midStep = new EntityLocation(0.2f, 0.2f, 0.0f);
		EntityLocation lastStep = new EntityLocation(0.4f, 0.4f, 0.0f);
		IEntityChange move1 = new EntityChangeMove(startLocation, midStep);
		IEntityChange move2 = new EntityChangeMove(midStep, lastStep);
		long commit1 = projector.applyLocalChange(move1, 1L);
		projector.checkCurrentActivity(1000L);
		long commit2 = projector.applyLocalChange(move2, 1001L);
		projector.checkCurrentActivity(2000L);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(2L, commit2);
		
		// We should see the entity moved to its speculative location.
		Assert.assertEquals(lastStep, listener.lastEntityStates.get(0).location());
		
		// Now, absorb a change from the server where neither change is present but the first has been considered.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		
		// We should now see 0 speculative commits and the entity should still be where it started.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(startLocation, listener.lastEntityStates.get(0).location());
	}

	@Test
	public void craftPlanks()
	{
		// Test the in-inventory crafting operation.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Load some items into the inventory.
		EntityChangeAcceptItems load = new EntityChangeAcceptItems(new Items(ItemRegistry.LOG, 2));
		long commit1 = projector.applyLocalChange(load, 0L);
		projector.checkCurrentActivity(1000L);
		Assert.assertEquals(1L, commit1);
		
		EntityChangeCraft craft = new EntityChangeCraft(Craft.LOG_TO_PLANKS);
		long commit2 = projector.applyLocalChange(craft, 1000L);
		projector.checkCurrentActivity(2000L);
		Assert.assertEquals(2L, commit2);
		
		// Check the inventory to see the craft completed.
		Inventory inv = listener.lastEntityStates.get(0).inventory();
		Assert.assertEquals(1, inv.items.get(ItemRegistry.LOG).count());
		Assert.assertEquals(2, inv.items.get(ItemRegistry.PLANK).count());
		
		
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(0, new LinkedList<>(List.of(load, craft)))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, 3000L
		);
		Assert.assertEquals(0, speculativeCount);
	}

	@Test
	public void inProgressOperation()
	{
		// Test that we can check that an in-progress operation is still running.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(EntityActionValidator.buildDefaultEntity(0))
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Move the entity and verify that the move is in-progress until we advance the clock.
		EntityLocation startLocation = listener.lastEntityStates.get(0).location();
		EntityLocation target = new EntityLocation(0.2f, 0.2f, 0.0f);
		IEntityChange move = new EntityChangeMove(startLocation, target);
		long commit1 = projector.applyLocalChange(move, 1L);
		Assert.assertEquals(1L, commit1);
		
		Assert.assertTrue(projector.checkCurrentActivity(51L));
		Assert.assertEquals(startLocation, listener.lastEntityStates.get(0).location());
		Assert.assertFalse(projector.checkCurrentActivity(101L));
		Assert.assertEquals(target, listener.lastEntityStates.get(0).location());
	}


	private int _countBlocks(IReadOnlyCuboidData cuboid, short blockType)
	{
		int count = 0;
		for (int x = 0; x < 32; ++x)
		{
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					short value = cuboid.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)x, (byte)y, (byte)z));
					if (blockType == value)
					{
						count += 1;
					}
				}
			}
		}
		return count;
	}

	private void _checkInventories(CountingListener listener, int encumbrance, Item stoneItem, AbsoluteLocation block1, AbsoluteLocation block2)
	{
		Inventory inventory1 = listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, block1.getBlockAddress());
		Assert.assertEquals(1 * encumbrance, inventory1.currentEncumbrance);
		Assert.assertEquals(1, inventory1.items.size());
		Assert.assertEquals(1, inventory1.items.get(stoneItem).count());
		Inventory inventory2 = listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, block2.getBlockAddress());
		Assert.assertEquals(3 * encumbrance, inventory2.currentEncumbrance);
		Assert.assertEquals(1, inventory1.items.size());
		Assert.assertEquals(3, inventory2.items.get(stoneItem).count());
	}

	private static class CountingListener implements SpeculativeProjection.IProjectionListener
	{
		public int loadCount = 0;
		public int changeCount = 0;
		public int unloadCount = 0;
		public IReadOnlyCuboidData lastData = null;
		public Map<Integer, Entity> lastEntityStates = new HashMap<>();
		
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid)
		{
			this.loadCount += 1;
			this.lastData = cuboid;
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid)
		{
			this.changeCount += 1;
			this.lastData = cuboid;
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			this.unloadCount += 1;
		}
		@Override
		public void entityDidLoad(Entity entity)
		{
			Entity old = this.lastEntityStates.put(entity.id(), entity);
			Assert.assertNull(old);
		}
		@Override
		public void entityDidChange(Entity entity)
		{
			Entity old = this.lastEntityStates.put(entity.id(), entity);
			Assert.assertNotNull(old);
		}
		@Override
		public void entityDidUnload(int id)
		{
			Entity old = this.lastEntityStates.remove(id);
			Assert.assertNull(old);
		}
	}
}
