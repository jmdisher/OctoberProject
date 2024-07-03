package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.EntityChangeAcceptItems;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.EntityMutationWrapper;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySetPartialEntity;
import com.jeffdisher.october.mutations.MutationEntityStoreToInventory;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.mutations.ReplaceBlockMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestSpeculativeProjection
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
	public void basicApplyMatching()
	{
		// We want to test that adding a few mutations as speculative, but then adding them as "committed" causes no problem.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(CuboidData.mutableClone(cuboid))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Apply a few local mutations.
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 0), ENV.items.AIR.number(), ENV.items.STONE.number());
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		IMutationBlock mutation2 = new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 1), ENV.items.AIR.number(), ENV.items.STONE.number());
		EntityChangeMutation lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		long commit2 = projector.applyLocalChange(lone2, 1L);
		List<MutationBlockSetBlock> mutationsToCommit = new ArrayList<>();
		List<IEntityUpdate> localEntityChangesToCommit = new LinkedList<>();
		long[] commitNumbers = new long[5];
		for (int i = 0; i < commitNumbers.length; ++i)
		{
			AbsoluteLocation location = new AbsoluteLocation(i, 0, 0);
			IMutationBlock mutation = new ReplaceBlockMutation(location, ENV.items.AIR.number(), ENV.items.STONE.number());
			EntityChangeMutation entityChange = new EntityChangeMutation(mutation);
			localEntityChangesToCommit.add(new EntityMutationWrapper(entityChange));
			mutationsToCommit.add(FakeBlockUpdate.applyUpdate(cuboid, mutation));
			commitNumbers[i] = projector.applyLocalChange(entityChange, 1L);
		}
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Commit the first 2, one at a time, and then the last ones at the same time.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(lone1))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		// Only the changes are in the speculative list:  We passed in 7 and committed 1.
		Assert.assertEquals(6, speculativeCount);
		Assert.assertEquals(7 + 1, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(lone2))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid, mutation2))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, 1L
		);
		// 5 changes left.
		Assert.assertEquals(5, speculativeCount);
		Assert.assertEquals(7 + 1 + 1, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, localEntityChangesToCommit
				, Map.of()
				, mutationsToCommit
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumbers[commitNumbers.length - 1]
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(7 + 1 + 1 + 1, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Now, unload.
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(0, listener.changeCount);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, List.of()
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
		IMutationBlock mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), ENV.items.AIR.number(), ENV.items.STONE.number());
		EntityChangeMutation lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), ENV.items.AIR.number(), ENV.items.STONE.number());
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		Assert.assertEquals(1, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		long commit1 = projector.applyLocalChange(lone1, 1L);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Commit the other one.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(lone0))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid0, mutation0))
				, Collections.emptyList()
				, List.of(address1)
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2 + 1, listener.changeCount);
		Assert.assertEquals(1, listener.unloadCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Unload the other.
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(CuboidData.mutableClone(cuboid0), CuboidData.mutableClone(cuboid1))
				, List.of()
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
		IMutationBlock mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), ENV.items.AIR.number(), ENV.items.STONE.number());
		EntityChangeMutation lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), ENV.items.AIR.number(), ENV.items.STONE.number());
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		Assert.assertEquals(1, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		long commit1 = projector.applyLocalChange(lone1, 1L);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(lone0))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid0, mutation0))
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		// We will still see 2 elements in the speculative list since EntityChangeMutation always claims to have applied.  Hence, we will only remove them when the commit level passes them.
		Assert.assertEquals(2, speculativeCount);
		// We see another 2 changes due to the reverses (that is, when applying changes from the server, they will be different instances compared to what WAS in the speculative projection).
		Assert.assertEquals(2 + 2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Commit the other one normally.
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(lone1))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid1, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		// This time, we will only add a +1 since the previous commit of mutation0 meant that our speculative change would have failed to apply on top so it ISN'T reverted here.
		// That is, the only change from the previous commit action is the application of mutation1.
		Assert.assertEquals(2 + 2 + 1, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(CuboidData.mutableClone(cuboid0), CuboidData.mutableClone(cuboid1))
				, List.of()
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
		EntityChangeMutation lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 37), 2);
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, 1L);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		// Note that shockwave doesn't change blocks.
		Assert.assertEquals(0, listener.changeCount);
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(lone0))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid0, mutation0))
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
				, List.of(new EntityMutationWrapper(lone1))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid1, mutation1))
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
				, List.of()
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
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(0, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Create and add an empty cuboid.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		projector.applyChangesForServerTick(0L
				, Collections.emptyList()
				, List.of(cuboid)
				, List.of()
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
		Item stoneItem = ENV.items.STONE;
		AbsoluteLocation block1 = new AbsoluteLocation(1, 1, 1);
		IMutationBlock mutation1 = new DropItemMutation(block1, stoneItem, 1);
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		AbsoluteLocation block2 = new AbsoluteLocation(3, 3, 3);
		IMutationBlock mutation2 = new DropItemMutation(block2, stoneItem, 3);
		EntityChangeMutation lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, 1L);
		long commit2 = projector.applyLocalChange(lone2, 1L);
		Assert.assertEquals(2, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Commit the first, then the second, making sure that things make sense at every point.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(lone1))
				, Map.of()
				, Collections.emptyList()
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
				, List.of(new EntityMutationWrapper(lone2))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid, mutation1))
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
				, List.of()
				, Collections.emptyMap()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid, mutation2))
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
		int entityId1 = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId1, listener);
		
		// We need 2 entities for this but we will give one some items.
		int entityId2 = 2;
		MutableEntity mutable = MutableEntity.create(entityId1);
		mutable.newInventory.addAllItems(ENV.items.STONE, 2);
		projector.setThisEntity(mutable.freeze());
		projector.applyChangesForServerTick(0L
				, List.of(PartialEntity.fromEntity(MutableEntity.create(entityId2).freeze()))
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertNotNull(listener.otherEntityStates.get(entityId2));
		PartialEntity otherEntity = listener.otherEntityStates.get(entityId2);
		
		// Try to pass the items to the other entity.
		EntityChangeSendItem send = new EntityChangeSendItem(entityId2, ENV.items.STONE);
		long commit1 = projector.applyLocalChange(send, 1L);
		
		// Check the values.
		Assert.assertEquals(0, listener.thisEntityState.inventory().sortedKeys().size());
		// Speculative projection no longer runs follow-up changes on entities, only cuboids, so this should be unchanged, even though we reference it.
		Assert.assertTrue(otherEntity == listener.otherEntityStates.get(entityId2));
		
		// Commit this and make sure the values are still correct.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(send))
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		// NOTE:  The other half of the transfer is going to be run against the second entity, on the server, but we
		// just see the partial update, on the client (if even that - the server may realize there is no change).
		speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of(entityId2, new LinkedList<>(List.of(new MutationEntitySetPartialEntity(otherEntity))))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		
		Assert.assertEquals(0, listener.thisEntityState.inventory().sortedKeys().size());
		// This won't change the instance since we will realize that they are the same.
		Assert.assertTrue(otherEntity == listener.otherEntityStates.get(entityId2));
	}

	@Test
	public void multiPhaseFullCommit()
	{
		// Test that we can use the block breaking change as 2 changes, seeing the change of state applied by each.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		long currentTimeMillis = 1L;
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(cuboid)
				, List.of()
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
		EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(changeLocation, (short) 500);
		long commit1 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(ENV.items.STONE.number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 500, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		
		// Allow time to pass in the local environment apply the second stage of the change.
		currentTimeMillis += 200L;
		long commit2 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(2, commit2);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(ENV.items.AIR.number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		// We should see no inventory in the block but the item should be in the entity's inventory.
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
		
		// If we commit the first part of this change, we should still see the same result - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 100L;
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(blockBreak))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid, new MutationBlockIncrementalBreak(changeLocation, (short) 500, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(ENV.items.AIR.number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
		
		// Commit the second part and make sure the change is still there.
		currentTimeMillis += 100L;
		speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(blockBreak))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid, new MutationBlockIncrementalBreak(changeLocation, (short) 1000, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(ENV.items.AIR.number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
	}

	@Test
	public void movePartialRejection()
	{
		// We want to test that 2 move changes, where the first is rejected by the server, but the second is still applied locally.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Apply the 2 steps of the move, locally.
		// (note that 0.4 is the limit for one tick)
		EntityLocation midStep = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation lastStep = new EntityLocation(0.8f, 0.0f, 0.0f);
		float speed = EntityConstants.SPEED_PLAYER;
		EntityChangeMove<IMutablePlayerEntity> move1 = new EntityChangeMove<>(speed, 0.4f, 0.0f);
		EntityChangeMove<IMutablePlayerEntity> move2 = new EntityChangeMove<>(speed, 0.4f, 0.0f);
		long commit1 = projector.applyLocalChange(move1, 1L);
		long commit2 = projector.applyLocalChange(move2, 1001L);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(2L, commit2);
		
		// We should see the entity moved to its speculative location.
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		
		// Now, absorb a change from the server where neither change is present but the first has been considered.
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		
		// We should now see 1 speculative commit and the entity should have moved over by that step, alone.
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(midStep, listener.thisEntityState.location());
	}

	@Test
	public void craftPlanks()
	{
		// Test the in-inventory crafting operation.
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(MutableEntity.create(entityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Load some items into the inventory.
		EntityChangeAcceptItems load = new EntityChangeAcceptItems(new Items(ENV.items.LOG, 2));
		long commit1 = projector.applyLocalChange(load, 0L);
		Assert.assertEquals(1L, commit1);
		
		// We will handle this as a single crafting operation to test the simpler case.
		EntityChangeCraft craft = new EntityChangeCraft(logToPlanks, logToPlanks.millisPerCraft);
		long commit2 = projector.applyLocalChange(craft, 1000L);
		Assert.assertEquals(2L, commit2);
		// Verify that we finished the craft (no longer in progress).
		Assert.assertNull(listener.thisEntityState.localCraftOperation());
		
		// Check the inventory to see the craft completed.
		Inventory inv = listener.thisEntityState.inventory();
		Assert.assertEquals(1, inv.getCount(ENV.items.LOG));
		Assert.assertEquals(2, inv.getCount(ENV.items.PLANK));
		
		
		int speculativeCount = projector.applyChangesForServerTick(1L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(load), new EntityMutationWrapper(craft))
				, Map.of()
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
		Craft stoneToStoneBrick = ENV.crafting.getCraftById("op.stone_to_stone_brick");
		CountingListener listener = new CountingListener();
		// Start the entity with some stone and with them selected.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.create(entityId);
		mutable.newInventory.addAllItems(ENV.items.STONE, 1);
		int stoneKey = mutable.newInventory.getIdOfStackableType(ENV.items.STONE);
		mutable.setSelectedKey(stoneKey);
		Entity entity = mutable.freeze();
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertEquals(0, listener.entityChangeCount);
		Assert.assertNotNull(listener.thisEntityState);
		entity = listener.thisEntityState;
		Assert.assertEquals(stoneKey, entity.hotbarItems()[entity.hotbarIndex()]);
		
		// Do the craft and observe it takes multiple actions with no current activity.
		EntityChangeCraft craft = new EntityChangeCraft(stoneToStoneBrick, 1000L);
		long commit1 = projector.applyLocalChange(craft, 1000L);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(1, listener.entityChangeCount);
		Assert.assertNotNull(listener.thisEntityState.localCraftOperation());
		
		craft = new EntityChangeCraft(stoneToStoneBrick, 1000L);
		long commit2 = projector.applyLocalChange(craft, 1000L);
		Assert.assertEquals(2L, commit2);
		Assert.assertEquals(2, listener.entityChangeCount);
		Assert.assertNull(listener.thisEntityState.localCraftOperation());
		
		// Check the inventory to see the craft completed.
		Inventory inv = listener.thisEntityState.inventory();
		Assert.assertEquals(0, inv.getCount(ENV.items.STONE));
		Assert.assertEquals(1, inv.getCount(ENV.items.STONE_BRICK));
		entity = listener.thisEntityState;
		Assert.assertEquals(Entity.NO_SELECTION, entity.hotbarItems()[entity.hotbarIndex()]);
	}

	@Test
	public void placeBlockTwice()
	{
		// Make sure that the speculative projection will prevent us from placing the same block down twice.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		MutableEntity mutable = MutableEntity.create(entityId);
		mutable.newInventory.addAllItems(ENV.items.STONE, 2);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(ENV.items.STONE));
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, ENV.items.STONE.number()));
		
		// Apply the local change.
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
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
		Craft stoneToStoneBrick = ENV.crafting.getCraftById("op.stone_to_stone_brick");
		CountingListener listener = new CountingListener();
		int localEntityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(localEntityId, listener);
		MutableEntity mutable = MutableEntity.create(localEntityId);
		mutable.newInventory.addAllItems(ENV.items.CRAFTING_TABLE, 1);
		mutable.newInventory.addAllItems(ENV.items.STONE, 2);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(ENV.items.CRAFTING_TABLE));
		int stoneKey = mutable.newInventory.getIdOfStackableType(ENV.items.STONE);
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR)
						, CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Place the crafting table.
		long currentTimeMillis = 1000L;
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 0);
		BlockAddress blockLocation = location.getBlockAddress();
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
		long commit1 = projector.applyLocalChange(place, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		
		// Store the stones in the inventory.
		currentTimeMillis += 1000L;
		MutationEntityPushItems push = new MutationEntityPushItems(location, stoneKey, 2, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit2 = projector.applyLocalChange(push, currentTimeMillis);
		Assert.assertEquals(2L, commit2);
		
		// Now, craft against the table (it has 10x speed so we will do this in 2 shots).
		currentTimeMillis += 100L;
		EntityChangeCraftInBlock craft = new EntityChangeCraftInBlock(location, stoneToStoneBrick, 100L);
		long commit3 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(3L, commit3);
		
		// Check the block and all of its aspects.
		Block craftingTable = ENV.blocks.fromItem(ENV.items.getItemById("op.crafting_table"));
		BlockProxy proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(craftingTable, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().getCount(ENV.items.STONE));
		Assert.assertEquals(1000L, proxy.getCrafting().completedMillis());
		
		// Complete the craft and check the proxy.
		currentTimeMillis += 100L;
		craft = new EntityChangeCraftInBlock(location, null, 100L);
		long commit4 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(4L, commit4);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(craftingTable, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.STONE));
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.STONE_BRICK));
		Assert.assertNull(proxy.getCrafting());
		
		// Now, break the table and verify that the final inventory state makes sense.
		// We expect the table inventory to spill into the block but the table to end up in the entity's inventory.
		currentTimeMillis += 200L;
		EntityChangeIncrementalBlockBreak breaking = new EntityChangeIncrementalBlockBreak(location, (short)100);
		long commit5 = projector.applyLocalChange(breaking, currentTimeMillis);
		Assert.assertEquals(5L, commit5);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().sortedKeys().size());
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.STONE));
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.STONE_BRICK));
		
		Inventory entityInventory = listener.thisEntityState.inventory();
		Assert.assertEquals(1, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(ENV.items.CRAFTING_TABLE));
	}

	@Test
	public void craftFurnaceFailure()
	{
		// Test the in-inventory crafting operation.
		Craft stoneBricksToFurnace = ENV.crafting.getCraftById("op.stone_bricks_to_furnace");
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		MutableEntity mutable = MutableEntity.create(entityId);
		mutable.newInventory.addAllItems(ENV.items.STONE_BRICK, 4);
		Inventory inventory = Inventory.start(StationRegistry.CAPACITY_PLAYER).addStackable(ENV.items.STONE_BRICK, 4).finish();
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(0L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Verify that this craft should be valid for the inventory.
		Assert.assertTrue(CraftAspect.canApply(stoneBricksToFurnace, inventory));
		
		// But verify that it fails when applied to the entity, directly (as it isn't "trivial").
		EntityChangeCraft craft = new EntityChangeCraft(stoneBricksToFurnace, 100L);
		long commit = projector.applyLocalChange(craft, 1000L);
		// This should fail to apply.
		Assert.assertEquals(0, commit);
		// There should be no active operation.
		Assert.assertNull(listener.thisEntityState.localCraftOperation());
	}

	@Test
	public void pickUpTwice()
	{
		// Create a cuboid with a single item on the ground and try to pick it up twice, showing that the projection is consistent at all points and doesn't duplicate an item.
		// Test the in-inventory crafting operation.
		CountingListener listener = new CountingListener();
		int localEntityId = 1;
		long currentTimeMillis = 1000L;
		SpeculativeProjection projector = new SpeculativeProjection(localEntityId, listener);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress block = new BlockAddress((byte)0, (byte)0, (byte)0);
		Inventory inv = Inventory.start(10).addStackable(ENV.items.STONE, 1).finish();
		int stoneKey = inv.getIdOfStackableType(ENV.items.STONE);
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, block, inv);
		projector.setThisEntity(MutableEntity.create(localEntityId).freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(cuboid)
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(0, listener.thisEntityState.inventory().currentEncumbrance);
		
		// Issue the command to pick up the item.
		AbsoluteLocation location = new AbsoluteLocation(0, 0, 0);
		int blockInventoryKey = stoneKey;
		int countRequested = 1;
		MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(location, blockInventoryKey, countRequested, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit1 = projector.applyLocalChange(request, currentTimeMillis);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(ENV.items.STONE), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		// Apply the commit from the server and show it still works.
		currentTimeMillis += 100L;
		int speculative = projector.applyChangesForServerTick(1L
				, List.of()
				, List.of()
				, List.of(new EntityMutationWrapper(request))
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculative);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(ENV.items.STONE), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		// Now, try to apply it again (this should fail since it won't be able to find the slot to validate the count).
		Assert.assertEquals(0, projector.applyLocalChange(request, currentTimeMillis));
		
		// Apply another 2 ticks, each with the correct part of the multi-step change and verify that the values still match.
		MutationBlockExtractItems extract = new MutationBlockExtractItems(location, blockInventoryKey, countRequested, Inventory.INVENTORY_ASPECT_INVENTORY, localEntityId);
		currentTimeMillis += 100L;
		speculative = projector.applyChangesForServerTick(2L
				, List.of()
				, List.of()
				, List.of(new EntityMutationWrapper(request))
				, Map.of()
				, List.of(FakeBlockUpdate.applyUpdate(cuboid, extract))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculative);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(ENV.items.STONE), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		MutationEntityStoreToInventory store = new MutationEntityStoreToInventory(new Items(ENV.items.STONE, 1), null);
		currentTimeMillis += 100L;
		speculative = projector.applyChangesForServerTick(3L
				, List.of()
				, List.of()
				, List.of(new EntityMutationWrapper(store))
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculative);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(ENV.items.STONE), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		currentTimeMillis += 100L;
		speculative = projector.applyChangesForServerTick(4L
				, List.of()
				, List.of()
				, List.of()
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculative);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(ENV.items.STONE), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
	}

	@Test
	public void placeAndLoadFurnace()
	{
		CountingListener listener = new CountingListener();
		int localEntityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(localEntityId, listener);
		MutableEntity mutable = MutableEntity.create(localEntityId);
		mutable.newInventory.addAllItems(ENV.items.FURNACE, 1);
		mutable.newInventory.addAllItems(ENV.items.PLANK, 1);
		mutable.newInventory.addAllItems(ENV.items.STONE, 1);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(ENV.items.FURNACE));
		int plankKey = mutable.newInventory.getIdOfStackableType(ENV.items.PLANK);
		int stoneKey = mutable.newInventory.getIdOfStackableType(ENV.items.STONE);
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR)
						, CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Place the furnace.
		long currentTimeMillis = 1000L;
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 0);
		BlockAddress blockLocation = location.getBlockAddress();
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
		long commit1 = projector.applyLocalChange(place, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		
		// Verify that storing stone in fuel inventory fails.
		currentTimeMillis += 1000L;
		MutationEntityPushItems pushFail = new MutationEntityPushItems(location, stoneKey, 1, Inventory.INVENTORY_ASPECT_FUEL);
		long commitFail = projector.applyLocalChange(pushFail, currentTimeMillis);
		Assert.assertEquals(0, commitFail);
		
		// Storing the stone in the normal inventory should work.
		currentTimeMillis += 1000L;
		MutationEntityPushItems push = new MutationEntityPushItems(location, stoneKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit2 = projector.applyLocalChange(push, currentTimeMillis);
		Assert.assertEquals(2L, commit2);
		
		// Verify that we can store the planks in the fuel inventory.
		currentTimeMillis += 1000L;
		MutationEntityPushItems pushFuel = new MutationEntityPushItems(location, plankKey, 1, Inventory.INVENTORY_ASPECT_FUEL);
		long commit3 = projector.applyLocalChange(pushFuel, currentTimeMillis);
		Assert.assertEquals(3L, commit3);
		
		// Check the block and all of its aspects.
		Block furnace = ENV.blocks.fromItem(ENV.items.getItemById("op.furnace"));
		BlockProxy proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(furnace, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.STONE));
		Assert.assertEquals(1, proxy.getFuel().fuelInventory().getCount(ENV.items.PLANK));
		
		// Now, break the furnace and verify that the final inventory state makes sense.
		// We expect the table inventory to spill into the block but the table to end up in the entity's inventory.
		currentTimeMillis += 2000L;
		EntityChangeIncrementalBlockBreak breaking = new EntityChangeIncrementalBlockBreak(location, (short)1000);
		long commit4 = projector.applyLocalChange(breaking, currentTimeMillis);
		Assert.assertEquals(4L, commit4);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().sortedKeys().size());
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.STONE));
		Assert.assertEquals(1, proxy.getInventory().getCount(ENV.items.PLANK));
		
		Inventory entityInventory = listener.thisEntityState.inventory();
		Assert.assertEquals(1, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(ENV.items.FURNACE));
	}

	@Test
	public void breakBlockFullInventory()
	{
		// Break a simple block with a full inventory and verify that it drops at your feet.
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		CountingListener listener = new CountingListener();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.create(entityId);
		int stored = mutable.newInventory.addItemsBestEfforts(dirt.item(), 100);
		Assert.assertTrue(stored < 100);
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		AbsoluteLocation targetLocation = new AbsoluteLocation(1, 1, 1);
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress(), dirt.item().number());
		long currentTimeMillis = 1L;
		projector.setThisEntity(mutable.freeze());
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(cuboid)
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Break the block and verify it drops at the feet of the entity, not where the block is.
		currentTimeMillis += 100L;
		EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(targetLocation, (short) 100);
		long commit1 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(ENV.items.AIR.number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()));
		Inventory feetInventory = listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, mutable.newLocation.getBlockLocation().getBlockAddress());
		Assert.assertEquals(1, feetInventory.sortedKeys().size());
		Assert.assertEquals(1, feetInventory.getCount(dirt.item()));
	}

	@Test
	public void moveWhileStarving()
	{
		// This verifies that the follow-up mutations are handled correctly in the SpeculativeProjection.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener);
		
		// Make sure that they are starving.
		MutableEntity mutable = MutableEntity.create(entityId);
		mutable.setFood((byte)0);
		projector.setThisEntity(mutable.freeze());
		
		projector.applyChangesForServerTick(0L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Apply 3 steps, locally.
		// (note that 0.4 is the limit for one tick)
		EntityLocation lastStep = new EntityLocation(1.2f, 0.0f, 0.0f);
		float speed = EntityConstants.SPEED_PLAYER;
		EntityChangeMove<IMutablePlayerEntity> move1 = new EntityChangeMove<>(speed, 0.4f, 0.0f);
		EntityChangeMove<IMutablePlayerEntity> move2 = new EntityChangeMove<>(speed, 0.4f, 0.0f);
		EntityChangeMove<IMutablePlayerEntity> move3 = new EntityChangeMove<>(speed, 0.4f, 0.0f);
		long commit1 = projector.applyLocalChange(move1, 100L);
		long commit2 = projector.applyLocalChange(move2, 200L);
		long commit3 = projector.applyLocalChange(move3, 300L);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(2L, commit2);
		Assert.assertEquals(3L, commit3);
		
		// We should see the entity moved to its speculative location.
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		
		// Now, absorb the first 2 changes from the server so we force follow-ups to be evaluated in a way which allows them to bunch up.
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(move1), new EntityMutationWrapper(move2))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, 200L
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		
		// Absorb the final change to make sure that the result is still as expected.
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EntityMutationWrapper(move3))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit3
				, 300L
		);
		
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
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
		Assert.assertEquals(1, inventory1.sortedKeys().size());
		Assert.assertEquals(1, inventory1.getCount(stoneItem));
		Inventory inventory2 = listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, block2.getBlockAddress());
		Assert.assertEquals(3 * encumbrance, inventory2.currentEncumbrance);
		Assert.assertEquals(1, inventory1.sortedKeys().size());
		Assert.assertEquals(3, inventory2.getCount(stoneItem));
	}

	private static class CountingListener implements SpeculativeProjection.IProjectionListener
	{
		public int loadCount = 0;
		public int changeCount = 0;
		public int unloadCount = 0;
		public IReadOnlyCuboidData lastData = null;
		public int entityChangeCount = 0;
		public Entity thisEntityState = null;
		public Map<Integer, PartialEntity> otherEntityStates = new HashMap<>();
		
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
		public void thisEntityDidLoad(Entity entity)
		{
			Assert.assertNull(this.thisEntityState);
			this.thisEntityState = entity;
		}
		@Override
		public void thisEntityDidChange(Entity entity)
		{
			Assert.assertNotNull(this.thisEntityState);
			this.thisEntityState = entity;
			this.entityChangeCount += 1;
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			PartialEntity old = this.otherEntityStates.put(entity.id(), entity);
			Assert.assertNull(old);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			PartialEntity old = this.otherEntityStates.put(entity.id(), entity);
			Assert.assertNotNull(old);
			this.entityChangeCount += 1;
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			PartialEntity old = this.otherEntityStates.remove(id);
			Assert.assertNull(old);
		}
	}
}
