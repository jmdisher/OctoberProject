package com.jeffdisher.october.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.mutations.MutationEntitySetPartialEntity;
import com.jeffdisher.october.mutations.ReplaceBlockMutation;
import com.jeffdisher.october.mutations.ShockwaveMutation;
import com.jeffdisher.october.subactions.EntityChangeAcceptItems;
import com.jeffdisher.october.subactions.EntityChangeAttackEntity;
import com.jeffdisher.october.subactions.EntityChangeCraft;
import com.jeffdisher.october.subactions.EntityChangeCraftInBlock;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.subactions.EntityChangePlaceMultiBlock;
import com.jeffdisher.october.subactions.EntityChangeSendItem;
import com.jeffdisher.october.subactions.EntitySubActionDropItemsAsPassive;
import com.jeffdisher.october.subactions.EntitySubActionReleaseWeapon;
import com.jeffdisher.october.subactions.MutationEntityPushItems;
import com.jeffdisher.october.subactions.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.utils.CuboidGenerator;


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
	private static EntityType ORC;
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
		ORC = ENV.creatures.getTypeById("op.orc");
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
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of()
				, List.of()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, List.of(cuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, lone1);
		long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, lone2);
		List<MutationBlockSetBlock> mutationsToCommit = new ArrayList<>();
		long[] commitNumbers = new long[5];
		for (int i = 0; i < commitNumbers.length; ++i)
		{
			AbsoluteLocation location = new AbsoluteLocation(i, 0, 0);
			IMutationBlock mutation = new ReplaceBlockMutation(location, ENV.special.AIR.item().number(), STONE_ITEM.number());
			EntityChangeMutation entityChange = new EntityChangeMutation(mutation);
			mutationsToCommit.add(FakeUpdateFactories.blockUpdate(serverCuboid, mutation));
			commitNumbers[i] = _wrapAndApply(projector, entity, currentTimeMillis, entityChange);
		}
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, STONE_ITEM.number()));
		// Each local change causes an update so we only see 1.
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		Assert.assertEquals(1, listener.lastHeightMap.getHeight(0, 0));
		Assert.assertEquals(0, listener.lastHeightMap.getHeight(1, 0));
		
		// Commit the first 2, one at a time, and then the last ones at the same time.
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, mutation2))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, mutationsToCommit
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address)
				, List.of()
				, commitNumbers[commitNumbers.length - 1]
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.unloadCount);
		Assert.assertTrue(listener.events.isEmpty());
	}

	@Test
	public void unloadWithMutations()
	{
		// Test that unloading a cuboid with local mutations correctly purges them but can go on to commit other things.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		_wrapAndApply(projector, entity, currentTimeMillis, lone0);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, lone1);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(32, listener.lastHeightMap.getHeight(0, 1));
		
		// Commit the other one.
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid0, mutation0))
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address1)
				, List.of()
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
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address0)
				, List.of()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
		Assert.assertTrue(listener.events.isEmpty());
	}

	@Test
	public void applyWithConflicts()
	{
		// We want to test that adding a few mutations as speculative, and then committing a few conflicts to make sure that we drop the speculative mutaions which fail.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		_wrapAndApply(projector, entity, currentTimeMillis, lone0);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, lone1);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(32, listener.lastHeightMap.getHeight(0, 1));
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid0, mutation0))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid1, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address0, address1)
				, List.of()
				, commit1
				, 1L
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
		Assert.assertTrue(listener.events.isEmpty());
	}

	@Test
	public void applySecondaryMutations()
	{
		// We want to apply a few mutations which themselves cause secondary mutations, and observe what happens when some commit versus conflict.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, List.of(cuboid0, cuboid1)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		_wrapAndApply(projector, entity, currentTimeMillis, lone0);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, lone1);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(25, listener.lastChangedBlocks.size());
		Assert.assertTrue(listener.lastChangedBlocks.contains(mutation0.getAbsoluteLocation().getBlockAddress()));
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		Assert.assertTrue(listener.lastChangedAspects.contains(AspectRegistry.DAMAGE));
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid0, mutation0))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		// We should still just see the initial changes in the speculative list and no updates since this didn't change anything.
		Assert.assertEquals(2, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		
		// Commit the other one normally.
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid1, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, currentTimeMillis
		);
		// This commit level change should cause them all to be retired but there will be no updates as nothing changed.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address0, address1)
				, List.of()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
		Assert.assertTrue(listener.events.isEmpty());
	}

	@Test
	public void itemInventory()
	{
		// Test that we can apply inventory changes to speculative mutation.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertEquals(0, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Create and add an empty cuboid.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		Block table = ENV.blocks.fromItem(CRAFTING_TABLE_ITEM);
		AbsoluteLocation block1 = new AbsoluteLocation(1, 1, 1);
		AbsoluteLocation block2 = new AbsoluteLocation(3, 3, 3);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, block1.getBlockAddress(), table.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, block1.getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, block2.getBlockAddress(), table.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, block2.getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(cuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Try to drop a few items.
		int encumbrance = 4;
		Item stoneItem = STONE_ITEM;
		IMutationBlock mutation1 = new MutationBlockStoreItems(block1, new Items(stoneItem, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY);
		EntityChangeMutation lone1 = new EntityChangeMutation(mutation1);
		IMutationBlock mutation2 = new MutationBlockStoreItems(block2, new Items(stoneItem, 3), null, Inventory.INVENTORY_ASPECT_INVENTORY);
		EntityChangeMutation lone2 = new EntityChangeMutation(mutation2);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, lone1);
		long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, lone2);
		Assert.assertEquals(2, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Commit the first, then the second, making sure that things make sense at every point.
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		// The same follow-up operations will be applied so there will be no change to the world.
		Assert.assertEquals(2, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, mutation1))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		// The server change and the remaining follow-up operations will be applied so there will be no change to the world.
		Assert.assertEquals(2, listener.changeCount);
		
		// Check the values.
		_checkInventories(listener, encumbrance, stoneItem, block1, block2);
		
		// Now, unload.
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(address)
				, List.of()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.unloadCount);
		Assert.assertTrue(listener.events.isEmpty());
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
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of(PartialEntity.fromEntity(MutableEntity.createForTest(entityId2).freeze()))
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		Assert.assertNotNull(listener.otherEntityStates.get(entityId2));
		PartialEntity otherEntity = listener.otherEntityStates.get(entityId2);
		
		// Try to pass the items to the other entity.
		EntityChangeSendItem send = new EntityChangeSendItem(entityId2, STONE_ITEM);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, send);
		
		// Check the values.
		Assert.assertEquals(1, listener.authoritativeEntityState.inventory().sortedKeys().size());
		Assert.assertEquals(0, listener.thisEntityState.inventory().sortedKeys().size());
		// Speculative projection no longer runs follow-up changes on entities, only cuboids, so this should be unchanged, even though we reference it.
		Assert.assertTrue(otherEntity == listener.otherEntityStates.get(entityId2));
		
		// Commit this and make sure the values are still correct.
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, FakeUpdateFactories.entityUpdate(Map.of(), listener.authoritativeEntityState, _wrap(entity, send))
				, Map.of()
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().sortedKeys().size());
		Assert.assertEquals(0, listener.thisEntityState.inventory().sortedKeys().size());
		// This won't change the instance since we will realize that they are the same.
		Assert.assertTrue(otherEntity == listener.otherEntityStates.get(entityId2));
		Assert.assertTrue(listener.events.isEmpty());
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
		long gameTick = 1L;
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(gameTick
				, List.of()
				, List.of()
				, List.of(cuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Apply the first stage of the change and observe that only the damage changes (done by cuboid mutation).
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		currentTimeMillis += 100L;
		EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(changeLocation);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, blockBreak);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(STONE_ITEM.number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)100, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		Assert.assertEquals(31, listener.lastHeightMap.getHeight(0, 0));
		
		// Apply the remaining hits to finish breaking.
		int hitsToBreak = (int) (ENV.damage.getToughness(STONE) / MILLIS_PER_TICK);
		long nextCommit = 2L;
		int changes = 1;
		for (int i = 1; i < hitsToBreak; ++i)
		{
			currentTimeMillis += 100L;
			long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, blockBreak);
			changes += 1;
			Assert.assertEquals(nextCommit, commit2);
			nextCommit += 1L;
			Assert.assertEquals(changes, listener.changeCount);
		}
		long commit2 = nextCommit - 1L;
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		// We should see no inventory in the block but the item should be in the entity's inventory.
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(31, listener.lastHeightMap.getHeight(0, 0));
		
		// If we commit the first part of this change, we should still see the same result - note that we need to fake-up all the changes and mutations which would come from this.
		currentTimeMillis += 100L;
		gameTick += 1L;
		int speculativeCount = projector.applyChangesForServerTick(gameTick
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(hitsToBreak - 1, speculativeCount);
		currentTimeMillis += 100L;
		gameTick += 1L;
		speculativeCount = projector.applyChangesForServerTick(gameTick
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(changeLocation, (short)1000, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(hitsToBreak - 1, speculativeCount);
		Assert.assertEquals(changes, listener.changeCount);
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
		
		// Commit the second part and make sure the change is still there.
		currentTimeMillis += 100L;
		gameTick += 1L;
		speculativeCount = projector.applyChangesForServerTick(gameTick
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(changeLocation, (short) 1000, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit2
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(changes, listener.changeCount);
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short) 0, listener.lastData.getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		Assert.assertNull(listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, changeLocation.getBlockAddress()));
		// (the authoritative side doesn't synthesize the item entering the inventory so it will be empty until the authoritative answer arrives).
		Assert.assertEquals(0, listener.authoritativeEntityState.inventory().getCount(STONE.item()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(STONE.item()));
		
		// Verify the events.
		Assert.assertEquals(1, listener.events.size());
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, changeLocation, 0, entityId), listener.events.get(0));
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
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR))
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		EntityLocation initialLocation = listener.authoritativeEntityState.location();
		
		// Apply the 2 steps of the move, locally.
		// (note that 0.4 is the limit for one tick)
		EntityLocation lastStep = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityActionSimpleMove<IMutablePlayerEntity> move1 = new EntityActionSimpleMove<>(0.2f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT, null);
		EntityActionSimpleMove<IMutablePlayerEntity> move2 = new EntityActionSimpleMove<>(0.2f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT, null);
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
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, currentTimeMillis
		);
		
		// We should now see 1 speculative commit with the entity only part of the way, since one step was reverted.
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(new EntityLocation(0.2f, 0.0f, 0.0f), listener.thisEntityState.location());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.thisEntityState.yaw());
		Assert.assertTrue(listener.events.isEmpty());
		
		// Absorb the final change as rejected.
		speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit2
				, currentTimeMillis
		);
		
		// This should show no speculative changes and no movement from start (rubber-band).
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(initialLocation, listener.thisEntityState.location());
		Assert.assertEquals(OrientationHelpers.YAW_NORTH, listener.thisEntityState.yaw());
		Assert.assertTrue(listener.events.isEmpty());
	}

	@Test
	public void craftPlanks()
	{
		// Test the in-inventory crafting operation.
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Load some items into the inventory.
		EntityChangeAcceptItems load = new EntityChangeAcceptItems(new Items(LOG_ITEM, 2));
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, load);
		Assert.assertEquals(1L, commit1);
		
		// We will handle this as a single crafting operation to test the simpler case.
		long nextCommit = 2L;
		for (long spent = 0L; spent < logToPlanks.millisPerCraft; spent += MILLIS_PER_TICK)
		{
			EntityChangeCraft craft = new EntityChangeCraft(logToPlanks);
			long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, craft);
			Assert.assertEquals(nextCommit, commit2);
			nextCommit += 1L;
		}
		// Verify that we finished the craft (no longer in progress).
		Assert.assertNull(listener.authoritativeEntityState.ephemeralShared().localCraftOperation());
		Assert.assertNull(listener.thisEntityState.ephemeralShared().localCraftOperation());
		
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
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, nextCommit - 1L
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertTrue(listener.events.isEmpty());
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
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(0, listener.entityChangeCount);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		entity = listener.thisEntityState;
		Assert.assertEquals(stoneKey, entity.hotbarItems()[entity.hotbarIndex()]);
		
		// Do the craft and observe it takes multiple actions with no current activity.
		long nextCommit = 1L;
		for (long spent = 0L; spent < 1000L; spent += MILLIS_PER_TICK)
		{
			EntityChangeCraft craft = new EntityChangeCraft(stoneToStoneBrick);
			long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, craft);
			Assert.assertEquals(nextCommit, commit1);
			nextCommit += 1L;
		}
		Assert.assertEquals(nextCommit - 1L, listener.entityChangeCount);
		Assert.assertNull(listener.authoritativeEntityState.ephemeralShared().localCraftOperation());
		Assert.assertNotNull(listener.thisEntityState.ephemeralShared().localCraftOperation());
		
		for (long spent = 0L; spent < 1000L; spent += MILLIS_PER_TICK)
		{
			EntityChangeCraft craft = new EntityChangeCraft(stoneToStoneBrick);
			long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, craft);
			Assert.assertEquals(nextCommit, commit2);
			nextCommit += 1L;
		}
		Assert.assertEquals(nextCommit - 1L, listener.entityChangeCount);
		Assert.assertNull(listener.authoritativeEntityState.ephemeralShared().localCraftOperation());
		Assert.assertNull(listener.thisEntityState.ephemeralShared().localCraftOperation());
		
		// Check the inventory to see the craft completed.
		Inventory inv = listener.authoritativeEntityState.inventory();
		Assert.assertEquals(1, inv.getCount(STONE_ITEM));
		Assert.assertEquals(0, inv.getCount(STONE_BRICK_ITEM));
		inv = listener.thisEntityState.inventory();
		Assert.assertEquals(0, inv.getCount(STONE_ITEM));
		Assert.assertEquals(1, inv.getCount(STONE_BRICK_ITEM));
		entity = listener.thisEntityState;
		Assert.assertEquals(Entity.NO_SELECTION, entity.hotbarItems()[entity.hotbarIndex()]);
		Assert.assertTrue(listener.events.isEmpty());
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
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR))
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, place);
		Assert.assertEquals(1, commit1);
		// (verify that it fails if we try to run it again.
		long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, place);
		Assert.assertEquals(0, commit2);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		Assert.assertEquals(1, listener.lastHeightMap.getHeight(1, 1));
		
		// Verify the events.
		Assert.assertEquals(1, listener.events.size());
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, location, 0, entityId), listener.events.get(0));
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
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR)
						, CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE))
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Place the crafting table.
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 0);
		BlockAddress blockLocation = location.getBlockAddress();
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, place);
		Assert.assertEquals(1L, commit1);
		
		// Store the stones in the inventory.
		MutationEntityPushItems push = new MutationEntityPushItems(location, stoneKey, 2, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, push);
		Assert.assertEquals(2L, commit2);
		
		// Now, craft against the table (it has 10x speed so we will do this in 2 shots).
		EntityChangeCraftInBlock craft = new EntityChangeCraftInBlock(location, stoneToStoneBrick);
		long commit3 = _wrapAndApply(projector, entity, currentTimeMillis, craft);
		Assert.assertEquals(3L, commit3);
		
		// Check the block and all of its aspects.
		Block craftingTable = ENV.blocks.fromItem(ENV.items.getItemById("op.crafting_table"));
		BlockProxy proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(craftingTable, proxy.getBlock());
		Assert.assertEquals(2, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1000L, proxy.getCrafting().completedMillis());
		Assert.assertEquals(0, listener.lastHeightMap.getHeight(1, 1));
		
		// Complete the craft and check the proxy.
		craft = new EntityChangeCraftInBlock(location, null);
		long commit4 = _wrapAndApply(projector, entity, currentTimeMillis, craft);
		Assert.assertEquals(4L, commit4);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(craftingTable, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_BRICK_ITEM));
		Assert.assertNull(proxy.getCrafting());
		
		// Now, break the table and verify that the final inventory state makes sense.
		// We expect the table inventory to spill into the block but the table to end up in the entity's inventory.
		EntityChangeIncrementalBlockBreak breaking = new EntityChangeIncrementalBlockBreak(location);
		long commit5 = _wrapAndApply(projector, entity, currentTimeMillis, breaking);
		Assert.assertEquals(5L, commit5);
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		// Note that the table inventory will spawn as passives, which aren't synthesized in the projection (remove this when empty inventories are completely removed).
		Assert.assertEquals(-1, listener.lastHeightMap.getHeight(1, 1));
		
		Inventory entityInventory = listener.authoritativeEntityState.inventory();
		Assert.assertEquals(2, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(CRAFTING_TABLE_ITEM));
		entityInventory = listener.thisEntityState.inventory();
		Assert.assertEquals(1, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(CRAFTING_TABLE_ITEM));
		
		// Verify the events.
		Assert.assertEquals(2, listener.events.size());
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, location, 0, localEntityId), listener.events.get(0));
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, location, 0, localEntityId), listener.events.get(1));
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
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Verify that this craft should be valid for the inventory.
		Assert.assertTrue(CraftAspect.canApply(stoneBricksToFurnace, inventory));
		
		// But verify that it fails when applied to the entity, directly (as it isn't "trivial").
		EntityChangeCraft craft = new EntityChangeCraft(stoneBricksToFurnace);
		long commit = _wrapAndApply(projector, entity, currentTimeMillis, craft);
		// This should fail to apply.
		Assert.assertEquals(0, commit);
		// There should be no active operation.
		Assert.assertNull(listener.authoritativeEntityState.ephemeralShared().localCraftOperation());
		Assert.assertNull(listener.thisEntityState.ephemeralShared().localCraftOperation());
		Assert.assertTrue(listener.events.isEmpty());
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
				, List.of()
				, List.of(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR)
						, CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE))
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Place the furnace.
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 0);
		BlockAddress blockLocation = location.getBlockAddress();
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(location, location);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, place);
		Assert.assertEquals(1L, commit1);
		
		// Verify that storing stone in fuel inventory fails.
		MutationEntityPushItems pushFail = new MutationEntityPushItems(location, stoneKey, 1, Inventory.INVENTORY_ASPECT_FUEL);
		long commitFail = _wrapAndApply(projector, entity, currentTimeMillis, pushFail);
		Assert.assertEquals(0, commitFail);
		
		// Storing the stone in the normal inventory should work.
		MutationEntityPushItems push = new MutationEntityPushItems(location, stoneKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, push);
		Assert.assertEquals(2L, commit2);
		
		// Verify that we can store the planks in the fuel inventory.
		MutationEntityPushItems pushFuel = new MutationEntityPushItems(location, plankKey, 1, Inventory.INVENTORY_ASPECT_FUEL);
		long commit3 = _wrapAndApply(projector, entity, currentTimeMillis, pushFuel);
		Assert.assertEquals(3L, commit3);
		
		// Check the block and all of its aspects.
		Block furnace = ENV.blocks.fromItem(FURNACE_ITEM);
		BlockProxy proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(furnace, proxy.getBlock());
		Assert.assertEquals(1, proxy.getInventory().getCount(STONE_ITEM));
		Assert.assertEquals(1, proxy.getFuel().fuelInventory().getCount(PLANK_ITEM));
		
		// Now, break the furnace and verify that the final inventory state makes sense.
		// We expect the table inventory to spill into the block but the table to end up in the entity's inventory.
		int hitsToBreak = (int) (ENV.damage.getToughness(furnace) / MILLIS_PER_TICK);
		long nextCommit = 4L;
		for (int i = 0; i < hitsToBreak; ++i)
		{
			EntityChangeIncrementalBlockBreak breaking = new EntityChangeIncrementalBlockBreak(location);
			long commit4 = _wrapAndApply(projector, entity, currentTimeMillis, breaking);
			Assert.assertEquals(nextCommit, commit4);
			nextCommit += 1L;
		}
		proxy = new BlockProxy(blockLocation, listener.lastData);
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		// Note that the furnace's various inventories will spawn as passives, which aren't synthesized in the projection (remove this when empty inventories are completely removed).
		
		Inventory entityInventory = listener.authoritativeEntityState.inventory();
		Assert.assertEquals(3, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(FURNACE_ITEM));
		entityInventory = listener.thisEntityState.inventory();
		Assert.assertEquals(1, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(FURNACE_ITEM));
		
		// Verify the events.
		Assert.assertEquals(2, listener.events.size());
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, location, 0, localEntityId), listener.events.get(0));
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, location, 0, localEntityId), listener.events.get(1));
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
				, List.of()
				, List.of(cuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		EntityLocation secondStep = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation lastStep = new EntityLocation(0.6f, 0.0f, 0.0f);
		EntityActionSimpleMove<IMutablePlayerEntity> move1 = new EntityActionSimpleMove<>(0.2f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT, null);
		EntityActionSimpleMove<IMutablePlayerEntity> move2 = new EntityActionSimpleMove<>(0.2f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT, null);
		EntityActionSimpleMove<IMutablePlayerEntity> move3 = new EntityActionSimpleMove<>(0.2f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT, null);
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
				, Collections.emptyList()
				, new MutationEntitySetEntity(authoritativeMutable.freeze())
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
				, Collections.emptyList()
				, FakeUpdateFactories.entityUpdate(Map.of(cuboid.getCuboidAddress(), cuboid), authoritativeMutable.freeze(), move3)
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit3
				, currentTimeMillis
		);
		
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(lastStep, listener.authoritativeEntityState.location());
		Assert.assertEquals(lastStep, listener.thisEntityState.location());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.thisEntityState.yaw());
		Assert.assertEquals(OrientationHelpers.YAW_EAST, listener.authoritativeEntityState.yaw());
		Assert.assertTrue(listener.events.isEmpty());
	}

	@Test
	public void checkCallbacksForUpdates()
	{
		// We want to check that the update callbacks make sense when the change is conflicting as well as when it is non-conflicting to the same location.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity entity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(1L
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, 1L
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		
		// Create and add an empty cuboid with an inventory block.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 2);
		Block table = ENV.blocks.fromItem(ENV.items.getItemById("op.crafting_table"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), table.item().number());
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(cuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, table.item().number()));
		Assert.assertEquals(Integer.MIN_VALUE, listener.lastHeightMap.getHeight(0, 0));
		
		// Apply a single local mutation.
		AbsoluteLocation blockChangeTarget = target.getRelative(0, 1, 0);
		ReplaceBlockMutation mutation = new ReplaceBlockMutation(blockChangeTarget, ENV.special.AIR.item().number(), STONE_ITEM.number());
		EntityChangeMutation lone = new EntityChangeMutation(mutation);
		long commit = _wrapAndApply(projector, entity, currentTimeMillis, lone);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertTrue(listener.lastChangedBlocks.contains(blockChangeTarget.getBlockAddress()));
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		Assert.assertTrue(listener.lastChangedAspects.contains(AspectRegistry.BLOCK));
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 2));
		
		// Commit an unrelated mutation (from the server) to change the inventory (this is air, so it can hold data and placing the block will overwrite it).
		MutationBlockStoreItems storeItems = new MutationBlockStoreItems(target, new Items(STONE_ITEM, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY);
		int speculativeCount = projector.applyChangesForServerTick(3L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, storeItems))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, 1L
		);
		// Note:  There is a temporary inconsistency here when these changes layer on top of one-another, making it look
		// like there is still an inventory in the block but it will be resolved when the final local change commits.
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertTrue(listener.lastChangedBlocks.contains(target.getBlockAddress()));
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		Assert.assertTrue(listener.lastChangedAspects.contains(AspectRegistry.INVENTORY));
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 2));
		
		// Now commit a conflicting change and notice that there is still inconsistency.
		ReplaceBlockMutation conflict = new ReplaceBlockMutation(blockChangeTarget, ENV.special.AIR.item().number(), LOG_ITEM.number());
		speculativeCount = projector.applyChangesForServerTick(4L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, conflict))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, 1L
		);
		// The speculative change will be there until we see the commit level and we should see the change from the conflict writing (since the wrapper we use always returns success).
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		// The local change will be written on top of the authoritative server change but will become eventually consistent.
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(0, _countBlocks(listener.lastData, LOG_ITEM.number()));
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertEquals(1, listener.lastChangedAspects.size());
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 2));
		
		// Account for the commit level update.
		speculativeCount = projector.applyChangesForServerTick(5L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit
				, 1L
		);
		// This should retire the change with no updates but won't become consistent for another tick.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(0, _countBlocks(listener.lastData, LOG_ITEM.number()));
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 2));
		Assert.assertEquals(4, listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress()).currentEncumbrance);
		Assert.assertTrue(listener.events.isEmpty());
		
		// Run another 2 ticks to let the follow-up changes become consistent.
		speculativeCount = projector.applyChangesForServerTick(6L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit
				, 1L
		);
		speculativeCount = projector.applyChangesForServerTick(7L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit
				, 1L
		);
		// Now, we should see another update to make the block consistent.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, STONE_ITEM.number()));
		Assert.assertEquals(1, _countBlocks(listener.lastData, LOG_ITEM.number()));
		Assert.assertEquals(2, listener.lastHeightMap.getHeight(0, 2));
		Assert.assertEquals(4, listener.lastData.getDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress()).currentEncumbrance);
		Assert.assertTrue(listener.events.isEmpty());
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
				, List.of()
				, List.of(scratchCuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
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
		Assert.assertNull(listener.lastChangedAspects);
		
		// Make some updates to the scratch cuboid and write them to the projection.
		AbsoluteLocation blockLocation = new AbsoluteLocation(1, 2, 3);
		MutableBlockProxy blockProxy = new MutableBlockProxy(blockLocation, scratchCuboid);
		blockProxy.setBlockAndClear(STONE);
		Assert.assertTrue(blockProxy.didChange());
		AbsoluteLocation lightLocation = new AbsoluteLocation(4, 5, 6);
		MutableBlockProxy lightProxy = new MutableBlockProxy(lightLocation, scratchCuboid);
		lightProxy.setLight((byte)10);
		Assert.assertTrue(lightProxy.didChange());
		AbsoluteLocation inventoryLocation = new AbsoluteLocation(7, 8, 9);
		MutableBlockProxy inventoryProxy = new MutableBlockProxy(inventoryLocation, scratchCuboid);
		inventoryProxy.setInventory(Inventory.start(10).addStackable(STONE.item(), 5).finish());
		Assert.assertTrue(inventoryProxy.didChange());
		ByteBuffer scratchBuffer = ByteBuffer.allocate(1024);
		projector.applyChangesForServerTick(2L
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, List.of(
						MutationBlockSetBlock.extractFromProxy(scratchBuffer, blockProxy),
						MutationBlockSetBlock.extractFromProxy(scratchBuffer, lightProxy),
						MutationBlockSetBlock.extractFromProxy(scratchBuffer, inventoryProxy)
				)
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, 2L
		);
		Assert.assertEquals(address, listener.lastData.getCuboidAddress());
		Assert.assertEquals(3, listener.lastChangedBlocks.size());
		Assert.assertEquals(3, listener.lastChangedAspects.size());
		Assert.assertEquals(3, listener.lastHeightMap.getHeight(1, 2));
		Assert.assertTrue(listener.events.isEmpty());
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
				, List.of()
				, List.of(airCuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertNotNull(listener.authoritativeEntityState);
		Assert.assertNotNull(listener.thisEntityState);
		EntityLocation initialLocation = listener.authoritativeEntityState.location();
		
		// Change orientation and move, locally.
		byte yaw = -34;
		byte pitch = 20;
		EntityLocation targetLocation = new EntityLocation(0.24f, 0.22f, 0.0f);
		EntityActionSimpleMove<IMutablePlayerEntity> move = new EntityActionSimpleMove<>(0.24f, 0.22f, EntityActionSimpleMove.Intensity.WALKING, yaw, pitch, null);
		long commit1 = projector.applyLocalChange(move, currentTimeMillis);
		Assert.assertEquals(1L, commit1);
		
		// We should see the entity moved to its speculative location (but only in projection).
		Assert.assertEquals(initialLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(targetLocation, listener.thisEntityState.location());
		
		// Absorb the change and observe.
		int speculativeCount = projector.applyChangesForServerTick(2L
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, FakeUpdateFactories.entityUpdate(Map.of(airAddress, airCuboid), listener.authoritativeEntityState, move)
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, commit1
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(yaw, listener.thisEntityState.yaw());
		Assert.assertEquals(pitch, listener.thisEntityState.pitch());
		Assert.assertEquals(yaw, listener.authoritativeEntityState.yaw());
		Assert.assertEquals(pitch, listener.authoritativeEntityState.pitch());
		Assert.assertEquals(targetLocation, listener.authoritativeEntityState.location());
		Assert.assertEquals(targetLocation, listener.thisEntityState.location());
		Assert.assertTrue(listener.events.isEmpty());
	}

	@Test
	public void attackEvents()
	{
		// Show that attack events come through sensibly, when both inflicted by and against the local entity.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity localEntity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(localEntity);
		int creatureId = -1;
		CreatureEntity orc = CreatureEntity.create(creatureId, ORC, new EntityLocation(1.0f, 0.0f, 0.0f), 0L);
		long currentTimeMillis = 1L;
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		projector.applyChangesForServerTick(1L
				, List.of(PartialEntity.fromCreature(orc))
				, List.of()
				, List.of(airCuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		
		// Attack the orc locally and verify that we don't see any events (since we don't apply changes to other entities in the projection).
		currentTimeMillis += 1000L;
		EntityChangeAttackEntity attack = new EntityChangeAttackEntity(creatureId);
		long commit = _wrapAndApply(projector, localEntity, currentTimeMillis, attack);
		Assert.assertEquals(1L, commit);
		Assert.assertTrue(listener.events.isEmpty());
		
		// Synthesize the events coming from the server along with some basic data updates and verify we see both events.
		MutableEntity mutable = MutableEntity.existing(localEntity);
		mutable.newHealth -= 10;
		localEntity = mutable.freeze();
		MutableCreature mutableCreature = MutableCreature.existing(orc);
		mutableCreature.newHealth -= 10;
		orc = mutableCreature.freeze();
		currentTimeMillis += 100L;
		projector.applyChangesForServerTick(2L
				, List.of()
				, List.of()
				, List.of()
				, new MutationEntitySetEntity(localEntity)
				, Map.of(orc.id(), new MutationEntitySetPartialEntity(PartialEntity.fromCreature(orc)))
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, localEntity.location().getBlockLocation(), localEntity.id(), orc.id())
						, new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, orc.location().getBlockLocation(), orc.id(), localEntity.id())
				)
				, commit
				, currentTimeMillis
		);
		Assert.assertEquals(2, listener.events.size());
	}

	@Test
	public void breakWithIncrementalCommit()
	{
		// We want to test that everything works correctly (including receiving the block) when we fully break locally, then slowly absorb server changes.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Item dirt = ENV.items.getItemById("op.dirt");
		
		AbsoluteLocation entityLocation = new AbsoluteLocation(5, 5, 5);
		AbsoluteLocation dirtLocation = new AbsoluteLocation(5, 6, 5);
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, entityLocation.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress(), dirt.number());
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		long currentTimeMillis = 1L;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = entityLocation.toEntityLocation();
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long gameTick = 1L;
		projector.applyChangesForServerTick(gameTick
				, List.of()
				, List.of()
				, List.of(cuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Now, break the block over 2 increments and make sure that we see the block end up in the inventory.
		int hitsToBreak = (int) (ENV.damage.getToughness(ENV.blocks.fromItem(dirt)) / MILLIS_PER_TICK);
		for (int i = 0; i < hitsToBreak; ++i)
		{
			Assert.assertEquals(0, listener.events.size());
			currentTimeMillis += 100L;
			EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(dirtLocation);
			long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, blockBreak);
			Assert.assertEquals(i + 1, commit1);
		}
		
		Assert.assertEquals(1, listener.events.size());
		Assert.assertEquals(EventRecord.Type.BLOCK_BROKEN, listener.events.get(0).type());
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
		Assert.assertEquals(1, listener.thisEntityState.inventory().getCount(dirt));
		MutationPlaceSelectedBlock placeBlock = new MutationPlaceSelectedBlock(dirtLocation, dirtLocation);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, placeBlock);
		Assert.assertEquals(hitsToBreak + 1, commit1);
		Assert.assertEquals(dirt.number(), listener.lastData.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.lastData.getData15(AspectRegistry.DAMAGE, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.thisEntityState.inventory().getCount(dirt));
		Assert.assertEquals(2, listener.events.size());
		Assert.assertEquals(EventRecord.Type.BLOCK_PLACED, listener.events.get(1).type());
		
		// Now, feed in the updates, one at a time, and make sure the result is the same after each one.
		for (int i = 0; i < hitsToBreak; ++i)
		{
			currentTimeMillis += 100L;
			gameTick += 1L;
			long commit = i + 1;
			List<MutationBlockSetBlock> list = (i > 0)
					? List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(dirtLocation, (short)MILLIS_PER_TICK, entityId)))
					: List.of()
			;
			int speculativeCount = projector.applyChangesForServerTick(gameTick
					, Collections.emptyList()
					, Collections.emptyList()
					, Collections.emptyList()
					, null
					, Map.of()
					, Map.of()
					, list
					, Collections.emptyList()
					, Collections.emptyList()
					, Collections.emptyList()
					, List.of()
					, commit
					, currentTimeMillis
			);
			Assert.assertEquals(hitsToBreak - i, speculativeCount);
			Assert.assertEquals(dirt.number(), listener.lastData.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
			Assert.assertEquals(0, listener.lastData.getData15(AspectRegistry.DAMAGE, dirtLocation.getBlockAddress()));
			Assert.assertEquals(0, listener.thisEntityState.inventory().getCount(dirt));
		}
		
		currentTimeMillis += 100L;
		gameTick += 1L;
		// This is an odd one:  We need to pass another tick to give time for the block to break, then a tick to pick up the item, then we can ack the commit to place it.
		int speculativeCount = projector.applyChangesForServerTick(gameTick
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(dirtLocation, (short)MILLIS_PER_TICK, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, hitsToBreak
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(dirt.number(), listener.lastData.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.lastData.getData15(AspectRegistry.DAMAGE, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.thisEntityState.inventory().getCount(dirt));
		
		currentTimeMillis += 100L;
		gameTick += 1L;
		// Now we pick up the block.
		speculativeCount = projector.applyChangesForServerTick(gameTick
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, FakeUpdateFactories.entityUpdate(Map.of(address, serverCuboid), listener.thisEntityState, new EntityActionStoreToInventory(new Items(dirt, 1), null))
				, Map.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, hitsToBreak
				, currentTimeMillis
		);
		Assert.assertEquals(1, speculativeCount);
		Assert.assertEquals(dirt.number(), listener.lastData.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.lastData.getData15(AspectRegistry.DAMAGE, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.thisEntityState.inventory().getCount(dirt));
		
		currentTimeMillis += 100L;
		gameTick += 1L;
		// Finally, we can commit the last change.
		speculativeCount = projector.applyChangesForServerTick(gameTick
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, FakeUpdateFactories.entityUpdate(Map.of(address, serverCuboid), listener.thisEntityState, _wrap(listener.thisEntityState, new MutationPlaceSelectedBlock(dirtLocation, dirtLocation)))
				, Map.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, hitsToBreak + 1L
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(dirt.number(), listener.lastData.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.lastData.getData15(AspectRegistry.DAMAGE, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.thisEntityState.inventory().getCount(dirt));
		
		currentTimeMillis += 100L;
		gameTick += 1L;
		// To wrap things up, we can now place the block so the follow-up should be done.
		speculativeCount = projector.applyChangesForServerTick(gameTick
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockOverwriteByEntity(dirtLocation, ENV.blocks.getAsPlaceableBlock(dirt), null, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, hitsToBreak + 1L
				, currentTimeMillis
		);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(dirt.number(), listener.lastData.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.lastData.getData15(AspectRegistry.DAMAGE, dirtLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.thisEntityState.inventory().getCount(dirt));
		Assert.assertEquals(2, listener.events.size());
	}

	@Test
	public void breakBlockNearLight()
	{
		// We want to break a block and observe that a nearby light source flows immediately.
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		Block torch = ENV.blocks.fromItem(ENV.items.getItemById("op.torch"));
		CountingListener listener = new CountingListener();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, 1.0f);
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		
		AbsoluteLocation entityLocation = mutable.newLocation.getBlockLocation();
		AbsoluteLocation targetLocation = new AbsoluteLocation(1, 3, 1);
		AbsoluteLocation torchLocation = new AbsoluteLocation(1, 4, 1);
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData serverCuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		serverCuboid.setData15(AspectRegistry.BLOCK, entityLocation.getBlockAddress(), ENV.special.AIR.item().number());
		serverCuboid.setData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress(), dirt.item().number());
		serverCuboid.setData15(AspectRegistry.BLOCK, torchLocation.getBlockAddress(), torch.item().number());
		serverCuboid.setData7(AspectRegistry.LIGHT, torchLocation.getBlockAddress(), LightAspect.MAX_LIGHT);
		Assert.assertEquals(0, serverCuboid.getData7(AspectRegistry.LIGHT, targetLocation.getBlockAddress()));
		Assert.assertEquals(0, serverCuboid.getData7(AspectRegistry.LIGHT, entityLocation.getBlockAddress()));
		
		long currentTimeMillis = 1L;
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long gameTick = 1L;
		projector.applyChangesForServerTick(gameTick
				, List.of()
				, List.of()
				, List.of(CuboidData.mutableClone(serverCuboid))
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Break the block, observing a lighting update in the last change.
		int hitsToBreak = (int) (ENV.damage.getToughness(dirt) / MILLIS_PER_TICK);
		long nextCommit = 1L;
		int changes = 0;
		for (int i = 0; i < hitsToBreak; ++i)
		{
			EntityChangeIncrementalBlockBreak blockBreak = new EntityChangeIncrementalBlockBreak(targetLocation);
			long commitNumber = _wrapAndApply(projector, entity, currentTimeMillis, blockBreak);
			Assert.assertEquals(nextCommit, commitNumber);
			changes += 1;
			Assert.assertEquals(changes, listener.changeCount);
			Assert.assertEquals(1, listener.lastChangedBlocks.size());
			nextCommit += 1L;
			if ((i + 1) == hitsToBreak)
			{
				Assert.assertTrue(listener.lastChangedAspects.contains(AspectRegistry.LIGHT));
			}
			else
			{
				Assert.assertFalse(listener.lastChangedAspects.contains(AspectRegistry.LIGHT));
			}
		}
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT, listener.lastData.getData7(AspectRegistry.LIGHT, torchLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT - 1, listener.lastData.getData7(AspectRegistry.LIGHT, targetLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT - 2, listener.lastData.getData7(AspectRegistry.LIGHT, entityLocation.getBlockAddress()));
		
		// This update will technically take 3 ticks (entity, block, light), so apply those changes in sequence.
		gameTick += 1L;
		projector.applyChangesForServerTick(gameTick
				, List.of()
				, List.of()
				, List.of()
				, null
				, Map.of()
				, Map.of()
				, List.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, (nextCommit - 1L)
				, currentTimeMillis
		);
		Assert.assertEquals(changes, listener.changeCount);
		
		gameTick += 1L;
		projector.applyChangesForServerTick(gameTick
				, List.of()
				, List.of()
				, List.of()
				, null
				, Map.of()
				, Map.of()
				, List.of(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(targetLocation, (short)200, entityId)))
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, (nextCommit - 1L)
				, currentTimeMillis
		);
		Assert.assertEquals(changes, listener.changeCount);
		
		// The lighting update requires a hard-coded update - note that a light-only change will NOT trigger another change callback since we already reported that.
		MutationBlockSetBlock targetLighting = new MutationBlockSetBlock(targetLocation, new byte[] {(byte)AspectRegistry.LIGHT.index(), LightAspect.MAX_LIGHT - 1});
		MutationBlockSetBlock entityLighting = new MutationBlockSetBlock(entityLocation, new byte[] {(byte)AspectRegistry.LIGHT.index(), LightAspect.MAX_LIGHT - 2});
		gameTick += 1L;
		projector.applyChangesForServerTick(gameTick
				, List.of()
				, List.of()
				, List.of()
				, null
				, Map.of()
				, Map.of()
				, List.of(targetLighting, entityLighting)
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, (nextCommit - 1L)
				, currentTimeMillis
		);
		Assert.assertEquals(changes, listener.changeCount);
		Assert.assertEquals(ENV.special.AIR.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT, listener.lastData.getData7(AspectRegistry.LIGHT, torchLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT - 1, listener.lastData.getData7(AspectRegistry.LIGHT, targetLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT - 2, listener.lastData.getData7(AspectRegistry.LIGHT, entityLocation.getBlockAddress()));
		
		// Verify the events.
		Assert.assertEquals(1, listener.events.size());
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, targetLocation, 0, entityId), listener.events.get(0));
	}

	@Test
	public void placeBlockNearLight()
	{
		// Place a block near a light source and show that the light is immediately blocked.
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		Block torch = ENV.blocks.fromItem(ENV.items.getItemById("op.torch"));
		CountingListener listener = new CountingListener();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(dirt.item(), 1);
		mutable.setSelectedKey(1);
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, 1.0f);
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		
		AbsoluteLocation entityLocation = mutable.newLocation.getBlockLocation();
		AbsoluteLocation targetLocation = new AbsoluteLocation(1, 3, 1);
		AbsoluteLocation torchLocation = new AbsoluteLocation(1, 4, 1);
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, torchLocation.getBlockAddress(), torch.item().number());
		cuboid.setData7(AspectRegistry.LIGHT, torchLocation.getBlockAddress(), LightAspect.MAX_LIGHT);
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData7(AspectRegistry.LIGHT, targetLocation.getBlockAddress(), (byte)(LightAspect.MAX_LIGHT - 1));
		cuboid.setData15(AspectRegistry.BLOCK, entityLocation.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData7(AspectRegistry.LIGHT, entityLocation.getBlockAddress(), (byte)(LightAspect.MAX_LIGHT - 2));
		
		long currentTimeMillis = 1L;
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of()
				, List.of(cuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Place the block and verify the other blocks go dark.
		currentTimeMillis += 100L;
		MutationPlaceSelectedBlock placeBlock = new MutationPlaceSelectedBlock(targetLocation, torchLocation);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, placeBlock);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		// Note that there was also a lighting change, but those aren't reported.
		Assert.assertEquals(1, listener.lastChangedBlocks.size());
		Assert.assertTrue(listener.lastChangedAspects.contains(AspectRegistry.LIGHT));
		Assert.assertEquals(dirt.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT, listener.lastData.getData7(AspectRegistry.LIGHT, torchLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.lastData.getData7(AspectRegistry.LIGHT, targetLocation.getBlockAddress()));
		Assert.assertEquals(0, listener.lastData.getData7(AspectRegistry.LIGHT, entityLocation.getBlockAddress()));
		
		// Verify the events.
		Assert.assertEquals(1, listener.events.size());
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, targetLocation, 0, entityId), listener.events.get(0));
	}

	@Test
	public void perfLightChanges()
	{
		// Places and breaks a lantern on the boundary of 2 cuboids, over and over.  The number of iterations can be increased to make this a good stress test for profiling.
		Block dirt = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		Block lantern = ENV.blocks.fromItem(ENV.items.getItemById("op.lantern"));
		CountingListener listener = new CountingListener();
		AbsoluteLocation entityLocation = new AbsoluteLocation(31, 15, 15);
		AbsoluteLocation dirtLocation = entityLocation.getRelative(0, 0, -1);
		AbsoluteLocation lanternLocation = entityLocation.getRelative(0, 0, 2);
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(lantern.item(), 1);
		mutable.setSelectedKey(1);
		mutable.newLocation = entityLocation.toEntityLocation();
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		cuboid0.setData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress(), dirt.item().number());
		CuboidAddress address1 = CuboidAddress.fromInt(1, 0, 0);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		
		long gameTick = 1L;
		long currentTimeMillis = 1L;
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(gameTick
				, List.of()
				, List.of()
				, List.of(cuboid0, cuboid1)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		
		// We now do the loop - note that we will ask that we will limit the number of speculative changes to 2, just for simplicity.
		int iterationCount = 10;
		long nextCommit = 1L;
		for (int i = 0; i < iterationCount; ++i)
		{
			currentTimeMillis += 100L;
			MutationPlaceSelectedBlock placeBlock = new MutationPlaceSelectedBlock(lanternLocation, lanternLocation);
			long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, placeBlock);
			Assert.assertEquals(nextCommit, commit1);
			nextCommit += 1;
			
			currentTimeMillis += 100L;
			EntityChangeIncrementalBlockBreak breakBlock = new EntityChangeIncrementalBlockBreak(lanternLocation);
			long commit2 = _wrapAndApply(projector, entity, currentTimeMillis, breakBlock);
			Assert.assertEquals(nextCommit, commit2);
			nextCommit += 1;
			
			gameTick += 1;
			projector.applyChangesForServerTick(gameTick
					, List.of()
					, List.of()
					, List.of()
					, null
					, Collections.emptyMap()
					, Collections.emptyMap()
					, Collections.emptyList()
					, Collections.emptyList()
					, Collections.emptyList()
					, Collections.emptyList()
					, List.of()
					, nextCommit - 1L
					, currentTimeMillis
			);
		}
	}

	@Test
	public void placeMultiBlock()
	{
		// Show what happens when we place a multi-block door.
		Block door = ENV.blocks.fromItem(ENV.items.getItemById("op.double_door_base"));
		CountingListener listener = new CountingListener();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(door.item(), 1);
		mutable.setSelectedKey(1);
		mutable.newLocation = new EntityLocation(5.0f, 6.0f, 0.0f);
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		
		AbsoluteLocation entityLocation = mutable.newLocation.getBlockLocation();
		AbsoluteLocation targetLocation = entityLocation.getRelative(1, 1, 0);
		CuboidData base = CuboidGenerator.createFilledCuboid(entityLocation.getCuboidAddress().getRelative(0, 0, -1), STONE);
		CuboidData top = CuboidGenerator.createFilledCuboid(entityLocation.getCuboidAddress(), ENV.special.AIR);
		
		long currentTimeMillis = 1L;
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		projector.applyChangesForServerTick(1L
				, List.of()
				, List.of()
				, List.of(base, top)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		Assert.assertEquals(2, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Place the block and verify the full placement completes.
		currentTimeMillis += 100L;
		EntityChangePlaceMultiBlock placeBlock = new EntityChangePlaceMultiBlock(targetLocation, OrientationAspect.Direction.EAST);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, placeBlock);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(1, listener.changeCount);
		Assert.assertEquals(4, listener.lastChangedBlocks.size());
		Assert.assertEquals(door.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
		Assert.assertEquals(door.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, -1, 0).getBlockAddress()));
		Assert.assertEquals(door.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, -1, 1).getBlockAddress()));
		Assert.assertEquals(door.item().number(), listener.lastData.getData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, 1).getBlockAddress()));
		
		// Verify the events.
		Assert.assertEquals(1, listener.events.size());
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, targetLocation, 0, entityId), listener.events.get(0));
	}

	@Test
	public void dropItemAsPassive() throws Throwable
	{
		// We should see the inventory change immediately but the passive will not appear (as it comes from the server).
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		mutable.setSelectedKey(1);
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, 1.0f);
		
		AbsoluteLocation entityLocation = mutable.newLocation.getBlockLocation();
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, entityLocation.getBlockAddress(), ENV.special.AIR.item().number());
		
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity entity = mutable.freeze();
		projector.setThisEntity(entity);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
			, List.of()
			, List.of()
			, List.of(cuboid)
			, null
			, Collections.emptyMap()
			, Collections.emptyMap()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, listener.changeCount);
		
		// Run the sub-action to drop the items
		currentTimeMillis += 100L;
		EntitySubActionDropItemsAsPassive drop = new EntitySubActionDropItemsAsPassive(1, true);
		long commit1 = _wrapAndApply(projector, entity, currentTimeMillis, drop);
		Assert.assertEquals(1, commit1);
		Assert.assertEquals(0, listener.changeCount);
		
		Entity result = listener.thisEntityState;
		Assert.assertEquals(0, result.inventory().currentEncumbrance);
		Assert.assertEquals(0, MutableEntity.existing(result).getSelectedKey());
	}

	@Test
	public void passiveCallbacks() throws Throwable
	{
		// Just show that we see the expected listener callbacks for plumbing passives through the projection.
		// We will plumb in the entity, since much of the projection assumes it is special.
		Entity entity = MutableEntity.createForTest(1).freeze();
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(entity.id(), listener, MILLIS_PER_TICK);
		projector.setThisEntity(entity);
		
		// Load a passive.
		Items stack = new Items(STONE_ITEM, 3);
		PartialPassive startPassive = new PartialPassive(1
			, PassiveType.ITEM_SLOT
			, new EntityLocation(1.2f, -4.5f, 7.9f)
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, ItemSlot.fromStack(stack)
		);
		long currentTimeMillis = 1L;
		projector.applyChangesForServerTick(1L
			, List.of()
			, List.of(startPassive)
			, List.of()
			, null
			, Collections.emptyMap()
			, Collections.emptyMap()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(1, listener.passiveEntityStates.size());
		Assert.assertEquals(startPassive.location(), listener.passiveEntityStates.get(startPassive.id()).location());
		Assert.assertEquals(stack, ((ItemSlot)listener.passiveEntityStates.get(startPassive.id()).extendedData()).stack);
		
		// Show the changed passive.
		PassiveUpdate update = new PassiveUpdate(new EntityLocation(1.2f, -4.5f, 7.8f), new EntityLocation(0.0f, 0.0f, -1.0f));
		currentTimeMillis += 1L;
		projector.applyChangesForServerTick(2L
			, List.of()
			, List.of()
			, List.of()
			, null
			, Map.of()
			, Map.of(startPassive.id(), update)
			, List.of()
			, List.of()
			, List.of()
			, List.of()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(1, listener.passiveEntityStates.size());
		Assert.assertEquals(update.location(), listener.passiveEntityStates.get(startPassive.id()).location());
		Assert.assertEquals(stack, ((ItemSlot)listener.passiveEntityStates.get(startPassive.id()).extendedData()).stack);
		
		// Show that the passive disappeared.
		currentTimeMillis += 1L;
		projector.applyChangesForServerTick(3L
			, List.of()
			, List.of()
			, List.of()
			, null
			, Map.of()
			, Map.of()
			, List.of()
			, List.of()
			, List.of(startPassive.id())
			, List.of()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(0, listener.passiveEntityStates.size());
	}

	@Test
	public void calfGrowUp()
	{
		// We want to show that a loaded entity changing into a different type is acceptable.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		Entity localEntity = MutableEntity.createForTest(entityId).freeze();
		projector.setThisEntity(localEntity);
		int creatureId = -1;
		EntityType babyCow = ENV.creatures.getTypeById("op.cow_baby");
		CreatureEntity baby = CreatureEntity.create(creatureId, babyCow, new EntityLocation(1.0f, 0.0f, 0.0f), 0L);
		long currentTimeMillis = 1L;
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		projector.applyChangesForServerTick(1L
				, List.of(PartialEntity.fromCreature(baby))
				, List.of()
				, List.of(airCuboid)
				, null
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		PartialEntity partial = listener.otherEntityStates.get(creatureId);
		Assert.assertEquals(babyCow, partial.type());
		Assert.assertEquals(babyCow.maxHealth(), partial.health());
		
		// Show that an update which changes the type for this ID is accepted.
		EntityType cow = ENV.creatures.getTypeById("op.cow");
		CreatureEntity adult = CreatureEntity.create(creatureId, cow, new EntityLocation(1.0f, 0.0f, 0.0f), 0L);
		currentTimeMillis += 100L;
		projector.applyChangesForServerTick(2L
				, List.of()
				, List.of()
				, List.of()
				, new MutationEntitySetEntity(localEntity)
				, Map.of(creatureId, new MutationEntitySetPartialEntity(PartialEntity.fromCreature(adult)))
				, Map.of()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, Collections.emptyList()
				, List.of()
				, 0L
				, currentTimeMillis
		);
		partial = listener.otherEntityStates.get(creatureId);
		Assert.assertEquals(cow, partial.type());
		Assert.assertEquals(cow.maxHealth(), partial.health());
	}

	@Test
	public void chargeState()
	{
		// Show that the charge state is correctly replicated from the server.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		Item bowItem = ENV.items.getItemById("op.bow");
		Item arrowItem = ENV.items.getItemById("op.arrow");
		NonStackableItem bow = PropertyHelpers.newItemWithDefaults(ENV, bowItem);
		mutable.newInventory.addNonStackableAllowingOverflow(bow);
		mutable.newInventory.addAllItems(arrowItem, 10);
		mutable.setSelectedKey(1);
		Entity localEntity = mutable.freeze();
		projector.setThisEntity(localEntity);
		long currentTimeMillis = 1L;
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		int remaining = projector.applyChangesForServerTick(1L
			, List.of()
			, List.of()
			, List.of(airCuboid)
			, null
			, Collections.emptyMap()
			, Collections.emptyMap()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(0, remaining);
		
		// We will send another update from the server, as though the "charge weapon" had already happened.
		mutable = MutableEntity.existing(localEntity);
		mutable.chargeMillis += MILLIS_PER_TICK;
		localEntity = mutable.freeze();
		currentTimeMillis += MILLIS_PER_TICK;
		remaining = projector.applyChangesForServerTick(2L
			, List.of()
			, List.of()
			, List.of()
			, new MutationEntitySetEntity(localEntity)
			, Map.of()
			, Map.of()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(0, remaining);
		Assert.assertEquals(MILLIS_PER_TICK, listener.thisEntityState.ephemeralShared().chargeMillis());
		
		// Now, apply the release and make sure that it passes.
		currentTimeMillis += MILLIS_PER_TICK;
		EntitySubActionReleaseWeapon release = new EntitySubActionReleaseWeapon();
		long commit1 = _wrapAndApply(projector, localEntity, currentTimeMillis, release);
		Assert.assertEquals(1L, commit1);
		Assert.assertEquals(0, listener.thisEntityState.ephemeralShared().chargeMillis());
	}

	@Test
	public void interleaveServerFieldChanges()
	{
		// We want to show that changes from the server which don't conflict with local changes are still observed in the output.
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(1.0f, 1.0f, 0.0f);
		mutable.newHealth = (byte)50;
		mutable.newYaw = (byte)0;
		mutable.newPitch = (byte)0;
		Entity localEntity = mutable.freeze();
		projector.setThisEntity(localEntity);
		long currentTimeMillis = 1L;
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		int remaining = projector.applyChangesForServerTick(1L
			, List.of()
			, List.of()
			, List.of(airCuboid)
			, null
			, Collections.emptyMap()
			, Collections.emptyMap()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(0, remaining);
		
		// We will just look around in a local change and then show that a health and location change from the server is still observed.
		byte yaw = 30;
		byte pitch = -10;
		EntityActionSimpleMove<IMutablePlayerEntity> lookAround = new EntityActionSimpleMove<>(0.0f
			, 0.0f
			, EntityActionSimpleMove.Intensity.STANDING
			, yaw
			, pitch
			, null
		);
		long commit = projector.applyLocalChange(lookAround, currentTimeMillis);
		Assert.assertEquals(1L, commit);
		Assert.assertEquals((byte)50, listener.thisEntityState.health());
		Assert.assertEquals(yaw, listener.thisEntityState.yaw());
		Assert.assertEquals(new EntityLocation(1.0f, 1.0f, 0.0f), listener.thisEntityState.location());
		
		// Show the changes from the server mix correctly.
		mutable = MutableEntity.existing(localEntity);
		mutable.newLocation = new EntityLocation(2.0f, 2.0f, 0.0f);
		mutable.newHealth = (byte)80;
		localEntity = mutable.freeze();
		currentTimeMillis += MILLIS_PER_TICK;
		remaining = projector.applyChangesForServerTick(2L
			, List.of()
			, List.of()
			, List.of()
			, new MutationEntitySetEntity(localEntity)
			, Map.of()
			, Map.of()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(1, remaining);
		Assert.assertEquals((byte)80, listener.thisEntityState.health());
		Assert.assertEquals(yaw, listener.thisEntityState.yaw());
		Assert.assertEquals(new EntityLocation(2.0f, 2.0f, 0.0f), listener.thisEntityState.location());
	}

	@Test
	public void accelerateOnServer()
	{
		// Show that acceleration coming from the server (knockback, for example) is accounted for on the client-side movement.
		// Note that, while player location is owned client-side (to allow tight timing in movement under heavy network latency),
		// acceleration can be changed by the server (as this is where things like knockback, nuge, etc are applied).
		CountingListener listener = new CountingListener();
		int entityId = 1;
		SpeculativeProjection projector = new SpeculativeProjection(entityId, listener, MILLIS_PER_TICK);
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(1.0f, 1.0f, 0.0f);
		Entity localEntity = mutable.freeze();
		projector.setThisEntity(localEntity);
		long currentTimeMillis = 1L;
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		int remaining = projector.applyChangesForServerTick(1L
			, List.of()
			, List.of()
			, List.of(airCuboid)
			, null
			, Collections.emptyMap()
			, Collections.emptyMap()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(0, remaining);
		
		// We will move forward and see the client-side location/velocity.
		EntityActionSimpleMove<IMutablePlayerEntity> lookAround = new EntityActionSimpleMove<>(0.0f
			, 0.4f
			, EntityActionSimpleMove.Intensity.WALKING
			, (byte)0
			, (byte)0
			, null
		);
		long commit = projector.applyLocalChange(lookAround, currentTimeMillis);
		Assert.assertEquals(1L, commit);
		Assert.assertEquals(new EntityLocation(1.0f, 1.4f, 0.0f), listener.thisEntityState.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntityState.velocity());
		
		// Apply a velocity change from the server and see what this changes.
		mutable = MutableEntity.existing(localEntity);
		mutable.newVelocity= new EntityLocation(2.0f, 0.0f, 0.0f);
		localEntity = mutable.freeze();
		currentTimeMillis += MILLIS_PER_TICK;
		remaining = projector.applyChangesForServerTick(2L
			, List.of()
			, List.of()
			, List.of()
			, new MutationEntitySetEntity(localEntity)
			, Map.of()
			, Map.of()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, Collections.emptyList()
			, List.of()
			, 0L
			, currentTimeMillis
		);
		Assert.assertEquals(1, remaining);
		Assert.assertEquals(new EntityLocation(1.18f, 1.36f, 0.0f), listener.thisEntityState.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntityState.velocity());
	}


	private static EntityActionSimpleMove<IMutablePlayerEntity> _wrap(Entity entity, IEntitySubAction<IMutablePlayerEntity> change)
	{
		return new EntityActionSimpleMove<>(0.0f
			, 0.0f
			, EntityActionSimpleMove.Intensity.STANDING
			, (byte)0
			, (byte)0
			, change
		);
	}

	private static long _wrapAndApply(SpeculativeProjection projector, Entity entity, long currentTimeMillis, IEntitySubAction<IMutablePlayerEntity> change)
	{
		EntityActionSimpleMove<IMutablePlayerEntity> wrapper = _wrap(entity, change);
		return projector.applyLocalChange(wrapper, currentTimeMillis);
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

	private static class CountingListener implements IProjectionListener
	{
		public int loadCount = 0;
		public int changeCount = 0;
		public int unloadCount = 0;
		public IReadOnlyCuboidData lastData = null;
		public ColumnHeightMap lastHeightMap = null;
		public Set<BlockAddress> lastChangedBlocks = null;
		public Set<Aspect<?, ?>> lastChangedAspects = null;
		public int entityChangeCount = 0;
		public Entity authoritativeEntityState = null;
		public Entity thisEntityState = null;
		public Map<Integer, PartialEntity> otherEntityStates = new HashMap<>();
		public Map<Integer, PartialPassive> passiveEntityStates = new HashMap<>();
		public long lastTickCompleted = 0L;
		public List<EventRecord> events = new ArrayList<>();
		
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap)
		{
			this.loadCount += 1;
			this.lastData = cuboid;
			this.lastHeightMap = columnHeightMap;
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, CuboidHeightMap cuboidHeightMap
				, ColumnHeightMap columnHeightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			// Note that the changed blocks can be empty but only if the changed aspects are NOT light.
			if ((changedAspects.size() > 1) || !changedAspects.contains(AspectRegistry.LIGHT))
			{
				Assert.assertFalse(changedBlocks.isEmpty());
			}
			Assert.assertFalse(changedAspects.isEmpty());
			this.changeCount += 1;
			this.lastData = cuboid;
			this.lastHeightMap = columnHeightMap;
			this.lastChangedBlocks = changedBlocks;
			this.lastChangedAspects = changedAspects;
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
		public void passiveEntityDidLoad(PartialPassive entity)
		{
			Object old = this.passiveEntityStates.put(entity.id(), entity);
			Assert.assertNull(old);
		}
		@Override
		public void passiveEntityDidChange(PartialPassive entity)
		{
			Object old = this.passiveEntityStates.put(entity.id(), entity);
			Assert.assertNotNull(old);
		}
		@Override
		public void passiveEntityDidUnload(int id)
		{
			Object old = this.passiveEntityStates.remove(id);
			Assert.assertNotNull(old);
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
			Assert.assertTrue(gameTick > this.lastTickCompleted);
			this.lastTickCompleted = gameTick;
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			this.events.add(event);
		}
	}
}
