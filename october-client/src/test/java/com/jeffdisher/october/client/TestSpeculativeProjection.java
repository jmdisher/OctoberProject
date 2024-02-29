package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.logic.EntityChangeReceiveItem;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.EntityChangeAcceptItems;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
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
		Assert.assertEquals(0, listener.changeCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Apply a few local mutations.
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 0), BlockAspect.AIR, BlockAspect.STONE);
		IMutationEntity lone1 = new EntityChangeMutation(mutation1);
		IMutationBlock mutation2 = new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 1), BlockAspect.AIR, BlockAspect.STONE);
		IMutationEntity lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		long commit2 = projector.applyLocalChange(lone2, 1L);
		List<IMutationBlock> mutationsToCommit = new ArrayList<>();
		List<IMutationEntity> localEntityChangesToCommit = new LinkedList<>();
		long[] commitNumbers = new long[5];
		for (int i = 0; i < commitNumbers.length; ++i)
		{
			AbsoluteLocation location = new AbsoluteLocation(i, 0, 0);
			IMutationBlock mutation = new ReplaceBlockMutation(location, BlockAspect.AIR, BlockAspect.STONE);
			IMutationEntity entityChange = new EntityChangeMutation(mutation);
			localEntityChangesToCommit.add(entityChange);
			mutationsToCommit.add(mutation);
			commitNumbers[i] = projector.applyLocalChange(entityChange, 1L);
		}
		Assert.assertEquals(7, listener.changeCount);
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
		Assert.assertEquals(7 + 1, listener.changeCount);
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
		Assert.assertEquals(7 + 1 + 1, listener.changeCount);
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
		Assert.assertEquals(7 + 1 + 1 + 1, listener.changeCount);
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
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply a few local mutations.
		IMutationBlock mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), BlockAspect.AIR, BlockAspect.STONE);
		IMutationEntity lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), BlockAspect.AIR, BlockAspect.STONE);
		IMutationEntity lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		long commit1 = projector.applyLocalChange(lone1, 1L);
		Assert.assertEquals(2, listener.changeCount);
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
		Assert.assertEquals(2 + 1, listener.changeCount);
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
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply a few local mutations.
		IMutationBlock mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), BlockAspect.AIR, BlockAspect.STONE);
		IMutationEntity lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), BlockAspect.AIR, BlockAspect.STONE);
		IMutationEntity lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		long commit1 = projector.applyLocalChange(lone1, 1L);
		Assert.assertEquals(2, listener.changeCount);
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
		// We will still see 2 elements in the speculative list since EntityChangeMutation always claims to have applied.  Hence, we will only remove them when the commit level passes them.
		Assert.assertEquals(2, speculativeCount);
		// We see another 2 changes due to the reverses (that is, when applying changes from the server, they will be different instances compared to what WAS in the speculative projection).
		Assert.assertEquals(2 + 2, listener.changeCount);
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
		// This time, we will only add a +1 since the previous commit of mutation0 meant that our speculative change would have failed to apply on top so it ISN'T reverted here.
		// That is, the only change from the previous commit action is the application of mutation1.
		Assert.assertEquals(2 + 2 + 1, listener.changeCount);
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
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply a few local mutations.
		IMutationBlock mutation0 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 5), 2);
		IMutationEntity lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 37), 2);
		IMutationEntity lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		// Note that shockwave doesn't change blocks.
		Assert.assertEquals(0, listener.changeCount);
		
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
		Assert.assertEquals(0, listener.changeCount);
		
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
		Assert.assertEquals(0, listener.changeCount);
		
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
		Assert.assertEquals(0, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
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
		Assert.assertEquals(0, listener.changeCount);
		
		// Try to drop a few items.
		int encumbrance = 2;
		Item stoneItem = ItemRegistry.STONE;
		AbsoluteLocation block1 = new AbsoluteLocation(1, 1, 1);
		IMutationBlock mutation1 = new DropItemMutation(block1, stoneItem, 1);
		IMutationEntity lone1 = new EntityChangeMutation(mutation1);
		AbsoluteLocation block2 = new AbsoluteLocation(3, 3, 3);
		IMutationBlock mutation2 = new DropItemMutation(block2, stoneItem, 3);
		IMutationEntity lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		long commit2 = projector.applyLocalChange(lone2, 1L);
		Assert.assertEquals(2, listener.changeCount);
		
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
		Assert.assertEquals(2 + 1, listener.changeCount);
		
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
		Assert.assertEquals(2 + 1 + 1, listener.changeCount);
		
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
		Inventory startInventory = Inventory.start(10).add(ItemRegistry.STONE, 2).finish();
		projector.applyChangesForServerTick(0L
				, List.of(new Entity(0, EntityActionValidator.DEFAULT_LOCATION, 0.0f, EntityActionValidator.DEFAULT_VOLUME, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED, startInventory, null, null)
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
		IMutationEntity send = new EntityChangeSendItem(1, ItemRegistry.STONE);
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
		// Test that we can use the block breaking change as 2 changes, seeing the change of state applied by each.
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
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply the first stage of the change and observe that only the damage changes (done by cuboid mutation).
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		currentTimeMillis += 100L;
		EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(changeLocation, (short) 100);
		long commit1 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(BlockAspect.STONE, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 1000, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		
		// Allow time to pass in the local environment apply the second stage of the change.
		currentTimeMillis += 200L;
		long commit2 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(2, commit2);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNotNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		
		// If we commit the first part of this change, we should still see the same result - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 100L;
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(entityId, new LinkedList<>(List.of(blockBreak)))
				, List.of(new MutationBlockIncrementalBreak(changeLocation, (short) 1000))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNotNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		
		// Commit the second part and make sure the change is still there.
		currentTimeMillis += 100L;
		speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, Map.of(entityId, new LinkedList<>(List.of(blockBreak)))
				, List.of(new MutationBlockIncrementalBreak(changeLocation, (short) 1000))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(BlockAspect.AIR, listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNotNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
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
		// (note that 0.4 is the limit for one tick)
		EntityLocation startLocation = listener.lastEntityStates.get(0).location();
		EntityLocation midStep = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation lastStep = new EntityLocation(0.8f, 0.0f, 0.0f);
		IMutationEntity move1 = new EntityChangeMove(startLocation, 0L, 0.4f, 0.0f);
		IMutationEntity move2 = new EntityChangeMove(midStep, 0L, 0.4f, 0.0f);
		long commit1 = projector.applyLocalChange(move1, 1L);
		long commit2 = projector.applyLocalChange(move2, 1001L);
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
		Assert.assertEquals(1L, commit1);
		
		// We will handle this as a single crafting operation to test the simpler case.
		EntityChangeCraft craft = new EntityChangeCraft(Craft.LOG_TO_PLANKS, Craft.LOG_TO_PLANKS.millisPerCraft);
		long commit2 = projector.applyLocalChange(craft, 1000L);
		Assert.assertEquals(2L, commit2);
		// Verify that we finished the craft (no longer in progress).
		Assert.assertNull(listener.lastEntityStates.get(0).localCraftOperation());
		
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
	public void craftBricksSelection()
	{
		// Test the in-inventory crafting operation.
		CountingListener listener = new CountingListener();
		// Start the entity with some stone and with them selected.
		Inventory start = Inventory.start(InventoryAspect.CAPACITY_AIR).add(ItemRegistry.STONE, 1).finish();
		Entity entity = new Entity(0, EntityActionValidator.DEFAULT_LOCATION, 0.0f, EntityActionValidator.DEFAULT_VOLUME, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED, start, ItemRegistry.STONE, null);
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		projector.applyChangesForServerTick(0L
				, List.of(entity)
				, Collections.emptyList()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(0, listener.entityChangeCount);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		Assert.assertEquals(ItemRegistry.STONE, listener.lastEntityStates.get(0).selectedItem());
		
		// Do the craft and observe it takes multiple actions with no current activity.
		EntityChangeCraft craft = new EntityChangeCraft(Craft.STONE_TO_STONE_BRICK, 1000L);
		long commit1 = projector.applyLocalChange(craft, 1000L);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(1, listener.entityChangeCount);
		Assert.assertNotNull(listener.lastEntityStates.get(0).localCraftOperation());
		
		craft = new EntityChangeCraft(Craft.STONE_TO_STONE_BRICK, 1000L);
		long commit2 = projector.applyLocalChange(craft, 1000L);
		Assert.assertEquals(2L, commit2);
		Assert.assertEquals(2, listener.entityChangeCount);
		Assert.assertNull(listener.lastEntityStates.get(0).localCraftOperation());
		
		// Check the inventory to see the craft completed.
		Inventory inv = listener.lastEntityStates.get(0).inventory();
		Assert.assertEquals(0, inv.getCount(ItemRegistry.STONE));
		Assert.assertEquals(1, inv.getCount(ItemRegistry.STONE_BRICK));
		Assert.assertNull(listener.lastEntityStates.get(0).selectedItem());
	}

	@Test
	public void placeBlockTwice()
	{
		// Make sure that the speculative projection will prevent us from placing the same block down twice.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(0, listener);
		Inventory inventory = Inventory.start(InventoryAspect.CAPACITY_PLAYER).add(ItemRegistry.STONE, 2).finish();
		int entityId = 0;
		Entity entity = new Entity(entityId
				, EntityActionValidator.DEFAULT_LOCATION
				, 0.0f
				, EntityActionValidator.DEFAULT_VOLUME
				, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED
				, inventory
				, ItemRegistry.STONE
				, null
		);
		projector.applyChangesForServerTick(0L
				, List.of(entity)
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Apply the local change.
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location);
		long commit1 = projector.applyLocalChange(place, 1L);
		Assert.assertEquals(1, commit1);
		// (verify that it fails if we try to run it again.
		long commit2 = projector.applyLocalChange(place, 1L);
		Assert.assertEquals(0, commit2);
	}

	@Test
	public void placeAndUseTable()
	{
		// Test the in-inventory crafting operation.
		CountingListener listener = new CountingListener();
		int localEntityId = 0;
		SpeculativeProjection projector = new SpeculativeProjection(localEntityId, listener);
		Entity entity = new Entity(localEntityId
				, EntityActionValidator.DEFAULT_LOCATION
				, 0.0f
				, EntityActionValidator.DEFAULT_VOLUME
				, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED
				, Inventory.start(InventoryAspect.CAPACITY_PLAYER).add(ItemRegistry.CRAFTING_TABLE, 1).add(ItemRegistry.STONE, 2).finish()
				, ItemRegistry.CRAFTING_TABLE
				, null
		);
		projector.applyChangesForServerTick(0L
				, List.of(entity)
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR)
						, CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Place the crafting table.
		long currentTimeMillis = 1000L;
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location);
		long commit1 = projector.applyLocalChange(place, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		
		// Store the stones in the inventory.
		currentTimeMillis += 1000L;
		MutationEntityPushItems push = new MutationEntityPushItems(location, new Items(ItemRegistry.STONE, 2));
		long commit2 = projector.applyLocalChange(push, currentTimeMillis);
		Assert.assertEquals(2L, commit2);
		
		// Now, craft against the table (it has 10x speed so we will do this in 2 shots).
		currentTimeMillis += 100L;
		EntityChangeCraftInBlock craft = new EntityChangeCraftInBlock(location, Craft.STONE_TO_STONE_BRICK, 100L);
		long commit3 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(3L, commit3);
		
		// Check the block and all of its aspects.
		BlockProxy proxy = new BlockProxy(new BlockAddress((byte)1, (byte)1, (byte)1), listener.lastData);
		Assert.assertEquals(ItemRegistry.CRAFTING_TABLE, proxy.getItem());
		Assert.assertEquals(2, proxy.getInventory().getCount(ItemRegistry.STONE));
		Assert.assertEquals(1000L, proxy.getCrafting().completedMillis());
		
		// Complete the craft and check the proxy.
		currentTimeMillis += 100L;
		craft = new EntityChangeCraftInBlock(location, null, 100L);
		long commit4 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(4L, commit4);
		proxy = new BlockProxy(new BlockAddress((byte)1, (byte)1, (byte)1), listener.lastData);
		Assert.assertEquals(ItemRegistry.CRAFTING_TABLE, proxy.getItem());
		Assert.assertEquals(1, proxy.getInventory().getCount(ItemRegistry.STONE));
		Assert.assertEquals(1, proxy.getInventory().getCount(ItemRegistry.STONE_BRICK));
		Assert.assertNull(proxy.getCrafting());
		
		// Now, break the table and verify that the final inventory state makes sense.
		currentTimeMillis += 200L;
		EntityChangeIncrementalBlockBreak breaking = new EntityChangeIncrementalBlockBreak(location, (short)20);
		long commit5 = projector.applyLocalChange(breaking, currentTimeMillis);
		Assert.assertEquals(5L, commit5);
		proxy = new BlockProxy(new BlockAddress((byte)1, (byte)1, (byte)1), listener.lastData);
		Assert.assertEquals(ItemRegistry.AIR, proxy.getItem());
		Assert.assertEquals(1, proxy.getInventory().getCount(ItemRegistry.STONE));
		Assert.assertEquals(1, proxy.getInventory().getCount(ItemRegistry.STONE_BRICK));
		Assert.assertEquals(1, proxy.getInventory().getCount(ItemRegistry.CRAFTING_TABLE));
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
		public int entityChangeCount = 0;
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
			this.entityChangeCount += 1;
		}
		@Override
		public void entityDidUnload(int id)
		{
			Entity old = this.lastEntityStates.remove(id);
			Assert.assertNull(old);
		}
	}
}
