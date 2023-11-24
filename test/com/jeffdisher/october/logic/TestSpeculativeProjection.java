package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Either;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;


public class TestSpeculativeProjection
{
	@Test
	public void basicApplyMatching()
	{
		// We want to test that adding a few mutations as speculative, but then adding them as "committed" causes no problem.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		projector.loadedEntity(EntityActionValidator.buildDefaultEntity(0));
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		CuboidData cuboid = CuboidData.createNew(address, new IOctree[] { data });
		projector.loadedCuboid(address, cuboid);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Apply a few local mutations.
		IMutation mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 0), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone1 = new EntityChangeMutation(0, mutation1);
		IMutation mutation2 = new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 1), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone2 = new EntityChangeMutation(0, mutation2);
		long commit1 = projector.applyLocalChange(lone1);
		long commit2 = projector.applyLocalChange(lone2);
		List<Either<IMutation, IEntityChange>> actionsToCommit = new ArrayList<>();
		long[] commitNumbers = new long[5];
		for (int i = 0; i < commitNumbers.length; ++i)
		{
			AbsoluteLocation location = new AbsoluteLocation(i, 0, 0);
			IMutation mutation = new ReplaceBlockMutation(location, BlockAspect.AIR, BlockAspect.STONE);
			IEntityChange entityChange = new EntityChangeMutation(0, mutation);
			actionsToCommit.add(Either.second(entityChange));
			actionsToCommit.add(Either.first(mutation));
			commitNumbers[i] = projector.applyLocalChange(entityChange);
		}
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit the first 2, one at a time, and then the last ones at the same time.
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone1), Either.first(mutation1)), commit1);
		// 12 speculative elements since there are 6 changes, each with a mutation.
		Assert.assertEquals(12, speculativeCount);
		Assert.assertEquals(8, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone2), Either.first(mutation2)), commit2);
		// 10 speculative elements since there are 5 changes, each with a mutation.
		Assert.assertEquals(10, speculativeCount);
		Assert.assertEquals(9, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), actionsToCommit, commitNumbers[commitNumbers.length - 1]);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(10, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Now, unload.
		speculativeCount = projector.applyCommittedMutations(Set.of(address), Collections.emptyList(), commitNumbers[commitNumbers.length - 1]);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.unloadCount);
	}

	@Test
	public void unloadWithMutations()
	{
		// Test that unloading a cuboid with local mutations correctly purges them but can go on to commit other things.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		projector.loadedEntity(EntityActionValidator.buildDefaultEntity(0));
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		OctreeShort data0 = OctreeShort.create(BlockAspect.AIR);
		OctreeShort data1 = OctreeShort.create(BlockAspect.AIR);
		CuboidData cuboid0 = CuboidData.createNew(address0, new IOctree[] { data0 });
		CuboidData cuboid1 = CuboidData.createNew(address1, new IOctree[] { data1 });
		projector.loadedCuboid(address0, cuboid0);
		projector.loadedCuboid(address1, cuboid1);
		Assert.assertEquals(2, listener.loadCount);
		
		// Apply a few local mutations.
		IMutation mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone0 = new EntityChangeMutation(0, mutation0);
		IMutation mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone1 = new EntityChangeMutation(0, mutation1);
		projector.applyLocalChange(lone0);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		long commit1 = projector.applyLocalChange(lone1);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit the other one.
		int speculativeCount = projector.applyCommittedMutations(Set.of(address1), List.of(Either.second(lone0), Either.first(mutation0)), commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(1, listener.unloadCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Unload the other.
		speculativeCount = projector.applyCommittedMutations(Set.of(address0), Collections.emptyList(), commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void applyWithConflicts()
	{
		// We want to test that adding a few mutations as speculative, and then committing a few conflicts to make sure that we drop the speculative mutaions which fail.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		projector.loadedEntity(EntityActionValidator.buildDefaultEntity(0));
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		OctreeShort data0 = OctreeShort.create(BlockAspect.AIR);
		OctreeShort data1 = OctreeShort.create(BlockAspect.AIR);
		CuboidData cuboid0 = CuboidData.createNew(address0, new IOctree[] { data0 });
		CuboidData cuboid1 = CuboidData.createNew(address1, new IOctree[] { data1 });
		projector.loadedCuboid(address0, cuboid0);
		projector.loadedCuboid(address1, cuboid1);
		Assert.assertEquals(2, listener.loadCount);
		
		// Apply a few local mutations.
		IMutation mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone0 = new EntityChangeMutation(0, mutation0);
		IMutation mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), BlockAspect.AIR, BlockAspect.STONE);
		IEntityChange lone1 = new EntityChangeMutation(0, mutation1);
		projector.applyLocalChange(lone0);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		long commit1 = projector.applyLocalChange(lone1);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone0), Either.first(mutation0)), 0L);
		// We should still see the other one.
		// Note that this is +2 since both entity changes stay in the list, despite both failing - we will still send them to the server unless they do pre-checking.
		Assert.assertEquals(1 +2, speculativeCount);
		// We see another 2 changes due to the reverses.
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		// Commit the other one normally.
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone1), Either.first(mutation1)), commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(5, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BlockAspect.STONE));
		
		speculativeCount = projector.applyCommittedMutations(Set.of(address0, address1), Collections.emptyList(), commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void applySecondaryMutations()
	{
		// We want to apply a few mutations which themselves cause secondary mutations, and observe what happens when some commit versus conflict.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		projector.loadedEntity(EntityActionValidator.buildDefaultEntity(0));
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		OctreeShort data0 = OctreeShort.create(BlockAspect.AIR);
		OctreeShort data1 = OctreeShort.create(BlockAspect.AIR);
		CuboidData cuboid0 = CuboidData.createNew(address0, new IOctree[] { data0 });
		CuboidData cuboid1 = CuboidData.createNew(address1, new IOctree[] { data1 });
		projector.loadedCuboid(address0, cuboid0);
		projector.loadedCuboid(address1, cuboid1);
		Assert.assertEquals(2, listener.loadCount);
		
		// Apply a few local mutations.
		IMutation mutation0 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 5), true, 2);
		IEntityChange lone0 = new EntityChangeMutation(0, mutation0);
		IMutation mutation1 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 37), true, 2);
		IEntityChange lone1 = new EntityChangeMutation(0, mutation1);
		projector.applyLocalChange(lone0);
		long commit1 = projector.applyLocalChange(lone1);
		Assert.assertEquals(2, listener.changeCount);
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone0), Either.first(mutation0)), 0L);
		// We should still see both initial mutations and all of their secondaries, since shockwaves don't actually conflict.
		// (+1 for the entity change)
		int mutationsPerShockwave = 1+ 1 + 6 + 36;
		Assert.assertEquals(2 * mutationsPerShockwave, speculativeCount);
		// We see another 2 changes due to the reverses.
		Assert.assertEquals(4, listener.changeCount);
		
		// Commit the other one normally.
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone1), Either.first(mutation1)), commit1);
		// This commit level change should cause them all to be retired.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(6, listener.changeCount);
		
		speculativeCount = projector.applyCommittedMutations(Set.of(address0, address1), Collections.emptyList(), commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void itemInventory()
	{
		// Test that we can apply inventory changes to speculative mutation.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		projector.loadedEntity(EntityActionValidator.buildDefaultEntity(0));
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		
		// Create and add an empty cuboid.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		OctreeShort blockTypes = OctreeShort.create(BlockAspect.AIR);
		OctreeObject inventories = OctreeObject.create();
		CuboidData cuboid = CuboidData.createNew(address, new IOctree[] { blockTypes, inventories });
		projector.loadedCuboid(address, cuboid);
		Assert.assertEquals(1, listener.loadCount);
		
		// Try to drop a few items.
		int encumbrance = 2;
		Item stoneItem = ItemRegistry.STONE;
		AbsoluteLocation block1 = new AbsoluteLocation(1, 1, 1);
		IMutation mutation1 = new DropItemMutation(block1, stoneItem, 1);
		IEntityChange lone1 = new EntityChangeMutation(0, mutation1);
		AbsoluteLocation block2 = new AbsoluteLocation(3, 3, 3);
		IMutation mutation2 = new DropItemMutation(block2, stoneItem, 3);
		IEntityChange lone2 = new EntityChangeMutation(0, mutation2);
		long commit1 = projector.applyLocalChange(lone1);
		long commit2 = projector.applyLocalChange(lone2);
		Assert.assertEquals(2, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Commit the first, then the second, making sure that things make sense at every point.
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone1), Either.first(mutation1)), commit1);
		Assert.assertEquals(2, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(lone2), Either.first(mutation2)), commit2);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(4, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Now, unload.
		speculativeCount = projector.applyCommittedMutations(Set.of(address), Collections.emptyList(), commit2);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.unloadCount);
	}

	@Test
	public void dependentEntityChanges()
	{
		// Test that we can enqueue new entity changes from within an entity change.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		
		// We need 2 entities for this but we will give one some items.
		Inventory startInventory = new Inventory(10, List.of(new Items(ItemRegistry.STONE, 2)), 2 * ItemRegistry.STONE.encumbrance());
		projector.loadedEntity(new Entity(0, EntityActionValidator.DEFAULT_LOCATION, EntityActionValidator.DEFAULT_VOLUME, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED, startInventory));
		projector.loadedEntity(EntityActionValidator.buildDefaultEntity(1));
		Assert.assertNotNull(listener.lastEntityStates.get(0));
		Assert.assertNotNull(listener.lastEntityStates.get(1));
		
		// Try to pass the items to the other entity.
		IEntityChange send = new EntityChangeSendItem(0, 1, ItemRegistry.STONE);
		long commit1 = projector.applyLocalChange(send);
		
		// Check the values.
		Assert.assertTrue(listener.lastEntityStates.get(0).inventory().items.isEmpty());
		Assert.assertEquals(1, listener.lastEntityStates.get(1).inventory().items.size());
		Items update = listener.lastEntityStates.get(1).inventory().items.get(0);
		Assert.assertEquals(ItemRegistry.STONE, update.type());
		Assert.assertEquals(2, update.count());
		
		// Commit this and make sure the values are still correct.
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), List.of(Either.second(send)), commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertTrue(listener.lastEntityStates.get(0).inventory().items.isEmpty());
		Assert.assertEquals(1, listener.lastEntityStates.get(1).inventory().items.size());
		update = listener.lastEntityStates.get(1).inventory().items.get(0);
		Assert.assertEquals(ItemRegistry.STONE, update.type());
		Assert.assertEquals(2, update.count());
	}


	private int _countBlocks(CuboidData cuboid, short blockType)
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
		Assert.assertEquals(stoneItem, inventory1.items.get(0).type());
		Assert.assertEquals(1, inventory1.items.get(0).count());
		Inventory inventory2 = listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, block2.getBlockAddress());
		Assert.assertEquals(3 * encumbrance, inventory2.currentEncumbrance);
		Assert.assertEquals(1, inventory1.items.size());
		Assert.assertEquals(stoneItem, inventory2.items.get(0).type());
		Assert.assertEquals(3, inventory2.items.get(0).count());
	}

	private static class CountingListener implements SpeculativeProjection.IProjectionListener
	{
		public int loadCount = 0;
		public int changeCount = 0;
		public int unloadCount = 0;
		public CuboidData lastData = null;
		public Map<Integer, Entity> lastEntityStates = new HashMap<>();
		
		@Override
		public void cuboidDidLoad(CuboidAddress address, CuboidData cuboid)
		{
			this.loadCount += 1;
			this.lastData = cuboid;
		}
		@Override
		public void cuboidDidChange(CuboidAddress address, CuboidData cuboid)
		{
			this.changeCount += 1;
			this.lastData = cuboid;
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address, CuboidData cuboid)
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
	}
}
