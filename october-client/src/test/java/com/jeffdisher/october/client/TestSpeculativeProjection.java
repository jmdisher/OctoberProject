package com.jeffdisher.october.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.mutations.EntityChangeAcceptItems;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.EntityChangeSetOrientation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
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
	public static final long MILLIS_PER_TICK = 100L;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item STONE_BRICK_ITEM;
	private static Item LOG_ITEM;
	private static Item PLANK_ITEM;
	private static Item CRAFTING_TABLE_ITEM;
	private static Item FURNACE_ITEM;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE_BRICK_ITEM = ENV.items.getItemById("op.stone_brick");
		LOG_ITEM = ENV.items.getItemById("op.log");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		CRAFTING_TABLE_ITEM = ENV.items.getItemById("op.crafting_table");
		FURNACE_ITEM = ENV.items.getItemById("op.furnace");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		projector.applyChangesForServerTick(1L
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
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
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
		Assert.assertEquals(0, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(Integer.MIN_VALUE, listener.lastHeightMap.getHeight(0, 0));
		
		// Apply a few local mutations.
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 0), ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		IMutationBlock mutation2 = new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 1), ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, currentTimeMillis);
		long commit2 = projector.applyLocalChange(lone2, currentTimeMillis);
		List<MutationBlockSetBlock> mutationsToCommit = new ArrayList<>();
		long[] commitNumbers = new long[5];
		for (int i = 0; i < commitNumbers.length; ++i)
		{
			AbsoluteLocation location = new AbsoluteLocation(i, 0, 0);
			IMutationBlock mutation = new ReplaceBlockMutation(location, ENV.special.AIR.item().number(), STONE_ITEM.number());
			EntityChangeMutation entityChange = new EntityChangeMutation(mutation);
			mutationsToCommit.add(FakeUpdateFactories.blockUpdate(serverCuboid, mutation));
			commitNumbers[i] = projector.applyLocalChange(entityChange, currentTimeMillis);
		}
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, STONE_ITEM.number()));
		// Each local change causes an update so we only see 1.
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastHeightMap.getHeight(0, 0));
		Assert.assertEquals(0, listener.lastHeightMap.getHeight(1, 0));
		
		// Commit the first 2, one at a time, and then the last ones at the same time.
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		// Only the changes are in the speculative list:  The one we committed should be something we already reported.
		Assert.assertEquals(6, speculativeCount);
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastHeightMap.getHeight(0, 0));
		Assert.assertEquals(0, listener.lastHeightMap.getHeight(1, 0));
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, mutation2))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, 1L
		);
		// 5 changes left but no new callbacks since there are no conflicts.
		Assert.assertEquals(5, speculativeCount);
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastHeightMap.getHeight(0, 0));
		Assert.assertEquals(0, listener.lastHeightMap.getHeight(1, 0));
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, mutationsToCommit
				, Collections.emptyList()
				, Collections.emptyList()
				, commitNumbers[commitNumbers.length - 1]
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastHeightMap.getHeight(0, 0));
		Assert.assertEquals(0, listener.lastHeightMap.getHeight(1, 0));
		
		// Now, unload.
		speculativeCount = projector.applyChangesForServerTick(6L
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		projector.applyChangesForServerTick(1L
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
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(0, listener.changeCount);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress address1 = CuboidAddress.fromInt(0, 0, 1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		CuboidData serverCuboid0 = CuboidData.mutableClone(cuboid0);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(2, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply a few local mutations.
		IMutationBlock mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, currentTimeMillis);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		long commit1 = projector.applyLocalChange(lone1, currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(32, listener.lastHeightMap.getHeight(0, 1));
		
		// Commit the other one.
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid0, mutation0))
				, Collections.emptyList()
				, List.of(address1)
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, listener.unloadCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		
		// Unload the other.
		speculativeCount = projector.applyChangesForServerTick(4L
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress address1 = CuboidAddress.fromInt(0, 0, 1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		CuboidData serverCuboid0 = CuboidData.mutableClone(cuboid0);
		CuboidData serverCuboid1 = CuboidData.mutableClone(cuboid1);
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(2, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply a few local mutations.
		IMutationBlock mutation0 = new ReplaceBlockMutation(new AbsoluteLocation(1, 0, 0), ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ReplaceBlockMutation(new AbsoluteLocation(0, 1, 32), ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, currentTimeMillis);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		long commit1 = projector.applyLocalChange(lone1, currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(32, listener.lastHeightMap.getHeight(0, 1));
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid0, mutation0))
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		// We will still see 2 elements in the speculative list since EntityChangeMutation always claims to have applied.  Hence, we will only remove them when the commit level passes them.
		Assert.assertEquals(2, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		
		// Commit the other one normally.
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid1, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		
		speculativeCount = projector.applyChangesForServerTick(5L
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress address1 = CuboidAddress.fromInt(0, 0, 1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, STONE);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, STONE);
		CuboidData serverCuboid0 = CuboidData.mutableClone(cuboid0);
		CuboidData serverCuboid1 = CuboidData.mutableClone(cuboid1);
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(2, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply a few local mutations.
		IMutationBlock mutation0 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 5), 2);
		EntityChangeMutation lone0 = new EntityChangeMutation(mutation0);
		IMutationBlock mutation1 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 37), 2);
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		projector.applyLocalChange(lone0, currentTimeMillis);
		long commit1 = projector.applyLocalChange(lone1, currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid0, mutation0))
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		// We should still just see the initial changes in the speculative list and the 1 update from the incoming change.
		Assert.assertEquals(2, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertTrue(listener.lastChangedBlocks.contains(mutation0.getAbsoluteLocation().getBlockAddress()));
		
		// Commit the other one normally.
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid1, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		// This commit level change should cause them all to be retired.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertTrue(listener.lastChangedBlocks.contains(mutation1.getAbsoluteLocation().getBlockAddress()));
		
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address0, address1)
				, commit1
				, currentTimeMillis
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(0, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Create and add an empty cuboid.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
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
		
		// Try to drop a few items.
		int encumbrance = 4;
		Item stoneItem = STONE_ITEM;
		AbsoluteLocation block1 = new AbsoluteLocation(1, 1, 1);
		IMutationBlock mutation1 = new DropItemMutation(block1, stoneItem, 1);
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		AbsoluteLocation block2 = new AbsoluteLocation(3, 3, 3);
		IMutationBlock mutation2 = new DropItemMutation(block2, stoneItem, 3);
		EntityChangeMutation lone2 = new EntityChangeMutation(mutation2);
		long commit1 = projector.applyLocalChange(lone1, currentTimeMillis);
		long commit2 = projector.applyLocalChange(lone2, currentTimeMillis);
		Assert.assertEquals(2, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Commit the first, then the second, making sure that things make sense at every point.
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(2 + 1, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2 + 1 + 1, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Now, unload.
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, List.of()
				, Collections.emptyList()
				, List.of(address)
				, commit2
				, currentTimeMillis
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId1, listener, MILLIS_PER_TICK);
		
		// We need 2 entities for this but we will give one some items.
		int entityId2 = 2;
		MutableEntity mutable = MutableEntity.createForTest(entityId1);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		projector.setThisEntity(mutable.freeze());
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of(PartialEntity.fromEntity(MutableEntity.createForTest(entityId2).freeze()))
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertNotNull(listener.otherEntityStates.get(entityId2));
		PartialEntity otherEntity = listener.otherEntityStates.get(entityId2);
		
		// Try to pass the items to the other entity.
		EntityChangeSendItem send = new EntityChangeSendItem(entityId2, STONE_ITEM);
		long commit1 = projector.applyLocalChange(send, currentTimeMillis);
		
		// Check the values.
		Assert.assertEquals(1, listener.authoritativeEntityState.inventory().sortedKeys().size());
		Assert.assertEquals(0, listener.thisEntityState.inventory().sortedKeys().size());
		// Speculative projection no longer runs follow-up changes on entities, only cuboids, so this should be unchanged, even though we reference it.
		Assert.assertTrue(otherEntity == listener.otherEntityStates.get(entityId2));
		
		// Commit this and make sure the values are still correct.
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(FakeUpdateFactories.entityUpdate(Map.of(), listener.authoritativeEntityState, send))
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		// NOTE:  The other half of the transfer is going to be run against the second entity, on the server, but we
		// just see the partial update, on the client (if even that - the server may realize there is no change).
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of(entityId2, new LinkedList<>(List.of(new MutationEntitySetPartialEntity(otherEntity))))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().sortedKeys().size());
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		long currentTimeMillis = 1L;
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		projector.applyChangesForServerTick(1L
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
		EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(changeLocation, (short)1000);
		long commit1 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(STONE_ITEM.number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)1000, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(31, listener.lastHeightMap.getHeight(0, 0));
		
		// Allow time to pass in the local environment apply the second stage of the change.
		currentTimeMillis += 200L;
		long commit2 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(2, commit2);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		// We should see no inventory in the block but the item should be in the entity's inventory.
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(31, listener.lastHeightMap.getHeight(0, 0));
		
		// If we commit the first part of this change, we should still see the same result - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 100L;
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(changeLocation, (short)1000, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
		
		// Commit the second part and make sure the change is still there.
		currentTimeMillis += 100L;
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(changeLocation, (short) 1000, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		// (the authoritative side doesn't synthesize the item entering the inventory so it will be empty until the authoritative answer arrives).
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
	}

	@Test
	public void movePartialRejection()
	{
		// We want to test that 2 move changes, where the first is rejected by the server, but the second is still applied locally.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		EntityLocation initialLocation = listener.authoritativeEntityState.location();
		
		// Apply the 2 steps of the move, locally.
		// (note that 0.4 is the limit for one tick)
		EntityLocation midStep = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation lastStep = new EntityLocation(0.8f, 0.0f, 0.0f);
		float speed = EntityConstants.SPEED_PLAYER;
		long millisInStep = EntityChangeMove.getTimeMostMillis(speed, 0.4f, 0.0f);
		EntityChangeMove<IMutablePlayerEntity> move1 = new EntityChangeMove<>(millisInStep, 1.0f, EntityChangeMove.Direction.EAST);
		EntityChangeMove<IMutablePlayerEntity> move2 = new EntityChangeMove<>(millisInStep, 1.0f, EntityChangeMove.Direction.EAST);
		long commit1 = projector.applyLocalChange(move1, currentTimeMillis);
		long commit2 = projector.applyLocalChange(move2, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(2L, commit2);
		
		// We should see the entity moved to its speculative location (but only in projection).
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		
		// Now, absorb a change from the server where neither change is present but the first has been considered.
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		
		// We should now see 1 speculative commit and the entity should have moved over by that step, alone.
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(midStep, listener.thisEntityState.location());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.thisEntityState.yaw());
	}

	@Test
	public void craftPlanks()
	{
		// Test the in-inventory crafting operation.
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Load some items into the inventory.
		EntityChangeAcceptItems load = new EntityChangeAcceptItems(new Items(LOG_ITEM, 2));
		long commit1 = projector.applyLocalChange(load, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		
		// We will handle this as a single crafting operation to test the simpler case.
		EntityChangeCraft craft = new EntityChangeCraft(logToPlanks, logToPlanks.millisPerCraft);
		long commit2 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(2L, commit2);
		// Verify that we finished the craft (no longer in progress).
		Assert.assertNull(listener.authoritativeEntityState.localCraftOperation());
		Assert.assertNull(listener.thisEntityState.localCraftOperation());
		
		// Check the inventory to see the craft completed.
		Inventory inv = listener.authoritativeEntityState.inventory();
		Assert.assertEquals(0, inv.getCount(LOG_ITEM));
		Assert.assertEquals(0, inv.getCount(PLANK_ITEM));
		inv = listener.thisEntityState.inventory();
		Assert.assertEquals(1, inv.getCount(LOG_ITEM));
		Assert.assertEquals(2, inv.getCount(PLANK_ITEM));
		
		currentTimeMillis = 3000L;
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
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
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(STONE_ITEM, 1);
		int stoneKey = mutable.newInventory.getIdOfStackableType(STONE_ITEM);
		mutable.setSelectedKey(stoneKey);
		Entity entity = mutable.freeze();
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(0, listener.entityChangeCount);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		entity = listener.thisEntityState;
		Assert.assertEquals(stoneKey, entity.hotbarItems()[entity.hotbarIndex()]);
		
		// Do the craft and observe it takes multiple actions with no current activity.
		EntityChangeCraft craft = new EntityChangeCraft(stoneToStoneBrick, 1000L);
		long commit1 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(1, listener.entityChangeCount);
		Assert.assertNull(listener.authoritativeEntityState.localCraftOperation());
		Assert.assertNotNull(listener.thisEntityState.localCraftOperation());
		
		craft = new EntityChangeCraft(stoneToStoneBrick, 1000L);
		long commit2 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(2L, commit2);
		Assert.assertEquals(2, listener.entityChangeCount);
		Assert.assertNull(listener.authoritativeEntityState.localCraftOperation());
		Assert.assertNull(listener.thisEntityState.localCraftOperation());
		
		// Check the inventory to see the craft completed.
		Inventory inv = listener.authoritativeEntityState.inventory();
		Assert.assertEquals(1, inv.getCount(STONE_ITEM));
		Assert.assertEquals(0, inv.getCount(STONE_BRICK_ITEM));
		inv = listener.thisEntityState.inventory();
		Assert.assertEquals(0, inv.getCount(STONE_ITEM));
		Assert.assertEquals(1, inv.getCount(STONE_BRICK_ITEM));
		entity = listener.thisEntityState;
		Assert.assertEquals(Entity.NO_SELECTION, entity.hotbarItems()[entity.hotbarIndex()]);
	}

	@Test
	public void placeBlockTwice()
	{
		// Make sure that the speculative projection will prevent us from placing the same block down twice.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(STONE_ITEM));
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(Integer.MIN_VALUE, listener.lastHeightMap.getHeight(0, 0));
		
		// Apply the local change.
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
		long commit1 = projector.applyLocalChange(place, currentTimeMillis);
		Assert.assertEquals(1, commit1);
		// (verify that it fails if we try to run it again.
		long commit2 = projector.applyLocalChange(place, currentTimeMillis);
		Assert.assertEquals(0, commit2);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastHeightMap.getHeight(1, 1));
	}

	@Test
	public void placeAndUseTable()
	{
		// Test the in-inventory crafting operation.
		Craft stoneToStoneBrick = ENV.crafting.getCraftById("op.stone_to_stone_brick");
		CountingListener listener = new CountingListener();
		int localEntityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(localEntityId, listener, MILLIS_PER_TICK);
		MutableEntity mutable = MutableEntity.createForTest(localEntityId);
		mutable.newInventory.addAllItems(CRAFTING_TABLE_ITEM, 1);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(CRAFTING_TABLE_ITEM));
		int stoneKey = mutable.newInventory.getIdOfStackableType(STONE_ITEM);
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR)
						, CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Place the crafting table.
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 0);
		BlockAddress blockLocation = location.getBlockAddress();
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
		long commit1 = projector.applyLocalChange(place, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		
		// Store the stones in the inventory.
		MutationEntityPushItems push = new MutationEntityPushItems(location, stoneKey, 2, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit2 = projector.applyLocalChange(push, currentTimeMillis);
		Assert.assertEquals(2L, commit2);
		
		// Now, craft against the table (it has 10x speed so we will do this in 2 shots).
		EntityChangeCraftInBlock craft = new EntityChangeCraftInBlock(location, stoneToStoneBrick, 100L);
		long commit3 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(3L, commit3);
		
		// Check the block and all of its aspects.
		Block craftingTable = ENV.blocks.fromItem(ENV.items.getItemById("op.crafting_table"));
		BlockProxy proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(craftingTable, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1000L, proxy.getCrafting().completedMillis());
		Assert.assertEquals(0, listener.lastHeightMap.getHeight(1, 1));
		
		// Complete the craft and check the proxy.
		craft = new EntityChangeCraftInBlock(location, null, 100L);
		long commit4 = projector.applyLocalChange(craft, currentTimeMillis);
		Assert.assertEquals(4L, commit4);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(craftingTable, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_BRICK_ITEM));
		Assert.assertNull(proxy.getCrafting());
		
		// Now, break the table and verify that the final inventory state makes sense.
		// We expect the table inventory to spill into the block but the table to end up in the entity's inventory.
		EntityChangeIncrementalBlockBreak breaking = new EntityChangeIncrementalBlockBreak(location, (short)100);
		long commit5 = projector.applyLocalChange(breaking, currentTimeMillis);
		Assert.assertEquals(5L, commit5);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().sortedKeys().size());
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_BRICK_ITEM));
		Assert.assertEquals(-1, listener.lastHeightMap.getHeight(1, 1));
		
		Inventory entityInventory = listener.authoritativeEntityState.inventory();
		Assert.assertEquals(2, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(CRAFTING_TABLE_ITEM));
		entityInventory = listener.thisEntityState.inventory();
		Assert.assertEquals(1, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(CRAFTING_TABLE_ITEM));
	}

	@Test
	public void craftFurnaceFailure()
	{
		// Test the in-inventory crafting operation.
		Craft stoneBricksToFurnace = ENV.crafting.getCraftById("op.stone_bricks_to_furnace");
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(STONE_BRICK_ITEM, 4);
		Inventory inventory = Inventory.start(StationRegistry.CAPACITY_PLAYER).addStackable(STONE_BRICK_ITEM, 4).finish();
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Verify that this craft should be valid for the inventory.
		Assert.assertTrue(CraftAspect.canApply(stoneBricksToFurnace, inventory));
		
		// But verify that it fails when applied to the entity, directly (as it isn't "trivial").
		EntityChangeCraft craft = new EntityChangeCraft(stoneBricksToFurnace, 100L);
		long commit = projector.applyLocalChange(craft, currentTimeMillis);
		// This should fail to apply.
		Assert.assertEquals(0, commit);
		// There should be no active operation.
		Assert.assertNull(listener.authoritativeEntityState.localCraftOperation());
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
		SpeculativeProjection projector = new SpeculativeProjection(localEntityId, listener, MILLIS_PER_TICK);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		BlockAddress block = BlockAddress.fromInt(0, 0, 0);
		Inventory inv = Inventory.start(10).addStackable(STONE_ITEM, 1).finish();
		int stoneKey = inv.getIdOfStackableType(STONE_ITEM);
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, block, inv);
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		projector.setThisEntity(MutableEntity.createForTest(localEntityId).freeze());
		projector.applyChangesForServerTick(1L
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
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, listener.thisEntityState.inventory().currentEncumbrance);
		
		// Issue the command to pick up the item.
		AbsoluteLocation location = new AbsoluteLocation(0, 0, 0);
		int blockInventoryKey = stoneKey;
		int countRequested = 1;
		MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(location, blockInventoryKey, countRequested, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit1 = projector.applyLocalChange(request, currentTimeMillis);
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(STONE_ITEM), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		// Apply the commit from the server and show it still works.
		currentTimeMillis += 100L;
		int speculative = projector.applyChangesForServerTick(2L
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
		// (the authoritative copy only made the request and doesn't synthesize the follow-up so it won't have the inventory until the authoritative change arrives).
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(STONE_ITEM), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		// Now, try to apply it again (this should fail since it won't be able to find the slot to validate the count).
		Assert.assertEquals(0, projector.applyLocalChange(request, currentTimeMillis));
		
		// Apply another 2 ticks, each with the correct part of the multi-step change and verify that the values still match.
		MutationBlockExtractItems extract = new MutationBlockExtractItems(location, blockInventoryKey, countRequested, Inventory.INVENTORY_ASPECT_INVENTORY, localEntityId);
		currentTimeMillis += 100L;
		speculative = projector.applyChangesForServerTick(3L
				, List.of()
				, List.of()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, extract))
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculative);
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(STONE_ITEM), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		MutationEntityStoreToInventory store = new MutationEntityStoreToInventory(new Items(STONE_ITEM, 1), null);
		currentTimeMillis += 100L;
		speculative = projector.applyChangesForServerTick(4L
				, List.of()
				, List.of()
				, List.of(FakeUpdateFactories.entityUpdate(Map.of(), listener.authoritativeEntityState, store))
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculative);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(STONE_ITEM), listener.authoritativeEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(STONE_ITEM), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
		
		currentTimeMillis += 100L;
		speculative = projector.applyChangesForServerTick(5L
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
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(STONE_ITEM), listener.authoritativeEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(ENV.encumbrance.getEncumbrance(STONE_ITEM), listener.thisEntityState.inventory().currentEncumbrance);
		Assert.assertEquals(0, new BlockProxy(block, listener.lastData).getInventory().currentEncumbrance);
	}

	@Test
	public void placeAndLoadFurnace()
	{
		CountingListener listener = new CountingListener();
		int localEntityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(localEntityId, listener, MILLIS_PER_TICK);
		MutableEntity mutable = MutableEntity.createForTest(localEntityId);
		mutable.newInventory.addAllItems(FURNACE_ITEM, 1);
		mutable.newInventory.addAllItems(PLANK_ITEM, 1);
		mutable.newInventory.addAllItems(STONE_ITEM, 1);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(FURNACE_ITEM));
		int plankKey = mutable.newInventory.getIdOfStackableType(PLANK_ITEM);
		int stoneKey = mutable.newInventory.getIdOfStackableType(STONE_ITEM);
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR)
						, CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Place the furnace.
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 0);
		BlockAddress blockLocation = location.getBlockAddress();
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
		long commit1 = projector.applyLocalChange(place, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		
		// Verify that storing stone in fuel inventory fails.
		MutationEntityPushItems pushFail = new MutationEntityPushItems(location, stoneKey, 1, Inventory.INVENTORY_ASPECT_FUEL);
		long commitFail = projector.applyLocalChange(pushFail, currentTimeMillis);
		Assert.assertEquals(0, commitFail);
		
		// Storing the stone in the normal inventory should work.
		MutationEntityPushItems push = new MutationEntityPushItems(location, stoneKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit2 = projector.applyLocalChange(push, currentTimeMillis);
		Assert.assertEquals(2L, commit2);
		
		// Verify that we can store the planks in the fuel inventory.
		MutationEntityPushItems pushFuel = new MutationEntityPushItems(location, plankKey, 1, Inventory.INVENTORY_ASPECT_FUEL);
		long commit3 = projector.applyLocalChange(pushFuel, currentTimeMillis);
		Assert.assertEquals(3L, commit3);
		
		// Check the block and all of its aspects.
		Block furnace = ENV.blocks.fromItem(ENV.items.getItemById("op.furnace"));
		BlockProxy proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(furnace, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1, proxy.getFuel().fuelInventory().getCount(PLANK_ITEM));
		
		// Now, break the furnace and verify that the final inventory state makes sense.
		// We expect the table inventory to spill into the block but the table to end up in the entity's inventory.
		EntityChangeIncrementalBlockBreak breaking = new EntityChangeIncrementalBlockBreak(location, (short)2000);
		long commit4 = projector.applyLocalChange(breaking, currentTimeMillis);
		Assert.assertEquals(4L, commit4);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().sortedKeys().size());
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1, proxy.getInventory().getCount(PLANK_ITEM));
		
		Inventory entityInventory = listener.authoritativeEntityState.inventory();
		Assert.assertEquals(3, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(FURNACE_ITEM));
		entityInventory = listener.thisEntityState.inventory();
		Assert.assertEquals(1, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(FURNACE_ITEM));
	}

	@Test
	public void breakBlockFullInventory()
	{
		// Break a simple block with a full inventory and verify that it drops at your feet.
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		CountingListener listener = new CountingListener();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		int stored = mutable.newInventory.addItemsBestEfforts(dirt.item(), 200);
		Assert.assertTrue(stored < 200);
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		
		AbsoluteLocation targetLocation = new AbsoluteLocation(1, 1, 1);
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress(), dirt.item().number());
		long currentTimeMillis = 1L;
		projector.setThisEntity(mutable.freeze());
		projector.applyChangesForServerTick(1L
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
		EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(targetLocation, (short)200);
		long commit1 = projector.applyLocalChange(blockBreak, currentTimeMillis);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
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
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		
		// Make sure that they are starving.
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.setFood((byte)0);
		projector.setThisEntity(mutable.freeze());
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
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
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		EntityLocation initialLocation = listener.authoritativeEntityState.location();
		Assert.assertEquals(OrientationHelpers.YAW_NORTH, listener.thisEntityState.yaw());
		Assert.assertEquals(OrientationHelpers.YAW_NORTH, listener.authoritativeEntityState.yaw());
		
		// Apply 3 steps, locally.
		// (note that 0.4 is the limit for one tick)
		EntityLocation secondStep = new EntityLocation(0.8f, 0.0f, 0.0f);
		EntityLocation lastStep = new EntityLocation(1.2f, 0.0f, 0.0f);
		float speed = EntityConstants.SPEED_PLAYER;
		long millisInStep = EntityChangeMove.getTimeMostMillis(speed, 0.4f, 0.0f);
		EntityChangeMove<IMutablePlayerEntity> move1 = new EntityChangeMove<>(millisInStep, 1.0f, EntityChangeMove.Direction.EAST);
		EntityChangeMove<IMutablePlayerEntity> move2 = new EntityChangeMove<>(millisInStep, 1.0f, EntityChangeMove.Direction.EAST);
		EntityChangeMove<IMutablePlayerEntity> move3 = new EntityChangeMove<>(millisInStep, 1.0f, EntityChangeMove.Direction.EAST);
		long commit1 = projector.applyLocalChange(move1, currentTimeMillis);
		long commit2 = projector.applyLocalChange(move2, currentTimeMillis);
		long commit3 = projector.applyLocalChange(move3, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(2L, commit2);
		Assert.assertEquals(3L, commit3);
		
		// We should see the entity moved to its speculative location (but only locally).
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.thisEntityState.yaw());
		Assert.assertEquals(OrientationHelpers.YAW_NORTH, listener.authoritativeEntityState.yaw());
		
		// Now, absorb the first 2 changes from the server so we force follow-ups to be evaluated in a way which allows them to bunch up.
		MutableEntity authoritativeMutable = MutableEntity.existing(listener.authoritativeEntityState);
		FakeUpdateFactories.entityUpdate(Map.of(cuboid.getCuboidAddress(), cuboid), authoritativeMutable.freeze(), move1).applyToEntity(authoritativeMutable);
		FakeUpdateFactories.entityUpdate(Map.of(cuboid.getCuboidAddress(), cuboid), authoritativeMutable.freeze(), move2).applyToEntity(authoritativeMutable);
		
		currentTimeMillis = 200L;
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new MutationEntitySetEntity(authoritativeMutable.freeze()))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(secondStep, listener.authoritativeEntityState.location());
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.thisEntityState.yaw());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.authoritativeEntityState.yaw());
		
		// Absorb the final change to make sure that the result is still as expected.
		currentTimeMillis = 300L;
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(FakeUpdateFactories.entityUpdate(Map.of(cuboid.getCuboidAddress(), cuboid), authoritativeMutable.freeze(), move3))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit3
				, currentTimeMillis
		);
		
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(lastStep, listener.authoritativeEntityState.location());
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.thisEntityState.yaw());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.authoritativeEntityState.yaw());
	}

	@Test
	public void checkCallbacksForUpdates()
	{
		// We want to check that the update callbacks make sense when the change is conflicting as well as when it is non-conflicting to the same location.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		projector.applyChangesForServerTick(1L
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
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 2);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		// We are going to try interacting with an inventory so write a solid block under it.
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
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
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(Integer.MIN_VALUE, listener.lastHeightMap.getHeight(0, 0));
		
		// Apply a single local mutation.
		ReplaceBlockMutation mutation = new ReplaceBlockMutation(target, ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone = new EntityChangeMutation(mutation);
		long commit = projector.applyLocalChange(lone, currentTimeMillis);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(2, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 1));
		
		// Commit an unrelated mutation (from the server) to change the inventory (this is air, so it can hold data and placing the block will overwrite it).
		MutationBlockStoreItems storeItems = new MutationBlockStoreItems(target, new Items(STONE_ITEM, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY);
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, storeItems))
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		// There should still be a change in the speculative list but our change would over-write the inventory so there shouldn't be a callback.
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(2, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 1));
		
		// Now commit a conflicting change.
		ReplaceBlockMutation conflict = new ReplaceBlockMutation(target, ENV.special.AIR.item().number(), LOG_ITEM.number());
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, conflict))
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		// The speculative change will be there until we see the commit level and we should see the change from the conflict writing (since the wrapper we use always returns success).
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, _countBlocks(listener.lastData, LOG_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 1));
		
		// Account for the commit level update.
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit
				, 1L
		);
		// This should retire the change with no updates.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, _countBlocks(listener.lastData, LOG_ITEM.number()));
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 1));
	}

	@Test
	public void smallServerUpdate()
	{
		// We just plumb through small block updates from the server and verify that we see the right updates.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData scratchCuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of(scratchCuboid)
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(address, listener.lastData.getCuboidAddress());
		listener.lastData = null;
		Assert.assertEquals(Integer.MIN_VALUE, listener.lastHeightMap.getHeight(1, 2));
		listener.lastHeightMap = null;
		Assert.assertNull(listener.lastChangedBlocks);
		
		// Make some updates to the scratch cuboid and write them to the projection.
		AbsoluteLocation blockLocation = new AbsoluteLocation(1, 2, 3);
		MutableBlockProxy blockProxy = new MutableBlockProxy(blockLocation, scratchCuboid);
		blockProxy.setBlockAndClear(STONE);
		AbsoluteLocation lightLocation = new AbsoluteLocation(4, 5, 6);
		MutableBlockProxy lightProxy = new MutableBlockProxy(lightLocation, scratchCuboid);
		lightProxy.setLight((byte)10);
		AbsoluteLocation inventoryLocation = new AbsoluteLocation(7, 8, 9);
		MutableBlockProxy inventoryProxy = new MutableBlockProxy(inventoryLocation, scratchCuboid);
		inventoryProxy.setInventory(Inventory.start(10).addStackable(STONE.item(), 5).finish());
		ByteBuffer scratchBuffer = ByteBuffer.allocate(1024);
		projector.applyChangesForServerTick(2L
				, List.of()
				, Collections.emptyList()
				, List.of()
				, Collections.emptyMap()
				, List.of(
						MutationBlockSetBlock.extractFromProxy(scratchBuffer, blockProxy),
						MutationBlockSetBlock.extractFromProxy(scratchBuffer, lightProxy),
						MutationBlockSetBlock.extractFromProxy(scratchBuffer, inventoryProxy)
				)
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, 2L
		);
		Assert.assertEquals(address, listener.lastData.getCuboidAddress());
		Assert.assertEquals(3, listener.lastChangedBlocks.size());
		Assert.assertEquals(3, listener.lastHeightMap.getHeight(1, 2));
	}

	@Test
	public void setOrientation()
	{
		// Just show that we can re-orient ourselves.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR))
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(OrientationHelpers.YAW_NORTH, listener.thisEntityState.yaw());
		Assert.assertEquals(OrientationHelpers.PITCH_FLAT, listener.thisEntityState.pitch());
		
		// Change orientation twice, showing that the final change is set locally.
		byte yaw1 = -10;
		byte pitch1 = 5;
		EntityChangeSetOrientation<IMutablePlayerEntity> set1 = new EntityChangeSetOrientation<>(yaw1, pitch1);
		byte yaw2 = -20;
		byte pitch2 = 15;
		EntityChangeSetOrientation<IMutablePlayerEntity> set2 = new EntityChangeSetOrientation<>(yaw2, pitch2);
		long commit1 = projector.applyLocalChange(set1, currentTimeMillis);
		long commit2 = projector.applyLocalChange(set2, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(2L, commit2);
		long failRedundant = projector.applyLocalChange(set2, currentTimeMillis);
		Assert.assertEquals(0L, failRedundant);
		
		// We should see the entity oriented (but only in projection).
		Assert.assertEquals(yaw2, listener.thisEntityState.yaw());
		Assert.assertEquals(pitch2, listener.thisEntityState.pitch());
		Assert.assertEquals(OrientationHelpers.YAW_NORTH, listener.authoritativeEntityState.yaw());
		Assert.assertEquals(OrientationHelpers.PITCH_FLAT, listener.authoritativeEntityState.pitch());
		
		// Absorb the first change and observe.
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(FakeUpdateFactories.entityUpdate(Map.of(), listener.authoritativeEntityState, set1))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		
		// We should now see 1 speculative commit and partial updates to orientation.
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(yaw2, listener.thisEntityState.yaw());
		Assert.assertEquals(pitch2, listener.thisEntityState.pitch());
		Assert.assertEquals(yaw1, listener.authoritativeEntityState.yaw());
		Assert.assertEquals(pitch1, listener.authoritativeEntityState.pitch());
		
		// Absorb the next change and see consistency.
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(FakeUpdateFactories.entityUpdate(Map.of(), listener.authoritativeEntityState, set2))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(yaw2, listener.thisEntityState.yaw());
		Assert.assertEquals(pitch2, listener.thisEntityState.pitch());
		Assert.assertEquals(yaw2, listener.authoritativeEntityState.yaw());
		Assert.assertEquals(pitch2, listener.authoritativeEntityState.pitch());
	}

	@Test
	public void orientAndAccelerate()
	{
		// Change orientation and walk, showing that the movement is correctly interpretted in the projection.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		projector.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		long currentTimeMillis = 1L;
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of(airCuboid)
				, List.of()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		EntityLocation initialLocation = listener.authoritativeEntityState.location();
		
		// Change orientation and move, locally.
		byte yaw = 30;
		byte pitch = 20;
		EntityChangeSetOrientation<IMutablePlayerEntity> set = new EntityChangeSetOrientation<>(yaw, pitch);
		EntityLocation targetLocation = new EntityLocation(0.24f, 0.22f, 0.0f);
		EntityChangeAccelerate<IMutablePlayerEntity> move = new EntityChangeAccelerate<>(100L, EntityChangeAccelerate.Relative.RIGHT);
		long commit1 = projector.applyLocalChange(set, currentTimeMillis);
		long commit2 = projector.applyLocalChange(move, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(2L, commit2);
		
		// We should see the entity moved to its speculative location (but only in projection).
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(targetLocation, listener.thisEntityState.location());
		
		// Absorb the first change and observe.
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(FakeUpdateFactories.entityUpdate(Map.of(airAddress, airCuboid), listener.authoritativeEntityState, set))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit1
				, currentTimeMillis
		);
		
		// We should now see 1 speculative commit and partial updates.
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(yaw, listener.thisEntityState.yaw());
		Assert.assertEquals(pitch, listener.thisEntityState.pitch());
		Assert.assertEquals(yaw, listener.authoritativeEntityState.yaw());
		Assert.assertEquals(pitch, listener.authoritativeEntityState.pitch());
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(targetLocation, listener.thisEntityState.location());
		
		// Absorb the next change and see consistency.
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(FakeUpdateFactories.entityUpdate(Map.of(airAddress, airCuboid), listener.authoritativeEntityState, move))
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(yaw, listener.thisEntityState.yaw());
		Assert.assertEquals(pitch, listener.thisEntityState.pitch());
		Assert.assertEquals(yaw, listener.authoritativeEntityState.yaw());
		Assert.assertEquals(pitch, listener.authoritativeEntityState.pitch());
		Assert.assertEquals(targetLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(targetLocation, listener.thisEntityState.location());
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
					short value = cuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, z));
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
		public ColumnHeightMap lastHeightMap = null;
		public Set<BlockAddress> lastChangedBlocks = null;
		public int entityChangeCount = 0;
		public Entity authoritativeEntityState = null;
		public Entity thisEntityState = null;
		public Map<Integer, PartialEntity> otherEntityStates = new HashMap<>();
		public long lastTickCompleted = 0L;
		
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			this.loadCount += 1;
			this.lastData = cuboid;
			this.lastHeightMap = heightMap;
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
		{
			Assert.assertFalse(changedBlocks.isEmpty());
			this.changeCount += 1;
			this.lastData = cuboid;
			this.lastHeightMap = heightMap;
			this.lastChangedBlocks = changedBlocks;
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			this.unloadCount += 1;
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			Assert.assertNull(this.authoritativeEntityState);
			Assert.assertNull(this.thisEntityState);
			this.authoritativeEntityState = authoritativeEntity;
			this.thisEntityState = authoritativeEntity;
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			Assert.assertNotNull(this.authoritativeEntityState);
			Assert.assertNotNull(this.thisEntityState);
			this.authoritativeEntityState = authoritativeEntity;
			this.thisEntityState = projectedEntity;
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
		@Override
		public void tickDidComplete(long gameTick)
		{
			Assert.assertTrue(gameTick > this.lastTickCompleted);
			this.lastTickCompleted = gameTick;
		}
	}
}
