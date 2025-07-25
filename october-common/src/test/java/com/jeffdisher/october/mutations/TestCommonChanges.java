package com.jeffdisher.october.mutations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityChangeApplyItemToCreature;
import com.jeffdisher.october.actions.EntityChangeOperatorSetCreative;
import com.jeffdisher.october.actions.EntityChangeOperatorSetLocation;
import com.jeffdisher.october.actions.EntityChangeOperatorSpawnCreature;
import com.jeffdisher.october.actions.EntityChangePeriodic;
import com.jeffdisher.october.actions.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.actions.MutationEntityStoreToInventory;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.subactions.EntityChangeAcceptItems;
import com.jeffdisher.october.subactions.EntityChangeAttackEntity;
import com.jeffdisher.october.subactions.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.subactions.EntityChangeCraft;
import com.jeffdisher.october.subactions.EntityChangeCraftInBlock;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.subactions.EntityChangeJump;
import com.jeffdisher.october.subactions.EntityChangePlaceMultiBlock;
import com.jeffdisher.october.subactions.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.subactions.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.subactions.EntityChangeSwapArmour;
import com.jeffdisher.october.subactions.EntityChangeSwim;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnEntity;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.subactions.EntitySubActionLadderAscend;
import com.jeffdisher.october.subactions.EntitySubActionLadderDescend;
import com.jeffdisher.october.subactions.MutationEntityPushItems;
import com.jeffdisher.october.subactions.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.subactions.MutationEntitySelectItem;
import com.jeffdisher.october.subactions.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCommonChanges
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item LOG_ITEM;
	private static Item PLANK_ITEM;
	private static Item CHARCOAL_ITEM;
	private static Item IRON_SWORD_ITEM;
	private static Item IRON_AXE_ITEM;
	private static Block STONE;
	private static Block WATER_SOURCE;
	private static Block LADDER;
	private static EntityType COW;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		LOG_ITEM = ENV.items.getItemById("op.log");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		CHARCOAL_ITEM = ENV.items.getItemById("op.charcoal");
		IRON_SWORD_ITEM = ENV.items.getItemById("op.iron_sword");
		IRON_AXE_ITEM = ENV.items.getItemById("op.iron_axe");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		LADDER = ENV.blocks.fromItem(ENV.items.getItemById("op.ladder"));
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void jumpAndFall()
	{
		// Jump into the air and fall back down.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		TickProcessingContext context = _createSimpleContext();
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		
		EntityChangeJump<IMutablePlayerEntity> jump = new EntityChangeJump<>();
		boolean didApply = jump.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		
		// The jump doesn't move, just sets the vector.
		Assert.assertEquals(EntityChangeJump.JUMP_FORCE, newEntity.newVelocity.z(), 0.01f);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
		
		// Try a few falling steps to see how we sink back to the ground.
		// (we will use 50ms updates to see the more detailed arc)
		for (int i = 0; i < 18; ++i)
		{
			context = _createNextTick(context, 50L);
			_stand(context, newEntity);
			TickUtils.endOfTick(context, newEntity);
			Assert.assertTrue(newEntity.newLocation.z() > 0.0f);
		}
		// The next step puts us back on the ground.
		context = _createNextTick(context, 100L);
		_stand(context, newEntity);
		TickUtils.endOfTick(context, newEntity);
		Assert.assertTrue(0.0f == newEntity.newLocation.z());
		// However, the vector is still drawing us down (since the vector is updated at the beginning of the move, not the end).
		Assert.assertEquals(-4.9f, newEntity.newVelocity.z(), 0.01f);
		
		// Fall one last time to finalize "impact".
		context = _createNextTick(context, 100L);
		_stand(context, newEntity);
		TickUtils.endOfTick(context, newEntity);
		Assert.assertEquals(0.0f, newEntity.newLocation.z(), 0.01f);
		Assert.assertEquals(0.0f, newEntity.newVelocity.z(), 0.01f);
	}

	@Test
	public void selection() throws Throwable
	{
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		
		// We will create a bogus context which just says that they are standing in a wall so they don't try to move.
		TickProcessingContext context = _createSimpleContext();
		
		// Give the entity some items and verify that they default to selected.
		EntityChangeAcceptItems accept = new EntityChangeAcceptItems(new Items(LOG_ITEM, 1));
		Assert.assertTrue(accept.applyChange(context, newEntity));
		Assert.assertEquals(LOG_ITEM, _selectedItemType(newEntity));
		
		// We want to capture the key for the log so we can try to reference it later.
		int logKey = newEntity.newInventory.getIdOfStackableType(LOG_ITEM);
		
		// Craft some items to use these up and verify that the selection is cleared.
		for (long spent = 0L; spent < logToPlanks.millisPerCraft; spent += context.millisPerTick)
		{
			EntityChangeCraft craft = new EntityChangeCraft(logToPlanks);
			Assert.assertTrue(craft.applyChange(context, newEntity));
		}
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.getSelectedKey());
		
		// Actively select the type and verify it is selected.
		MutationEntitySelectItem select = new MutationEntitySelectItem(newEntity.newInventory.getIdOfStackableType(PLANK_ITEM));
		Assert.assertTrue(select.applyChange(context, newEntity));
		Assert.assertEquals(PLANK_ITEM, _selectedItemType(newEntity));
		
		// Demonstrate that we can't select something we don't have (the logs we just used).
		MutationEntitySelectItem select2 = new MutationEntitySelectItem(logKey);
		Assert.assertFalse(select2.applyChange(context, newEntity));
		Assert.assertEquals(PLANK_ITEM, _selectedItemType(newEntity));
		
		// Show that we can unselect.
		MutationEntitySelectItem select3 = new MutationEntitySelectItem(Entity.NO_SELECTION);
		Assert.assertTrue(select3.applyChange(context, newEntity));
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.getSelectedKey());
	}

	@Test
	public void placeBlock() throws Throwable
	{
		// Create the entity in an air block so we can place this (give us a starter inventory).
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(LOG_ITEM, 1);
		newEntity.setSelectedKey(newEntity.newInventory.getIdOfStackableType(LOG_ITEM));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 10);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(target, target);
		Assert.assertTrue(place.applyChange(holder.context, newEntity));
		
		// We also need to apply the actual mutation.
		Assert.assertTrue(holder.mutation instanceof MutationBlockOverwriteByEntity);
		AbsoluteLocation location = holder.mutation.getAbsoluteLocation();
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, location, 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		
		// We expect that the block will be placed and our selection and inventory will be cleared.
		Assert.assertEquals(LOG_ITEM.number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(0, newEntity.freeze().inventory().sortedKeys().size());
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.getSelectedKey());
	}

	@Test
	public void pickUpItems() throws Throwable
	{
		// Create an air cuboid with items in an inventory slot and then pick it up.
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		Inventory blockInventory = Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(STONE_ITEM, 2).finish();
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress(), blockInventory);
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// This is a multi-step process which starts by asking the entity to attempt the pick-up.
		int stoneKey = blockInventory.getIdOfStackableType(STONE_ITEM);
		MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(targetLocation, stoneKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(request.applyChange(holder.context, newEntity));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(holder.mutation instanceof MutationBlockExtractItems);
		AbsoluteLocation location = holder.mutation.getAbsoluteLocation();
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, newBlock));
		newBlock.writeBack(cuboid);
		
		// By this point, the entity shouldn't yet have changed.
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(STONE_ITEM));
		Assert.assertEquals(0, newEntity.newInventory.getCount(STONE_ITEM));
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.getSelectedKey());
		
		// We should see the request to store data, now.
		Assert.assertTrue(holder.change instanceof MutationEntityStoreToInventory);
		Assert.assertTrue(holder.change.applyChange(holder.context, newEntity));
		
		// We can now verify the final result of this - we should see the one item moved and selected since nothing else was.
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(STONE_ITEM));
		Assert.assertEquals(1, newEntity.newInventory.getCount(STONE_ITEM));
		Assert.assertEquals(STONE_ITEM, _selectedItemType(newEntity));
		
		// Run the process again to pick up the last item and verify that the inventory is now null.
		holder.mutation = null;
		holder.change = null;
		request = new MutationEntityRequestItemPickUp(targetLocation, stoneKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(request.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, newBlock));
		newBlock.writeBack(cuboid);
		Assert.assertTrue(holder.change.applyChange(holder.context, newEntity));
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertNull(blockInventory);
		Assert.assertEquals(2, newEntity.newInventory.getCount(STONE_ITEM));
		Assert.assertEquals(STONE_ITEM, _selectedItemType(newEntity));
	}

	@Test
	public void dropStackbleItems() throws Throwable
	{
		// Create an air cuboid and an entity with some items, then try to drop them onto a block.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(STONE_ITEM));
		Entity original = mutable.freeze();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		// We need to make sure that there is a solid block under the target location so it doesn't just fall.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// This is a multi-step process which starts by asking the entity to start the drop.
		MutableEntity newEntity = MutableEntity.existing(original);
		MutationEntityPushItems push = new MutationEntityPushItems(targetLocation, newEntity.newInventory.getIdOfStackableType(STONE_ITEM), 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(holder.context, newEntity));
		
		// We can now verify that the entity has lost the item but the block is unchanged.
		Assert.assertEquals(1, newEntity.newInventory.getCount(STONE_ITEM));
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		AbsoluteLocation location = holder.mutation.getAbsoluteLocation();
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, newBlock));
		newBlock.writeBack(cuboid);
		holder.mutation = null;
		
		// By this point, we should be able to verify both the entity and the block.
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.getCount(STONE_ITEM));
		Entity freeze = newEntity.freeze();
		Assert.assertEquals(1, freeze.inventory().getCount(STONE_ITEM));
		Assert.assertEquals(STONE_ITEM, _selectedItemType(newEntity));
		
		// Drop again to verify that this correctly handles dropping the last selected item.
		push = new MutationEntityPushItems(targetLocation, newEntity.newInventory.getIdOfStackableType(STONE_ITEM), 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, newBlock));
		newBlock.writeBack(cuboid);
		
		// By this point, we should be able to verify both the entity and the block.
		blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		freeze = newEntity.freeze();
		Assert.assertEquals(2, blockInventory.getCount(STONE_ITEM));
		Assert.assertEquals(0, freeze.inventory().sortedKeys().size());
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.getSelectedKey());
	}

	@Test
	public void dropNonStackableItems() throws Throwable
	{
		// Create an air cuboid and an entity with an item, then try to drop it onto a block.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		Item pickItem = ENV.items.getItemById("op.iron_pickaxe");
		mutable.newInventory.addNonStackableBestEfforts(new NonStackableItem(pickItem, ENV.durability.getDurability(pickItem)));
		int idOfPick = 1;
		mutable.setSelectedKey(idOfPick);
		Entity original = mutable.freeze();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		// We need to make sure that there is a solid block under the target location so it doesn't just fall.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// This is a multi-step process which starts by asking the entity to start the drop.
		MutableEntity newEntity = MutableEntity.existing(original);
		MutationEntityPushItems push = new MutationEntityPushItems(targetLocation, idOfPick, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(holder.context, newEntity));
		
		// We can now verify that the entity has lost the item but the block is unchanged.
		Assert.assertNull(newEntity.newInventory.getNonStackableForKey(idOfPick));
		Assert.assertEquals(0, newEntity.newInventory.getCurrentEncumbrance());
		Assert.assertEquals(0, newEntity.getSelectedKey());
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()));
		
		// We should see the mutation requested and then we can process step 2.
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		AbsoluteLocation location = holder.mutation.getAbsoluteLocation();
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, newBlock));
		newBlock.writeBack(cuboid);
		holder.mutation = null;
		
		// By this point, we should be able to verify both the entity and the block.
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		Assert.assertEquals(pickItem, blockInventory.getNonStackableForKey(idOfPick).type());
		Entity freeze = newEntity.freeze();
		Assert.assertEquals(0, freeze.inventory().currentEncumbrance);
		Assert.assertEquals(0, newEntity.getSelectedKey());
	}

	@Test
	public void invalidPlacements() throws Throwable
	{
		// We will try to place a block colliding with the entity or too far from them to verify that this fails.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(LOG_ITEM, 1);
		newEntity.setSelectedKey(newEntity.newInventory.getIdOfStackableType(LOG_ITEM));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Try too close (colliding).
		AbsoluteLocation tooClose = new AbsoluteLocation(0, 0, 10);
		MutationPlaceSelectedBlock placeTooClose = new MutationPlaceSelectedBlock(tooClose, tooClose);
		Assert.assertFalse(placeTooClose.applyChange(holder.context, newEntity));
		
		// Try too far.
		AbsoluteLocation tooFar = new AbsoluteLocation(0, 0, 15);
		MutationPlaceSelectedBlock placeTooFar = new MutationPlaceSelectedBlock(tooFar, tooFar);
		Assert.assertFalse(placeTooFar.applyChange(holder.context, newEntity));
		
		// Try reasonable location.
		AbsoluteLocation reasonable = new AbsoluteLocation(1, 1, 8);
		MutationPlaceSelectedBlock placeReasonable = new MutationPlaceSelectedBlock(reasonable, reasonable);
		Assert.assertTrue(placeReasonable.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation instanceof MutationBlockOverwriteByEntity);
		holder.mutation = null;
		
		// Make sure we fail if there is no selection.
		reasonable = new AbsoluteLocation(1, 1, 8);
		placeReasonable = new MutationPlaceSelectedBlock(reasonable, reasonable);
		newEntity.setSelectedKey(Entity.NO_SELECTION);
		Assert.assertFalse(placeReasonable.applyChange(holder.context, newEntity));
	}

	@Test
	public void invalidBreak() throws Throwable
	{
		// We will try to place a breaking a block of the wrong type or too far away.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		
		AbsoluteLocation tooFar = new AbsoluteLocation(8, 2, 10);
		AbsoluteLocation wrongType = new AbsoluteLocation(5, 0, 10);
		AbsoluteLocation reasonable = new AbsoluteLocation(6, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, tooFar.getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, wrongType.getBlockAddress(), PLANK_ITEM.number());
		Assert.assertEquals(PLANK_ITEM.number(), cuboid.getData15(AspectRegistry.BLOCK, wrongType.getBlockAddress()));
		cuboid.setData15(AspectRegistry.BLOCK, reasonable.getBlockAddress(), STONE_ITEM.number());
		// (we also need to make sure that we are standing on something)
		cuboid.setData15(AspectRegistry.BLOCK, newEntity.newLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), PLANK_ITEM.number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Try too far.
		EntityChangeIncrementalBlockBreak breakTooFar = new EntityChangeIncrementalBlockBreak(tooFar);
		Assert.assertFalse(breakTooFar.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.mutation);
		
		// Try reasonable location.
		EntityChangeIncrementalBlockBreak breakReasonable = new EntityChangeIncrementalBlockBreak(reasonable);
		Assert.assertTrue(breakReasonable.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
	}

	@Test
	public void fallAfterCraft() throws Throwable
	{
		// We want to run a basic craft operation and observe that we start falling when it completes.
		// (this will need to be adapted when the crafting system changes, later)
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(16.0f, 16.0f, 20.0f);
		newEntity.newInventory.addAllItems(LOG_ITEM, 1);
		newEntity.setSelectedKey(newEntity.newInventory.getIdOfStackableType(LOG_ITEM));
		
		// We will create a bogus context which just says that they are floating in the air so they can drop.
		TickProcessingContext context = _createSimpleContext();
		
		// Craft some items to use these up and verify that we also moved.
		for (long spent = 0L; spent < logToPlanks.millisPerCraft; spent += context.millisPerTick)
		{
			EntityChangeCraft craft = new EntityChangeCraft(logToPlanks);
			context = _createNextTick(context, context.millisPerTick);
			Assert.assertTrue(craft.applyChange(context, newEntity));
			_stand(context, newEntity);
			TickUtils.endOfTick(context, newEntity);
		}
		Assert.assertEquals(15.1f, newEntity.newLocation.z(), 0.01f);
		Assert.assertEquals(-9.8, newEntity.newVelocity.z(), 0.01f);
	}

	@Test
	public void nonBlockUsage() throws Throwable
	{
		// Show that a non-block item cannot be placed in the world, but can be placed in inventories.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(LOG_ITEM, 1);
		newEntity.newInventory.addAllItems(CHARCOAL_ITEM, 2);
		newEntity.setSelectedKey(newEntity.newInventory.getIdOfStackableType(CHARCOAL_ITEM));
		AbsoluteLocation furnace = new AbsoluteLocation(2, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		MutableBlockProxy proxy = new MutableBlockProxy(furnace, cuboid);
		proxy.setBlockAndClear(ENV.blocks.fromItem(ENV.items.getItemById("op.furnace")));
		proxy.writeBack(cuboid);
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Fail to place the charcoal item on the ground.
		AbsoluteLocation air = new AbsoluteLocation(1, 0, 10);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(air, air);
		Assert.assertFalse(place.applyChange(holder.context, newEntity));
		
		// Change the selection to the log and prove that this works.
		MutationEntitySelectItem select = new MutationEntitySelectItem(newEntity.newInventory.getIdOfStackableType(LOG_ITEM));
		Assert.assertTrue(select.applyChange(holder.context, newEntity));
		Assert.assertTrue(place.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation instanceof MutationBlockOverwriteByEntity);
		holder.mutation = null;
		
		// Verify that we can store the charcoal into the furnace inventory or fuel inventory.
		MutationEntityPushItems pushInventory = new MutationEntityPushItems(furnace, newEntity.newInventory.getIdOfStackableType(CHARCOAL_ITEM), 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		MutationEntityPushItems pushFuel = new MutationEntityPushItems(furnace, newEntity.newInventory.getIdOfStackableType(CHARCOAL_ITEM), 1, Inventory.INVENTORY_ASPECT_FUEL);
		Assert.assertTrue(pushInventory.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		holder.mutation = null;
		Assert.assertTrue(pushFuel.applyChange(holder.context, newEntity));
		
		// Verify that their inventory is now empty.
		Assert.assertEquals(0, newEntity.newInventory.getCurrentEncumbrance());
	}

	@Test
	public void duplicateItemOverfill() throws Throwable
	{
		// Fill a block to only have space for 1 item left, then show that 2 entities storing the item in the same tick still cause the block to become over-full.
		int entityId1 = 1;
		MutableEntity mutable1 = MutableEntity.createForTest(entityId1);
		mutable1.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		mutable1.newInventory.addAllItems(STONE_ITEM, 1);
		mutable1.setSelectedKey(mutable1.newInventory.getIdOfStackableType(STONE_ITEM));
		int entityId2 = 2;
		MutableEntity mutable2 = MutableEntity.createForTest(entityId2);
		mutable2.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		mutable2.newInventory.addAllItems(STONE_ITEM, 1);
		mutable2.setSelectedKey(mutable2.newInventory.getIdOfStackableType(STONE_ITEM));
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 9);
		// We need to make sure that there is a solid block under the target location so it doesn't just fall.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		// Fill the inventory.
		MutableBlockProxy proxy = new MutableBlockProxy(targetLocation, cuboid);
		MutableInventory mutInv = new MutableInventory(proxy.getInventory());
		int added = mutInv.addItemsBestEfforts(STONE_ITEM, 50);
		Assert.assertTrue(added < 50);
		mutInv.removeStackableItems(STONE_ITEM, 1);
		proxy.setInventory(mutInv.freeze());
		proxy.writeBack(cuboid);
		
		List<IMutationBlock> blockHolder = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							blockHolder.add(mutation);
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
						}
					}, null)
				.finish()
		;
		
		// This is a multi-step process which starts by asking the entity to start the drop.
		MutationEntityPushItems push1 = new MutationEntityPushItems(targetLocation, mutable1.newInventory.getIdOfStackableType(STONE_ITEM), 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push1.applyChange(context, mutable1));
		MutationEntityPushItems push2 = new MutationEntityPushItems(targetLocation, mutable2.newInventory.getIdOfStackableType(STONE_ITEM), 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push2.applyChange(context, mutable2));
		Assert.assertEquals(added - 1, cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()).getCount(STONE_ITEM));
		
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
		Assert.assertEquals(added + 1, inventory.getCount(STONE_ITEM));
		Assert.assertTrue(inventory.currentEncumbrance > inventory.maxEncumbrance);
	}

	@Test
	public void itemDropOverfill() throws Throwable
	{
		// Fill 2 blocks in the column and show that the top falls into the bottom, even making it over-full.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 9);
		// We need to make sure that there is a solid block under the target location so it doesn't just fall.
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		// Fill the inventory.
		MutableBlockProxy proxy = new MutableBlockProxy(targetLocation, cuboid);
		MutableInventory mutInv = new MutableInventory(proxy.getInventory());
		int added = mutInv.addItemsBestEfforts(STONE_ITEM, 50);
		Assert.assertTrue(added < 50);
		proxy.setInventory(mutInv.freeze());
		proxy.writeBack(cuboid);
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Just directly create the mutation to push items into the block above this one.
		AbsoluteLocation dropLocation = targetLocation.getRelative(0, 0, 1);
		MutationBlockStoreItems mutations = new MutationBlockStoreItems(dropLocation, new Items(STONE_ITEM, added), null, Inventory.INVENTORY_ASPECT_INVENTORY);
		proxy = new MutableBlockProxy(dropLocation, cuboid);
		Assert.assertTrue(mutations.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		
		// This should cause a follow-up so run that.
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		AbsoluteLocation mutationTarget = holder.mutation.getAbsoluteLocation();
		Assert.assertEquals(targetLocation, mutationTarget);
		proxy = new MutableBlockProxy(mutationTarget, cuboid);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		
		// Verify we see only the one inventory, and over-filled.
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, dropLocation.getBlockAddress()));
		Assert.assertEquals(2 * added, cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()).getCount(STONE_ITEM));
	}

	@Test
	public void storeItemInFullInventory() throws Throwable
	{
		// Normally, we won't try to store an item if the inventory is already full but something like breaking a block bypasses that check so see what happens when the inventory is full.
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		int stored = newEntity.newInventory.addItemsBestEfforts(STONE_ITEM, 100);
		Assert.assertTrue(stored < 100);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Create the change.
		MutationEntityStoreToInventory store = new MutationEntityStoreToInventory(new Items(STONE_ITEM, 1), null);
		Assert.assertTrue(store.applyChange(holder.context, newEntity));
		
		// We should see the attempt to drop this item onto the ground since it won't fit.
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
	}

	@Test
	public void attackEntity() throws Throwable
	{
		// Verify the range check and correct follow-up, on hit.
		int attackerId = 1;
		int targetId = 2;
		int missId = 3;
		MutableEntity attacker = MutableEntity.createForTest(attackerId);
		attacker.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		MutableEntity target = MutableEntity.createForTest(targetId);
		target.newLocation = new EntityLocation(9.0f, 9.0f, 0.0f);
		target.newInventory.addAllItems(STONE_ITEM, 2);
		target.setSelectedKey(target.newInventory.getIdOfStackableType(STONE_ITEM));
		MutableEntity miss = MutableEntity.createForTest(missId);
		miss.newLocation = new EntityLocation(12.0f, 10.0f, 0.0f);
		
		Map<Integer, Entity> targetsById = Map.of(targetId, target.freeze(), missId, miss.freeze());
		int[] targetHolder = new int[1];
		@SuppressWarnings("unchecked")
		IEntityAction<IMutablePlayerEntity>[] changeHolder = new IEntityAction[1];
		TickProcessingContext context = ContextBuilder.build()
				.tick(5L)
				.lookups(null, (Integer thisId) -> MinimalEntity.fromEntity(targetsById.get(thisId)))
				.sinks(null, new TickProcessingContext.IChangeSink() {
						@Override
						public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
						{
							Assert.assertNull(changeHolder[0]);
							targetHolder[0] = targetEntityId;
							changeHolder[0] = change;
						}
						@Override
						public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
						{
							Assert.fail("Not expected in tets");
						}
						@Override
						public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
						{
							Assert.fail("Not expected in tets");
						}
					})
				.finish()
		;
		
		// Check the miss.
		Assert.assertFalse(new EntityChangeAttackEntity(missId).applyChange(context, attacker));
		Assert.assertNull(changeHolder[0]);
		
		// Check the hit.
		Assert.assertTrue(new EntityChangeAttackEntity(targetId).applyChange(context, attacker));
		Assert.assertEquals(targetId, targetHolder[0]);
		Assert.assertTrue(changeHolder[0] instanceof EntityChangeTakeDamageFromEntity);
	}

	@Test
	public void takeDamage() throws Throwable
	{
		// Verify that damage is correctly applied, as well as the "respawn" mechanic.
		int attackerId = 1;
		int targetId = 2;
		MutableEntity attacker = MutableEntity.createForTest(attackerId);
		attacker.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		EntityLocation targetLocation = new EntityLocation(9.0f, 9.0f, 0.0f);
		MutableEntity target = MutableEntity.createWithLocation(targetId, targetLocation, MutableEntity.TESTING_LOCATION);
		target.newInventory.addAllItems(STONE_ITEM, 2);
		target.setSelectedKey(target.newInventory.getIdOfStackableType(STONE_ITEM));
		
		// We need to make sure that there is a solid block under the entities so nothing falls.
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		CuboidAddress stoneAddress = CuboidAddress.fromInt(0, 0, -1);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(stoneAddress, STONE);
		
		IMutationBlock[] blockHolder = new IMutationBlock[1];
		long tickNumber = 100L;
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.tick(tickNumber)
				.lookups((AbsoluteLocation location) ->
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
					}, null)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public void next(IMutationBlock mutation)
						{
							Assert.assertNull(blockHolder[0]);
							blockHolder[0] = mutation;
						}
						@Override
						public void future(IMutationBlock mutation, long millisToDelay)
						{
							Assert.fail("Not used in test");
						}
					}, null)
				.eventSink(events)
				.finish()
		;
		
		// We want to control the spawn location place a bed down to use.
		AbsoluteLocation bedLocation = new AbsoluteLocation (8, 8, 0);
		Item bed = ENV.items.getItemById("op.bed");
		airCuboid.setData15(AspectRegistry.BLOCK, bedLocation.getBlockAddress(), bed.number());
		
		// Set the spawn.
		EntityChangeSetDayAndSpawn setSpawn = new EntityChangeSetDayAndSpawn(bedLocation);
		Assert.assertEquals(MutableEntity.TESTING_LOCATION, target.newSpawn);
		Assert.assertEquals(0, context.config.dayStartTick);
		Assert.assertTrue(setSpawn.applyChange(context, target));
		Assert.assertEquals(target.newLocation, target.newSpawn);
		Assert.assertEquals(context.config.ticksPerDay - tickNumber, context.config.dayStartTick);
		EntityLocation spawnLocation = target.newSpawn;
		
		// Move slightly so that we see the location update on respawn.
		target.newLocation = new EntityLocation(target.newLocation.x() - 1.0f, target.newLocation.y() - 1.0f, target.newLocation.z());
		
		// Now, we will attack in 2 swipes to verify damage is taken but also the respawn logic works.
		EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamageFromEntity<>(BodyPart.HEAD, 60, attackerId);
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, target.newLocation.getBlockLocation(), targetId, attackerId));
		Assert.assertTrue(takeDamage.applyChange(context, target));
		Assert.assertEquals((byte)40, target.newHealth);
		Assert.assertNull(blockHolder[0]);
		
		events.expected(new EventRecord(EventRecord.Type.ENTITY_KILLED, EventRecord.Cause.ATTACKED, target.newLocation.getBlockLocation(), targetId, attackerId));
		// We shouldn't be able to take more damage until the damage timeout has passed.
		Assert.assertFalse(takeDamage.applyChange(context, target));
		
		context = ContextBuilder.nextTick(context, MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK).finish();
		Assert.assertTrue(takeDamage.applyChange(context, target));
		Assert.assertEquals(ENV.creatures.PLAYER.maxHealth(), target.newHealth);
		Assert.assertEquals(MiscConstants.PLAYER_MAX_FOOD, target.newFood);
		Assert.assertEquals(0, target.newInventory.freeze().sortedKeys().size());
		Assert.assertEquals(Entity.NO_SELECTION, target.getSelectedKey());
		Assert.assertEquals(spawnLocation, target.newLocation);
		Assert.assertTrue(blockHolder[0] instanceof MutationBlockStoreItems);
	}

	@Test
	public void attackWithSword() throws Throwable
	{
		// Verify sowrd damage and durability loss.
		int attackerId = 1;
		int targetId = 2;
		MutableEntity attacker = MutableEntity.createForTest(attackerId);
		attacker.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		Item swordType = ENV.items.getItemById("op.iron_sword");
		int startDurability = 100;
		attacker.newInventory.addNonStackableBestEfforts(new NonStackableItem(swordType, startDurability));
		attacker.setSelectedKey(1);
		MutableEntity target = MutableEntity.createForTest(targetId);
		target.newLocation = new EntityLocation(9.0f, 9.0f, 0.0f);
		target.newInventory.addAllItems(STONE_ITEM, 2);
		target.setSelectedKey(target.newInventory.getIdOfStackableType(STONE_ITEM));
		
		Map<Integer, Entity> targetsById = Map.of(targetId, target.freeze());
		int[] targetHolder = new int[1];
		@SuppressWarnings("unchecked")
		IEntityAction<IMutablePlayerEntity>[] changeHolder = new IEntityAction[1];
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.tick(5L)
				.lookups(null, (Integer thisId) -> MinimalEntity.fromEntity(targetsById.get(thisId)))
				.sinks(null, new TickProcessingContext.IChangeSink() {
						@Override
						public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
						{
							Assert.assertNull(changeHolder[0]);
							targetHolder[0] = targetEntityId;
							changeHolder[0] = change;
						}
						@Override
						public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
						{
							Assert.fail("Not expected in tets");
						}
						@Override
						public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
						{
							Assert.fail("Not expected in tets");
						}
					})
				.eventSink(events)
				.finish()
		;
		
		// Check that the sword durability changed and that we scheduled the hit.
		Assert.assertTrue(new EntityChangeAttackEntity(targetId).applyChange(context, attacker));
		Assert.assertEquals(targetId, targetHolder[0]);
		Assert.assertTrue(changeHolder[0] instanceof EntityChangeTakeDamageFromEntity);
		int endDurability = attacker.newInventory.getNonStackableForKey(attacker.getSelectedKey()).durability();
		Assert.assertEquals(1, (startDurability - endDurability));
		
		// Apply the hit and verify that the target health changed.
		EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> change = (EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>) changeHolder[0];
		targetHolder[0] = 0;
		changeHolder[0] = null;
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, target.newLocation.getBlockLocation(), targetId, attackerId));
		Assert.assertTrue(change.applyChange(context, target));
		Assert.assertEquals(90, target.newHealth);
	}

	@Test
	public void entityPeriodic()
	{
		CommonChangeSink changeSink = new CommonChangeSink();
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.sinks(null, changeSink)
				.eventSink(events)
				.finish()
		;
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		EntityChangePeriodic periodic = new EntityChangePeriodic();
		// We should only see this change after applying it 100 times.
		for (int i = 0; i < 99; ++i)
		{
			Assert.assertTrue(periodic.applyChange(context, newEntity));
			Assert.assertEquals((byte)100, newEntity.newFood);
		}
		Assert.assertTrue(periodic.applyChange(context, newEntity));
		Assert.assertEquals((byte)99, newEntity.newFood);
		
		// If we heal, it always takes some food.
		newEntity.newHealth = 99;
		Assert.assertTrue(periodic.applyChange(context, newEntity));
		Assert.assertEquals((byte)98, newEntity.newFood);
		Assert.assertEquals((byte)100, newEntity.newHealth);
		
		// Show what happens when we starve.
		newEntity.newFood = 0;
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.STARVATION, newEntity.newLocation.getBlockLocation(), entityId, 0));
		Assert.assertTrue(periodic.applyChange(context, newEntity));
		Assert.assertEquals((byte)0, newEntity.newFood);
		// The health change is applied inline.
		Assert.assertEquals((byte)100 - MiscConstants.STARVATION_DAMAGE_PER_SECOND, newEntity.newHealth);
	}

	@Test
	public void eatBread() throws Throwable
	{
		// Show that we can eat bread, but not stone, and that the bread increases our food level.
		Item bread = ENV.items.getItemById("op.bread");
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(STONE_ITEM, 1);
		newEntity.newInventory.addAllItems(bread, 1);
		newEntity.setSelectedKey(newEntity.newInventory.getIdOfStackableType(LOG_ITEM));
		newEntity.newFood = 90;
		TickProcessingContext context = ContextBuilder.build()
				.tick(5L)
				.finish()
		;
		
		// We will fail to eat the log.
		EntityChangeUseSelectedItemOnSelf eat = new EntityChangeUseSelectedItemOnSelf();
		Assert.assertFalse(eat.applyChange(context, newEntity));
		Assert.assertEquals(90, newEntity.newFood);
		
		// We should succeed in eating the bread, though.
		newEntity.setSelectedKey(newEntity.newInventory.getIdOfStackableType(bread));
		Assert.assertTrue(eat.applyChange(context, newEntity));
		
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.getSelectedKey());
		Assert.assertEquals(1, newEntity.newInventory.getCount(STONE_ITEM));
		Assert.assertEquals(0, newEntity.newInventory.getCount(bread));
		Assert.assertEquals(100, newEntity.newFood);
	}

	@Test
	public void breakTool() throws Throwable
	{
		// Break a block with a tool with 1 durability to observe it break.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		Item pickItem = ENV.items.getItemById("op.iron_pickaxe");
		newEntity.newInventory.addNonStackableBestEfforts(new NonStackableItem(pickItem, 1));
		// We assume that this is 1.
		newEntity.setSelectedKey(1);
		
		AbsoluteLocation target = new AbsoluteLocation(6, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), STONE_ITEM.number());
		// (we also need to make sure that we are standing on something)
		cuboid.setData15(AspectRegistry.BLOCK, newEntity.newLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), PLANK_ITEM.number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// Do the break with enough time to break the block.
		EntityChangeIncrementalBlockBreak breakReasonable = new EntityChangeIncrementalBlockBreak(target);
		Assert.assertTrue(breakReasonable.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		Assert.assertEquals(0, newEntity.getSelectedKey());
		Assert.assertEquals(0, newEntity.newInventory.freeze().sortedKeys().size());
	}

	@Test
	public void breakBlockFullInventory() throws Throwable
	{
		// Break a block with a nearly full inventory and verify that it doesn't add the new item.
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		Item plank = ENV.items.getItemById("op.plank");
		newEntity.newInventory.addItemsBestEfforts(plank, newEntity.newInventory.maxVacancyForItem(plank) - 1);
		int initialEncumbrance = newEntity.newInventory.getCurrentEncumbrance();
		
		AbsoluteLocation target = new AbsoluteLocation(6, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), STONE_ITEM.number());
		// (we also need to make sure that we are standing on something)
		cuboid.setData15(AspectRegistry.BLOCK, newEntity.newLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), plank.number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// Do the break with enough time to break the block.
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, target, 0, entityId));
		for (long spent = 0L; spent < ENV.damage.getToughness(STONE); spent += holder.context.millisPerTick)
		{
			EntityChangeIncrementalBlockBreak breakReasonable = new EntityChangeIncrementalBlockBreak(target);
			Assert.assertTrue(breakReasonable.applyChange(holder.context, newEntity));
			Assert.assertNotNull(holder.mutation);
			Assert.assertNull(holder.change);
			MutationBlockIncrementalBreak breaking = (MutationBlockIncrementalBreak) holder.mutation;
			holder.mutation = null;
			
			MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
			Assert.assertTrue(breaking.applyMutation(holder.context, proxy));
			proxy.writeBack(cuboid);
		}
		Assert.assertTrue(holder.events.didPost());
		Assert.assertNull(holder.mutation);
		Assert.assertNotNull(holder.change);
		MutationEntityStoreToInventory store = (MutationEntityStoreToInventory) holder.change;
		holder.change = null;
		
		// We should see an attempt to drop the items, since they won't fit.
		Assert.assertTrue(store.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		Assert.assertNull(holder.change);
		
		// The block should be broken but our inventory should be the same size.
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(1, newEntity.newInventory.freeze().sortedKeys().size());
		Assert.assertEquals(initialEncumbrance, newEntity.newInventory.getCurrentEncumbrance());
	}

	@Test
	public void blockMaterial() throws Throwable
	{
		// Show what happens when we try to break different blocks with the same tool.
		Item pickaxe = ENV.items.getItemById("op.iron_pickaxe");
		int startDurability = 300;
		
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		newEntity.newInventory.addNonStackableBestEfforts(new NonStackableItem(pickaxe, startDurability));
		newEntity.setSelectedKey(1);
		
		AbsoluteLocation targetStone = new AbsoluteLocation(6, 0, 10);
		AbsoluteLocation targetLog = new AbsoluteLocation(6, 1, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, targetStone.getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, targetLog.getBlockAddress(), LOG_ITEM.number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Apply a tick to the stone and observe what happens.
		short duration = (short) holder.context.millisPerTick;
		EntityChangeIncrementalBlockBreak breakStone = new EntityChangeIncrementalBlockBreak(targetStone);
		Assert.assertTrue(breakStone.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		MutationBlockIncrementalBreak breaking = (MutationBlockIncrementalBreak) holder.mutation;
		holder.mutation = null;
		MutableBlockProxy proxy = new MutableBlockProxy(targetStone, cuboid);
		Assert.assertTrue(breaking.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(startDurability - 1, newEntity.newInventory.getNonStackableForKey(1).durability());
		Assert.assertEquals(10 * duration, cuboid.getData15(AspectRegistry.DAMAGE, targetStone.getBlockAddress()));
		
		// Now, do the same to the plank and observe the difference.
		EntityChangeIncrementalBlockBreak breakLog = new EntityChangeIncrementalBlockBreak(targetLog);
		Assert.assertTrue(breakLog.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		breaking = (MutationBlockIncrementalBreak) holder.mutation;
		holder.mutation = null;
		proxy = new MutableBlockProxy(targetLog, cuboid);
		Assert.assertTrue(breaking.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(startDurability - 2, newEntity.newInventory.getNonStackableForKey(1).durability());
		Assert.assertEquals(duration, cuboid.getData15(AspectRegistry.DAMAGE, targetLog.getBlockAddress()));
	}

	@Test
	public void bucketUsage() throws Throwable
	{
		// We will use a water bucket and lava bucket to show that we can place and replace these liquid sources.
		Item emptyBucket = ENV.items.getItemById("op.bucket_empty");
		Item waterBucket = ENV.items.getItemById("op.bucket_water");
		Item lavaBucket = ENV.items.getItemById("op.bucket_lava");
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		short waterSourceItemNumber = ENV.items.getItemById("op.water_source").number();
		short lavaSourceItemNumber = ENV.items.getItemById("op.lava_source").number();
		
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		newEntity.newInventory.addNonStackableBestEfforts(new NonStackableItem(waterBucket, 0));
		newEntity.newInventory.addNonStackableBestEfforts(new NonStackableItem(lavaBucket, 0));
		// Start with the water.
		newEntity.setSelectedKey(1);
		
		AbsoluteLocation target = new AbsoluteLocation(6, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), stone);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), ENV.special.AIR.item().number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Place the water source.
		EntityChangeUseSelectedItemOnBlock exchange = new EntityChangeUseSelectedItemOnBlock(target);
		Assert.assertTrue(exchange.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		MutationBlockReplace replace = (MutationBlockReplace) holder.mutation;
		holder.mutation = null;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(replace.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(emptyBucket, newEntity.newInventory.getNonStackableForKey(1).type());
		Assert.assertEquals(waterSourceItemNumber, cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		
		// Try to pick up the water source - note that this should fail unless we clear the last special action time.
		Assert.assertFalse(exchange.applyChange(holder.context, newEntity));
		newEntity.ephemeral_lastSpecialActionMillis = 0L;
		Assert.assertTrue(exchange.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		replace = (MutationBlockReplace) holder.mutation;
		holder.mutation = null;
		proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(replace.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(waterBucket, newEntity.newInventory.getNonStackableForKey(1).type());
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		
		// Place the lava source.
		newEntity.setSelectedKey(2);
		newEntity.ephemeral_lastSpecialActionMillis = 0L;
		Assert.assertTrue(exchange.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		replace = (MutationBlockReplace) holder.mutation;
		holder.mutation = null;
		proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(replace.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(emptyBucket, newEntity.newInventory.getNonStackableForKey(2).type());
		Assert.assertEquals(lavaSourceItemNumber, cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		
		// Try to pick up the lava source.
		newEntity.ephemeral_lastSpecialActionMillis = 0L;
		Assert.assertTrue(exchange.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		replace = (MutationBlockReplace) holder.mutation;
		holder.mutation = null;
		proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(replace.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(lavaBucket, newEntity.newInventory.getNonStackableForKey(2).type());
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
	}

	@Test
	public void changeHotbar() throws Throwable
	{
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		int stoneId = 1;
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		Item swordType = ENV.items.getItemById("op.iron_sword");
		int startDurability = 100;
		int swordId = 2;
		mutable.newInventory.addNonStackableBestEfforts(new NonStackableItem(swordType, startDurability));
		
		// We should default to slot 0.
		Assert.assertEquals(0, mutable.newHotbarIndex);
		MutationEntitySelectItem select = new MutationEntitySelectItem(stoneId);
		Assert.assertTrue(select.applyChange(null,  mutable));
		Assert.assertEquals(stoneId, mutable.newHotbar[0]);
		Assert.assertEquals(stoneId, mutable.getSelectedKey());
		
		// Change to the other slot and add another reference.
		Assert.assertFalse(new EntityChangeChangeHotbarSlot(0).applyChange(null, mutable));
		EntityChangeChangeHotbarSlot change = new EntityChangeChangeHotbarSlot(1);
		Assert.assertTrue(change.applyChange(null, mutable));
		select = new MutationEntitySelectItem(swordId);
		Assert.assertTrue(select.applyChange(null,  mutable));
		Assert.assertEquals(swordId, mutable.newHotbar[1]);
		Assert.assertEquals(swordId, mutable.getSelectedKey());
		Assert.assertEquals(stoneId, mutable.newHotbar[0]);
	}

	@Test
	public void armourBehaviour() throws Throwable
	{
		// We will put some armour on the entity and see how the health is impacted by various attacks.
		int entityId = 1;
		int attackerId = 2;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		Item helmetType = ENV.items.getItemById("op.iron_helmet");
		int startDurability = 15;
		mutable.newArmour[BodyPart.HEAD.ordinal()] = new NonStackableItem(helmetType, startDurability);
		_Events events = new _Events();
		TickProcessingContext context = _createSimpleContextWithEvents(events);
		
		// Hit them in a different place and see the whole damage applied.
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, mutable.newLocation.getBlockLocation(), entityId, attackerId));
		Assert.assertTrue(new EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>(BodyPart.TORSO, 10, attackerId).applyChange(context,  mutable));
		Assert.assertEquals((byte)90, mutable.newHealth);
		
		// Hit them in the head with 1 damage and see it applied, with no durability loss.
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, mutable.newLocation.getBlockLocation(), entityId, attackerId));
		context = ContextBuilder.nextTick(context, MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK).finish();
		Assert.assertTrue(new EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>(BodyPart.HEAD, 1, attackerId).applyChange(context,  mutable));
		Assert.assertEquals((byte)89, mutable.newHealth);
		Assert.assertEquals(startDurability, mutable.newArmour[BodyPart.HEAD.ordinal()].durability());
		
		// Hit them in the head with 10 damage (what the armour blocks) see the durability loss and damage reduced.
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, mutable.newLocation.getBlockLocation(), entityId, attackerId));
		context = ContextBuilder.nextTick(context, MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK).finish();
		Assert.assertTrue(new EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>(BodyPart.HEAD, 10, attackerId).applyChange(context,  mutable));
		Assert.assertEquals((byte)88, mutable.newHealth);
		Assert.assertEquals(6, mutable.newArmour[BodyPart.HEAD.ordinal()].durability());
		
		// Hit them in the head with 10 damage, again to see the armour break and damage reduced.
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, mutable.newLocation.getBlockLocation(), entityId, attackerId));
		context = ContextBuilder.nextTick(context, MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK).finish();
		Assert.assertTrue(new EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>(BodyPart.HEAD, 10, attackerId).applyChange(context,  mutable));
		Assert.assertEquals((byte)87, mutable.newHealth);
		Assert.assertNull(mutable.newArmour[BodyPart.HEAD.ordinal()]);
	}

	@Test
	public void swapArmour() throws Throwable
	{
		// Put some armour in the inventory and show that we can swap it in and out of the armour slots.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		Item dirtType = ENV.items.getItemById("op.dirt");
		Item helmetType = ENV.items.getItemById("op.iron_helmet");
		int helmet1Durability = 15;
		int helmet1Id = 1;
		mutable.newInventory.addNonStackableBestEfforts(new NonStackableItem(helmetType, helmet1Durability));
		int helmet2Durability = 2000;
		int helmet2Id = 2;
		mutable.newInventory.addNonStackableBestEfforts(new NonStackableItem(helmetType, helmet2Durability));
		int dirtId = 3;
		mutable.newInventory.addItemsBestEfforts(dirtType, 1);
		Assert.assertEquals(10, mutable.newInventory.getCurrentEncumbrance());
		
		// Show that we can't swap the dirt.
		Assert.assertFalse(new EntityChangeSwapArmour(BodyPart.TORSO, dirtId).applyChange(null,  mutable));
		Assert.assertNull(mutable.newArmour[BodyPart.TORSO.ordinal()]);
		Assert.assertEquals(10, mutable.newInventory.getCurrentEncumbrance());
		
		// Show that we can't swap the helmet to the wrong body part.
		Assert.assertFalse(new EntityChangeSwapArmour(BodyPart.TORSO, helmet1Id).applyChange(null,  mutable));
		Assert.assertNull(mutable.newArmour[BodyPart.TORSO.ordinal()]);
		Assert.assertEquals(10, mutable.newInventory.getCurrentEncumbrance());
		
		// Show that we can wear the helmet.
		Assert.assertTrue(new EntityChangeSwapArmour(BodyPart.HEAD, helmet1Id).applyChange(null,  mutable));
		Assert.assertEquals(helmet1Durability, mutable.newArmour[BodyPart.HEAD.ordinal()].durability());
		Assert.assertEquals(6, mutable.newInventory.getCurrentEncumbrance());
		
		// Show that we can swap to the other helmet.
		Assert.assertTrue(new EntityChangeSwapArmour(BodyPart.HEAD, helmet2Id).applyChange(null,  mutable));
		Assert.assertEquals(helmet2Durability, mutable.newArmour[BodyPart.HEAD.ordinal()].durability());
		Assert.assertEquals(6, mutable.newInventory.getCurrentEncumbrance());
		
		// Show that we can swap out with nothing.
		Assert.assertTrue(new EntityChangeSwapArmour(BodyPart.HEAD, 0).applyChange(null,  mutable));
		Assert.assertNull(mutable.newArmour[BodyPart.HEAD.ordinal()]);
		Assert.assertEquals(10, mutable.newInventory.getCurrentEncumbrance());
	}

	@Test
	public void loadUnloadFurnaceFuel() throws Throwable
	{
		// Verify that we can load and unload the furnace fuel inventory.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(CHARCOAL_ITEM, 2);
		int charcoalId = newEntity.newInventory.getIdOfStackableType(CHARCOAL_ITEM);
		AbsoluteLocation furnace = new AbsoluteLocation(2, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		MutableBlockProxy mutable = new MutableBlockProxy(furnace, cuboid);
		mutable.setBlockAndClear(ENV.blocks.fromItem(ENV.items.getItemById("op.furnace")));
		mutable.writeBack(cuboid);
		
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// Place a charcoal in the normal and fuel inventories.
		MutationEntityPushItems pushToInventory = new MutationEntityPushItems(furnace, charcoalId, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(pushToInventory.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.change);
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, mutable));
		Assert.assertNull(holder.change);
		holder.mutation = null;
		mutable.writeBack(cuboid);
		
		MutationEntityPushItems pushToFuel = new MutationEntityPushItems(furnace, charcoalId, 1, Inventory.INVENTORY_ASPECT_FUEL);
		Assert.assertTrue(pushToFuel.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.change);
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, mutable));
		Assert.assertNull(holder.change);
		holder.mutation = null;
		mutable.writeBack(cuboid);
		
		// Now, remove the charcoal from both normal and fuel inventories.
		// (this is the first thing in both inventories so just assume key 1).
		int blockInventoryKey = 1;
		MutationEntityRequestItemPickUp pullFromInventory = new MutationEntityRequestItemPickUp(furnace, blockInventoryKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(pullFromInventory.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.change);
		Assert.assertTrue(holder.mutation instanceof MutationBlockExtractItems);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, mutable));
		holder.mutation = null;
		Assert.assertTrue(holder.change instanceof MutationEntityStoreToInventory);
		Assert.assertTrue(holder.change.applyChange(holder.context, newEntity));
		holder.change = null;
		mutable.writeBack(cuboid);
		
		MutationEntityRequestItemPickUp pullFromFuel = new MutationEntityRequestItemPickUp(furnace, blockInventoryKey, 1, Inventory.INVENTORY_ASPECT_FUEL);
		Assert.assertTrue(pullFromFuel.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.change);
		Assert.assertTrue(holder.mutation instanceof MutationBlockExtractItems);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, mutable));
		holder.mutation = null;
		Assert.assertTrue(holder.change instanceof MutationEntityStoreToInventory);
		Assert.assertTrue(holder.change.applyChange(holder.context, newEntity));
		holder.change = null;
		mutable.writeBack(cuboid);
		
		// Verify that the charcoal is now only in the entity inventory.
		Assert.assertEquals(2, newEntity.newInventory.getCount(CHARCOAL_ITEM));
		BlockProxy proxy = new BlockProxy(furnace.getBlockAddress(), cuboid);
		Assert.assertEquals(0, proxy.getInventory().currentEncumbrance);
		Assert.assertEquals(0, proxy.getFuel().fuelInventory().currentEncumbrance);
	}

	@Test
	public void attackCreature() throws Throwable
	{
		// Verify that attacking a creature ends up calling the other path.
		int attackerId = 1;
		int targetId = -1;
		MutableEntity attacker = MutableEntity.createForTest(attackerId);
		attacker.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(targetId
				, COW
				, new EntityLocation(9.0f, 9.0f, 0.0f)
				, (byte) 100
		);
		CommonChangeSink changeSink = new CommonChangeSink();
		TickProcessingContext context = ContextBuilder.build()
				.tick(5L)
				.lookups(null, (Integer id) -> {
						Assert.assertEquals(targetId, id.intValue());
						return MinimalEntity.fromCreature(creature);
					})
				.sinks(null, changeSink)
				.finish()
		;
		
		// Attack and verify that we see damage come through the creature path.
		Assert.assertTrue(new EntityChangeAttackEntity(targetId).applyChange(context, attacker));
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureChanges = changeSink.takeExportedCreatureChanges();
		Assert.assertEquals(1, creatureChanges.size());
		List<IEntityAction<IMutableCreatureEntity>> list = creatureChanges.get(targetId);
		Assert.assertEquals(1, list.size());
		IEntityAction<IMutableCreatureEntity> change = list.get(0);
		Assert.assertTrue(change instanceof EntityChangeTakeDamageFromEntity<IMutableCreatureEntity>);
	}

	@Test
	public void staticUsageQueries() throws Throwable
	{
		Item emptyBucket = ENV.items.getItemById("op.bucket_empty");
		Item waterBucket = ENV.items.getItemById("op.bucket_water");
		Item breadItem = ENV.items.getItemById("op.bread");
		Item fertilizerItem = ENV.items.getItemById("op.fertilizer");
		Block waterSource = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.water_source"));
		Block waterWeak = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.water_weak"));
		Block wheatYoung = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.wheat_young"));
		Block wheatMature = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.wheat_mature"));
		
		Assert.assertTrue(EntityChangeUseSelectedItemOnBlock.canUseOnBlock(emptyBucket, waterSource));
		Assert.assertTrue(EntityChangeUseSelectedItemOnBlock.canUseOnBlock(waterBucket, waterWeak));
		Assert.assertFalse(EntityChangeUseSelectedItemOnBlock.canUseOnBlock(emptyBucket, waterWeak));
		Assert.assertFalse(EntityChangeUseSelectedItemOnBlock.canUseOnBlock(emptyBucket, waterWeak));
		Assert.assertTrue(EntityChangeUseSelectedItemOnBlock.canUseOnBlock(fertilizerItem, wheatYoung));
		Assert.assertFalse(EntityChangeUseSelectedItemOnBlock.canUseOnBlock(fertilizerItem, wheatMature));
		
		Assert.assertTrue(EntityChangeUseSelectedItemOnSelf.canBeUsedOnSelf(breadItem));
		Assert.assertFalse(EntityChangeUseSelectedItemOnSelf.canBeUsedOnSelf(waterBucket));
	}

	@Test
	public void feedCow() throws Throwable
	{
		// Verify that feeding a creature ends up sending the follow-up change and that it works.
		int entityId = 1;
		int targetId = -1;
		MutableEntity entity = MutableEntity.createForTest(entityId);
		entity.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		entity.newInventory.addAllItems(ENV.items.getItemById("op.wheat_item"), 1);
		// We assume that this is key 1.
		entity.newHotbar[0] = 1;
		CreatureEntity creature = CreatureEntity.create(targetId
				, COW
				, new EntityLocation(9.0f, 9.0f, 0.0f)
				, (byte) 100
		);
		CommonChangeSink changeSink = new CommonChangeSink();
		TickProcessingContext context = ContextBuilder.build()
				.tick(5L)
				.lookups(null, (Integer id) -> {
						Assert.assertEquals(targetId, id.intValue());
						return MinimalEntity.fromCreature(creature);
					})
				.sinks(null, changeSink)
				.finish()
		;
		
		// Feed the creature and verify that we see the apply scheduled.
		Assert.assertTrue(new EntityChangeUseSelectedItemOnEntity(targetId).applyChange(context, entity));
		Entity updatedEntty = entity.freeze();
		Assert.assertEquals(0, updatedEntty.inventory().currentEncumbrance);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureChanges = changeSink.takeExportedCreatureChanges();
		Assert.assertEquals(1, creatureChanges.size());
		List<IEntityAction<IMutableCreatureEntity>> list = creatureChanges.get(targetId);
		Assert.assertEquals(1, list.size());
		IEntityAction<IMutableCreatureEntity> change = list.get(0);
		Assert.assertTrue(change instanceof EntityChangeApplyItemToCreature);
		
		// Verify that the apply works.
		MutableCreature mutable = MutableCreature.existing(creature);
		Assert.assertTrue(change.applyChange(context, mutable));
		mutable.freeze();
		
		// Note that a second attempt should fail since the cow has already been fed.
		Assert.assertFalse(change.applyChange(context, mutable));
	}

	@Test
	public void doorUsage() throws Throwable
	{
		// Give the entity a door, have them place it, open it, close it, open it, break it, and verify it is a normal door in the inventory.
		Item itemDoor = ENV.items.getItemById("op.door");
		Block door = ENV.blocks.getAsPlaceableBlock(itemDoor);
		
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(itemDoor, 1);
		newEntity.setSelectedKey(1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 10);
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(target, target);
		Assert.assertTrue(place.applyChange(holder.context, newEntity));
		
		// We also need to apply the actual mutation.
		MutableBlockProxy proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, holder.mutation.getAbsoluteLocation(), 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Open the door.
		EntityChangeSetBlockLogicState open = new EntityChangeSetBlockLogicState(target, true);
		Assert.assertTrue(open.applyChange(holder.context, newEntity));
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(door, proxy.getBlock());
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, proxy.getFlags());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Close the door.
		EntityChangeSetBlockLogicState close = new EntityChangeSetBlockLogicState(target, false);
		Assert.assertTrue(close.applyChange(holder.context, newEntity));
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(door, proxy.getBlock());
		Assert.assertNotEquals(FlagsAspect.FLAG_ACTIVE, proxy.getFlags());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Open it again.
		Assert.assertTrue(open.applyChange(holder.context, newEntity));
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(door, proxy.getBlock());
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, proxy.getFlags());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Break it and verify it is a closed door in the inventory.
		EntityChangeIncrementalBlockBreak breaker = new EntityChangeIncrementalBlockBreak(target);
		Assert.assertTrue(breaker.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, holder.mutation.getAbsoluteLocation(), 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		Assert.assertTrue(holder.change.applyChange(holder.context, newEntity));
		holder.change = null;
		
		// Check our inventory.
		Inventory inventory = newEntity.freeze().inventory();
		Assert.assertEquals(1, inventory.sortedKeys().size());
		Assert.assertEquals(1, inventory.getCount(itemDoor));
	}

	@Test
	public void hopperPlacement() throws Throwable
	{
		// Give the entity a hopper, place it with a direction, and observe that it ends up oriented that way.
		Item itemHopper = ENV.items.getItemById("op.hopper");
		
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(itemHopper, 6);
		newEntity.setSelectedKey(1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// We will place down 5 hoppers, all with different orientations, and verify that they end up correctly oriented in the world.
		// We place in the opposite direction, facing north.
		AbsoluteLocation centreTarget = new AbsoluteLocation(1, 1, 10);
		
		// Face north.
		MutationPlaceSelectedBlock north = new MutationPlaceSelectedBlock(centreTarget.getRelative(0, -1, 0), centreTarget);
		Assert.assertTrue(north.applyChange(holder.context, newEntity));
		MutableBlockProxy proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, centreTarget.getRelative(0, -1, 0), 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(ENV.items.getItemById("op.hopper"), proxy.getBlock().item());
		Assert.assertEquals(OrientationAspect.Direction.NORTH, proxy.getOrientation());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Face south.
		MutationPlaceSelectedBlock south = new MutationPlaceSelectedBlock(centreTarget.getRelative(0, 1, 0), centreTarget);
		Assert.assertTrue(south.applyChange(holder.context, newEntity));
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, centreTarget.getRelative(0, 1, 0), 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(ENV.items.getItemById("op.hopper"), proxy.getBlock().item());
		Assert.assertEquals(OrientationAspect.Direction.SOUTH, proxy.getOrientation());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Face east.
		MutationPlaceSelectedBlock east = new MutationPlaceSelectedBlock(centreTarget.getRelative(-1, 0, 0), centreTarget);
		Assert.assertTrue(east.applyChange(holder.context, newEntity));
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, centreTarget.getRelative(-1, 0, 0), 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(ENV.items.getItemById("op.hopper"), proxy.getBlock().item());
		Assert.assertEquals(OrientationAspect.Direction.EAST, proxy.getOrientation());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Face west.
		MutationPlaceSelectedBlock west = new MutationPlaceSelectedBlock(centreTarget.getRelative(1, 0, 0), centreTarget);
		Assert.assertTrue(west.applyChange(holder.context, newEntity));
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, centreTarget.getRelative(1, 0, 0), 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(ENV.items.getItemById("op.hopper"), proxy.getBlock().item());
		Assert.assertEquals(OrientationAspect.Direction.WEST, proxy.getOrientation());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Face down - this case, the target just needs to match z.
		MutationPlaceSelectedBlock down = new MutationPlaceSelectedBlock(centreTarget.getRelative(0, 0, -1), centreTarget);
		Assert.assertTrue(down.applyChange(holder.context, newEntity));
		proxy = new MutableBlockProxy(holder.mutation.getAbsoluteLocation(), cuboid);
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, centreTarget.getRelative(0, 0, -1), 0, entityId));
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(itemHopper, proxy.getBlock().item());
		proxy.writeBack(cuboid);
		holder.mutation = null;
		
		// Check our inventory.
		Inventory inventory = newEntity.freeze().inventory();
		Assert.assertEquals(1, inventory.sortedKeys().size());
		Assert.assertEquals(1, inventory.getCount(itemHopper));
	}

	@Test
	public void doorWithSwitch() throws Throwable
	{
		// Place a door and switch in the world, have the entity activate the switch, then break it, observing the expected door state change.
		Item itemSwitch = ENV.items.getItemById("op.switch");
		Block switc = ENV.blocks.getAsPlaceableBlock(itemSwitch);
		Block door = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.door"));
		Block logicWire = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.logic_wire"));
		
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation doorLocation = new AbsoluteLocation(0, 1, 10);
		AbsoluteLocation wireLocation = doorLocation.getRelative(1, 0, 0);
		AbsoluteLocation switchLocation = wireLocation.getRelative(1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, doorLocation.getBlockAddress(), door.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, wireLocation.getBlockAddress(), logicWire.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress(), switc.item().number());
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// Activate the switch - requires that we invoke the PropagationHelpers to trigger the logic update event.
		EntityChangeSetBlockLogicState open = new EntityChangeSetBlockLogicState(switchLocation, true);
		Assert.assertTrue(open.applyChange(holder.context, newEntity));
		_runMutationWithLogicUpdate(holder, cuboid, switchLocation, doorLocation, switc, FlagsAspect.FLAG_ACTIVE);
		_runMutationInContext(cuboid, holder, door, (byte)0x0);
		_runMutationInContext(cuboid, holder, door, FlagsAspect.FLAG_ACTIVE);
		Assert.assertNull(holder.mutation);
		
		// Deactivate the switch - requires running the 3 follow-up mutations.
		EntityChangeSetBlockLogicState close = new EntityChangeSetBlockLogicState(switchLocation, false);
		Assert.assertTrue(close.applyChange(holder.context, newEntity));
		_runMutationWithLogicUpdate(holder, cuboid, switchLocation, doorLocation, switc, (byte)0x0);
		_runMutationInContext(cuboid, holder, door, FlagsAspect.FLAG_ACTIVE);
		_runMutationInContext(cuboid, holder, door, (byte)0x0);
		Assert.assertNull(holder.mutation);
		
		// Open it again.
		Assert.assertTrue(open.applyChange(holder.context, newEntity));
		_runMutationWithLogicUpdate(holder, cuboid, switchLocation, doorLocation, switc, FlagsAspect.FLAG_ACTIVE);
		_runMutationInContext(cuboid, holder, door, (byte)0x0);
		_runMutationInContext(cuboid, holder, door, FlagsAspect.FLAG_ACTIVE);
		Assert.assertNull(holder.mutation);
		
		// Break it and watch the door close.
		EntityChangeIncrementalBlockBreak breaker = new EntityChangeIncrementalBlockBreak(switchLocation);
		Assert.assertTrue(breaker.applyChange(holder.context, newEntity));
		holder.events.expected(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, switchLocation, 0, entityId));
		_runMutationWithLogicUpdate(holder, cuboid, switchLocation, doorLocation, ENV.special.AIR, (byte)0x0);
		_runMutationInContext(cuboid, holder, door, FlagsAspect.FLAG_ACTIVE);
		_runMutationInContext(cuboid, holder, door, (byte)0x0);
		Assert.assertNull(holder.mutation);
		Assert.assertTrue(holder.change.applyChange(holder.context, newEntity));
		holder.change = null;
		
		// Check our inventory.
		Inventory inventory = newEntity.freeze().inventory();
		Assert.assertEquals(1, inventory.sortedKeys().size());
		Assert.assertEquals(1, inventory.getCount(itemSwitch));
	}

	@Test
	public void craftHotbarClear() throws Throwable
	{
		// Handles the case where we need to clear the hotbar selections if we used up the last of them - test for the index we selected or another.
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		Craft stoneToBrick = ENV.crafting.getCraftById("op.stone_to_stone_brick");
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		
		// Put log and stone on the hotbar, selecting the log.
		newEntity.newInventory.addAllItems(LOG_ITEM, 1);
		newEntity.newInventory.addAllItems(STONE_ITEM, 1);
		newEntity.newHotbar[0] = 1;
		newEntity.newHotbar[1] = 2;
		newEntity.newHotbarIndex = 0;
		newEntity.freeze();
		
		// We will create a bogus context which just says that they are standing in a wall so they don't try to move.
		TickProcessingContext context = _createSimpleContext();
		
		for (long spent = 0L; spent < logToPlanks.millisPerCraft; spent += context.millisPerTick)
		{
			EntityChangeCraft craft = new EntityChangeCraft(logToPlanks);
			Assert.assertTrue(craft.applyChange(context, newEntity));
		}
		Assert.assertNull(newEntity.newLocalCraftOperation);
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.newHotbar[0]);
		Assert.assertEquals(2, newEntity.newHotbar[1]);
		Assert.assertEquals(0, newEntity.newHotbarIndex);
		newEntity.freeze();
		
		for (long spent = 0L; spent < stoneToBrick.millisPerCraft; spent += context.millisPerTick)
		{
			EntityChangeCraft craft = new EntityChangeCraft(stoneToBrick);
			Assert.assertTrue(craft.applyChange(context, newEntity));
		}
		Assert.assertNull(newEntity.newLocalCraftOperation);
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.newHotbar[0]);
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.newHotbar[1]);
		Assert.assertEquals(0, newEntity.newHotbarIndex);
		newEntity.freeze();
	}

	@Test
	public void basicSwim()
	{
		// Swim and see how our vector changes.
		EntityLocation oldLocation = new EntityLocation(5.0f, 5.0f, 5.0f);
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), waterSource);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		
		EntityChangeSwim<IMutablePlayerEntity> swim = new EntityChangeSwim<>();
		boolean didApply = swim.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		
		// The swim doesn't move, just sets the vector.
		Assert.assertEquals(EntityChangeSwim.SWIM_FORCE, newEntity.newVelocity.z(), 0.01f);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
		
		// Try a few ticks to see how our motion changes - the specific values are derived from how we implement _stand, so they aren't too important.
		_stand(context, newEntity);
		TickUtils.endOfTick(context, newEntity);
		Assert.assertEquals(5.47, newEntity.newLocation.z(), 0.01f);
		Assert.assertEquals(4.41f, newEntity.newVelocity.z(), 0.01f);
		// See how long it takes for the viscosity to slow us and gravity to act on us until we start to descend.
		int ticks = 0;
		while (newEntity.newVelocity.z() > 0.0f)
		{
			_stand(context, newEntity);
			TickUtils.endOfTick(context, newEntity);
			ticks += 1;
		}
		// Verify the expected tick count, location, and velocity (experimentally derived).
		Assert.assertEquals(9, ticks);
		Assert.assertEquals(7.45f, newEntity.newLocation.z(), 0.01f);
		Assert.assertEquals(0.0f, newEntity.newVelocity.z(), 0.01f);
	}

	@Test
	public void swimVersusJump()
	{
		// Create a water cuboid with a solid block in it to show that a jump or swim will work on the block but only swimming works in the water.
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), waterSource);
		AbsoluteLocation stoneLocation = cuboid.getCuboidAddress().getBase().getRelative(5, 5, 5);
		cuboid.setData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress(), STONE.item().number());
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		EntityChangeJump<IMutablePlayerEntity> jump = new EntityChangeJump<>();
		EntityChangeSwim<IMutablePlayerEntity> swim = new EntityChangeSwim<>();
		
		// Jumping under water is fine if standing on a solid block.
		MutableEntity entity = MutableEntity.createForTest(1);
		entity.newLocation = new EntityLocation(stoneLocation.x(), stoneLocation.y(), stoneLocation.z() + 1);
		Assert.assertTrue(jump.applyChange(context, entity));
		
		// Jumping under water fails if not on a solid block but swimming should work.
		entity = MutableEntity.createForTest(1);
		entity.newLocation = new EntityLocation(stoneLocation.x(), stoneLocation.y(), stoneLocation.z() + 1.5f);
		Assert.assertFalse(jump.applyChange(context, entity));
		Assert.assertTrue(swim.applyChange(context, entity));
		
		// Jumping under water fails if not on a solid block but swimming should work.
		entity = MutableEntity.createForTest(1);
		entity.newLocation = new EntityLocation(stoneLocation.x(), stoneLocation.y(), stoneLocation.z() + 2.0f);
		Assert.assertFalse(jump.applyChange(context, entity));
		Assert.assertTrue(swim.applyChange(context, entity));
	}

	@Test
	public void grindFlourInQuern() throws Throwable
	{
		Craft grindFlour = ENV.crafting.getCraftById("op.flour");
		Item wheatItem = ENV.items.getItemById("op.wheat_item");
		Item flour = ENV.items.getItemById("op.flour");
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(wheatItem, 2);
		int wheatId = newEntity.newInventory.getIdOfStackableType(wheatItem);
		AbsoluteLocation quern = new AbsoluteLocation(2, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		MutableBlockProxy mutable = new MutableBlockProxy(quern, cuboid);
		mutable.setBlockAndClear(ENV.blocks.fromItem(ENV.items.getItemById("op.quern")));
		mutable.writeBack(cuboid);
		
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// Place the wheat into the quern.
		MutationEntityPushItems pushToInventory = new MutationEntityPushItems(quern, wheatId, 2, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(pushToInventory.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.change);
		Assert.assertTrue(holder.mutation instanceof MutationBlockStoreItems);
		Assert.assertTrue(holder.mutation.applyMutation(holder.context, mutable));
		Assert.assertNull(holder.change);
		holder.mutation = null;
		mutable.writeBack(cuboid);
		
		// Run the crafting operation.
		for (long spent = 0L; spent < grindFlour.millisPerCraft; spent += holder.context.millisPerTick)
		{
			EntityChangeCraftInBlock craft = new EntityChangeCraftInBlock(quern, grindFlour);
			Assert.assertTrue(craft.applyChange(holder.context, newEntity));
			Assert.assertTrue(holder.mutation instanceof MutationBlockCraft);
			Assert.assertTrue(holder.mutation.applyMutation(holder.context, mutable));
			Inventory inv = mutable.getInventory();
			boolean isLastIteration = (spent + holder.context.millisPerTick) >= grindFlour.millisPerCraft;
			if (isLastIteration)
			{
				Assert.assertNull(mutable.getCrafting());
				Assert.assertEquals(1, inv.getCount(wheatItem));
				Assert.assertEquals(1, inv.getCount(flour));
			}
			else
			{
				Assert.assertNotNull(mutable.getCrafting());
				Assert.assertEquals(2, inv.getCount(wheatItem));
				Assert.assertEquals(0, inv.getCount(flour));
			}
			holder.mutation = null;
			mutable.writeBack(cuboid);
		}
	}

	@Test
	public void dropItemsCreative() throws Throwable
	{
		// Create an entity in an air block, in creative mode, then drop some of the items and observe the inventory is unchanged.
		int entityId = 1;
		Inventory inventory = Inventory.start(StationRegistry.CAPACITY_PLAYER).finish();
		Entity original = new Entity(entityId
				, true
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, inventory
				, new int[Entity.HOTBAR_SIZE]
				, 0
				, new NonStackableItem[BodyPart.values().length]
				, null
				, ENV.creatures.PLAYER.maxHealth()
				, MiscConstants.PLAYER_MAX_FOOD
				, MiscConstants.MAX_BREATH
				, 0
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_DATA
		);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation targetLocation = new AbsoluteLocation(0, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, targetLocation.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// This is a multi-step process which starts by asking the entity to start the 2 drops.
		// We first need to look up the keys (which is tricky for non-stackables).
		Inventory creativeInventory = CreativeInventory.fakeInventory();
		Item pickItem = ENV.items.getItemById("op.iron_pickaxe");
		int stoneId = creativeInventory.getIdOfStackableType(STONE_ITEM);
		int pickId = creativeInventory.sortedKeys().stream().filter(
				(Integer key) -> (null != creativeInventory.getNonStackableForKey(key)) && (pickItem == creativeInventory.getNonStackableForKey(key).type())
		).toList().get(0);
		
		MutableEntity newEntity = MutableEntity.existing(original);
		MutationEntityPushItems push = new MutationEntityPushItems(targetLocation, stoneId, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(holder.context, newEntity));
		MutationBlockStoreItems step2_1 = (MutationBlockStoreItems) holder.mutation;
		holder.mutation = null;
		push = new MutationEntityPushItems(targetLocation, pickId, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		Assert.assertTrue(push.applyChange(holder.context, newEntity));
		MutationBlockStoreItems step2_2 = (MutationBlockStoreItems) holder.mutation;
		holder.mutation = null;
		
		// This should be unchanged and the block shouldn't yet have the items.
		Assert.assertEquals(CreativeInventory.STACK_SIZE, newEntity.accessMutableInventory().getCount(STONE_ITEM));
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress()));
		
		// We can now apply the step 2.
		AbsoluteLocation location = step2_1.getAbsoluteLocation();
		MutableBlockProxy newBlock = new MutableBlockProxy(location, cuboid);
		Assert.assertTrue(step2_1.applyMutation(holder.context, newBlock));
		Assert.assertTrue(step2_2.applyMutation(holder.context, newBlock));
		newBlock.writeBack(cuboid);
		Assert.assertNull(holder.mutation);
		
		// We can now verify that the block has the stone and the pick.
		Inventory blockInventory = cuboid.getDataSpecial(AspectRegistry.INVENTORY, targetLocation.getBlockAddress());
		int stone = 0;
		int pick = 0;
		for (Integer key : blockInventory.sortedKeys())
		{
			Items stack = blockInventory.getStackForKey(key);
			NonStackableItem nonStack = blockInventory.getNonStackableForKey(key);
			Assert.assertTrue((null == stack) != (null == nonStack));
			if (null != stack)
			{
				Assert.assertEquals(STONE_ITEM, stack.type());
				stone += stack.count();
			}
			else
			{
				Assert.assertEquals(pickItem, nonStack.type());
				pick += 1;
			}
		}
		Assert.assertEquals(1, stone);
		Assert.assertEquals(1, pick);
	}

	@Test
	public void setCreative() throws Throwable
	{
		// Create an entity in survival mode, change them to creative and back.  We also show that this change clears the hotbar (since it isn't the same inventory).
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newInventory.addAllItems(CHARCOAL_ITEM, 1);
		newEntity.newHotbar[newEntity.newHotbarIndex] = 1;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ContextHolder holder = new _ContextHolder(cuboid, false, false);
		Assert.assertFalse(newEntity.freeze().isCreativeMode());
		
		EntityChangeOperatorSetCreative set = new EntityChangeOperatorSetCreative(true);
		Assert.assertTrue(set.applyChange(holder.context, newEntity));
		Assert.assertTrue(newEntity.freeze().isCreativeMode());
		Assert.assertEquals(0, newEntity.newHotbar[newEntity.newHotbarIndex]);
		MutationEntitySelectItem select = new MutationEntitySelectItem(10);
		Assert.assertTrue(select.applyChange(holder.context, newEntity));
		Assert.assertEquals(10, newEntity.newHotbar[newEntity.newHotbarIndex]);
		
		EntityChangeOperatorSetCreative clear = new EntityChangeOperatorSetCreative(false);
		Assert.assertTrue(clear.applyChange(holder.context, newEntity));
		Assert.assertFalse(newEntity.freeze().isCreativeMode());
		Assert.assertEquals(0, newEntity.newHotbar[newEntity.newHotbarIndex]);
	}

	@Test
	public void teleport() throws Throwable
	{
		// Create an entity in survival mode, change them to creative and back.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ContextHolder holder = new _ContextHolder(cuboid, false, false);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), newEntity.freeze().location());
		
		EntityLocation destination = new EntityLocation(15.5f, -50.0f, 11.0f);
		EntityChangeOperatorSetLocation set = new EntityChangeOperatorSetLocation(destination);
		Assert.assertTrue(set.applyChange(holder.context, newEntity));
		Assert.assertEquals(destination, newEntity.freeze().location());
	}

	@Test
	public void ignoreHealthInCreative() throws Throwable
	{
		// Create an entity in creative mode and verify that it ignores changes to health, food, and breath.
		int entityId = 1;
		Inventory inventory = Inventory.start(StationRegistry.CAPACITY_PLAYER).finish();
		byte health = (byte)(ENV.creatures.PLAYER.maxHealth() / 2);
		byte food = MiscConstants.PLAYER_MAX_FOOD / 2;
		byte breath = MiscConstants.MAX_BREATH / 2;
		int energyDeficit = 50;
		Entity original = new Entity(entityId
				, true
				, new EntityLocation(0.0f, 0.0f, 10.0f)
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, inventory
				, new int[Entity.HOTBAR_SIZE]
				, 0
				, new NonStackableItem[BodyPart.values().length]
				, null
				, health
				, food
				, breath
				, energyDeficit
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_DATA
		);
		
		// Try to change these values and verify that nothing happens.
		MutableEntity localMutable = MutableEntity.existing(original);
		localMutable.setBreath((byte)1);
		localMutable.setFood((byte)1);
		localMutable.setHealth((byte)1);
		localMutable.setEnergyDeficit(1);
		Assert.assertTrue(original == localMutable.freeze());
		
		// Periodic change should also not change anything even if we set a high energy deficit.
		TickProcessingContext context = ContextBuilder.build()
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
					{
					}
					@Override
					public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
					{
					}
					@Override
					public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
					{
					}})
				.finish()
		;
		EntityChangePeriodic periodic = new EntityChangePeriodic();
		localMutable.setEnergyDeficit(10 * EntityChangePeriodic.ENERGY_PER_FOOD);
		periodic.applyChange(context, localMutable);
		Entity end = localMutable.freeze();
		Assert.assertEquals(health, end.health());
		Assert.assertEquals(food, end.food());
		Assert.assertEquals(breath, end.breath());
		Assert.assertEquals(energyDeficit, end.energyDeficit());
	}

	@Test
	public void almostBrokenToolCreative() throws Throwable
	{
		// Break a block with a tool with 1 durability, while in creative mode, to observe it does NOT break and only takes one hit.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		newEntity.isCreativeMode = true;
		Item pickItem = ENV.items.getItemById("op.iron_pickaxe");
		newEntity.newInventory.addNonStackableBestEfforts(new NonStackableItem(pickItem, 1));
		// We assume that this is 1.
		newEntity.setSelectedKey(1);
		
		AbsoluteLocation target = new AbsoluteLocation(6, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), STONE_ITEM.number());
		// (we also need to make sure that we are standing on something)
		cuboid.setData15(AspectRegistry.BLOCK, newEntity.newLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), PLANK_ITEM.number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// Do the break with only 1 tick, as it should break instantly.
		EntityChangeIncrementalBlockBreak breakReasonable = new EntityChangeIncrementalBlockBreak(target);
		Assert.assertTrue(breakReasonable.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		// We should still see the item in the inventory.
		Assert.assertEquals(1, newEntity.getSelectedKey());
		Assert.assertEquals(1, newEntity.newInventory.freeze().getNonStackableForKey(1).durability());
	}

	@Test
	public void repairCases() throws Throwable
	{
		// Try to repair a damaged block, an undamaged block, a block of the wrong type, and a block too far away.
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		
		AbsoluteLocation tooFar = new AbsoluteLocation(8, 2, 10);
		AbsoluteLocation wrongType = new AbsoluteLocation(5, 0, 10);
		AbsoluteLocation noDamage = new AbsoluteLocation(5, 1, 10);
		AbsoluteLocation valid = new AbsoluteLocation(6, 0, 10);
		short damaged = 150;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, tooFar.getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.DAMAGE, tooFar.getBlockAddress(), damaged);
		// wrongType will just be air.
		cuboid.setData15(AspectRegistry.BLOCK, noDamage.getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, valid.getBlockAddress(), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.DAMAGE, valid.getBlockAddress(), damaged);
		// (we also need to make sure that we are standing on something)
		cuboid.setData15(AspectRegistry.BLOCK, newEntity.newLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), PLANK_ITEM.number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Try too far.
		EntityChangeIncrementalBlockRepair repairTooFar = new EntityChangeIncrementalBlockRepair(tooFar);
		Assert.assertFalse(repairTooFar.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.mutation);
		
		// Try wrong type.
		EntityChangeIncrementalBlockRepair repairWrongType = new EntityChangeIncrementalBlockRepair(wrongType);
		Assert.assertFalse(repairWrongType.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.mutation);
		
		// Try undamaged
		EntityChangeIncrementalBlockRepair repairUndamaged = new EntityChangeIncrementalBlockRepair(noDamage);
		Assert.assertFalse(repairUndamaged.applyChange(holder.context, newEntity));
		Assert.assertNull(holder.mutation);
		
		// Try valid
		EntityChangeIncrementalBlockRepair repairValid = new EntityChangeIncrementalBlockRepair(valid);
		Assert.assertTrue(repairValid.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation instanceof MutationBlockIncrementalRepair);
	}

	@Test
	public void lavaBucketDestroysItems() throws Throwable
	{
		// Show that placing a lava bucket in a block with items will destroy them.
		Item emptyBucket = ENV.items.getItemById("op.bucket_empty");
		Item lavaBucket = ENV.items.getItemById("op.bucket_lava");
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		short lavaSourceItemNumber = ENV.items.getItemById("op.lava_source").number();
		
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		newEntity.newInventory.addNonStackableBestEfforts(new NonStackableItem(lavaBucket, 0));
		newEntity.setSelectedKey(1);
		
		AbsoluteLocation target = new AbsoluteLocation(6, 0, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), stone);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress(), Inventory.start(10).addStackable(CHARCOAL_ITEM, 2).finish());
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Place the lava source.
		EntityChangeUseSelectedItemOnBlock exchange = new EntityChangeUseSelectedItemOnBlock(target);
		Assert.assertTrue(exchange.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		MutationBlockReplace replace = (MutationBlockReplace) holder.mutation;
		holder.mutation = null;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(replace.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(0, proxy.getInventory().getCount(CHARCOAL_ITEM));
		Assert.assertEquals(emptyBucket, newEntity.newInventory.getNonStackableForKey(1).type());
		Assert.assertEquals(lavaSourceItemNumber, cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertNull(cuboid.getDataSpecial(AspectRegistry.INVENTORY, target.getBlockAddress()));
	}

	@Test
	public void extinguishFire() throws Throwable
	{
		// Show that a repair mutation will put out a fire in a block.
		Block log = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		
		AbsoluteLocation target = new AbsoluteLocation(10, 10, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), log.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, target.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		
		_ContextHolder holder = new _ContextHolder(cuboid, false, true);
		
		// Extinguish the fire.
		EntityChangeIncrementalBlockRepair repair = new EntityChangeIncrementalBlockRepair(target);
		Assert.assertTrue(repair.applyChange(holder.context, newEntity));
		Assert.assertNotNull(holder.mutation);
		MutationBlockIncrementalRepair followUp = (MutationBlockIncrementalRepair) holder.mutation;
		holder.mutation = null;
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		Assert.assertTrue(followUp.applyMutation(holder.context, proxy));
		proxy.writeBack(cuboid);
		Assert.assertEquals(log.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, target.getBlockAddress()));
	}

	@Test
	public void multiBlockDoorUsage() throws Throwable
	{
		// Give the entity a door, have them place it, open it, close it, open it, break it, and verify it is a normal door in the inventory.
		Item itemDoor = ENV.items.getItemById("op.double_door_base");
		Block door = ENV.blocks.getAsPlaceableBlock(itemDoor);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		
		// We know that multiple mutations are sent for multi-block changes so we will capture in a list.
		List<IMutationBlock> mutations = new ArrayList<>();
		List<IMutationBlock> futureMutations = new ArrayList<>();
		MutationEntityStoreToInventory[] out_store = new MutationEntityStoreToInventory[1];
		EventRecord[] out_record = new EventRecord[1];
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return location.getCuboidAddress().equals(cuboid.getCuboidAddress())
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
					;
				}, null)
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						mutations.add(mutation);
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.assertTrue(ContextBuilder.DEFAULT_MILLIS_PER_TICK == millisToDelay);
						futureMutations.add(mutation);
					}
				}, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
					{
						Assert.assertNull(out_store[0]);
						out_store[0] = (MutationEntityStoreToInventory) change;
					}
					@Override
					public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
					{
						Assert.fail("Not in test");
					}
					@Override
					public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
					{
						Assert.fail("Not in test");
					}
				})
				.eventSink(new TickProcessingContext.IEventSink() {
					@Override
					public void post(EventRecord event)
					{
						Assert.assertNull(out_record[0]);
						out_record[0] = event;
					}
				})
				.finish()
		;
		
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		newEntity.newInventory.addAllItems(itemDoor, 1);
		newEntity.setSelectedKey(1);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 10);
		
		// Show that we fail to place when using the wrong helper.
		MutationPlaceSelectedBlock fail = new MutationPlaceSelectedBlock(target, target);
		Assert.assertFalse(fail.applyChange(context, newEntity));
		Assert.assertEquals(0, mutations.size());
		
		// Show that we correctly place when using the right helper.
		EntityChangePlaceMultiBlock place = new EntityChangePlaceMultiBlock(target, OrientationAspect.Direction.NORTH);
		Assert.assertTrue(place.applyChange(context, newEntity));
		
		// We also need to apply the actual mutations.
		Assert.assertEquals(4, mutations.size());
		for (IMutationBlock mutation : mutations)
		{
			MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(mutation.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
			Assert.assertEquals(door, proxy.getBlock());
			Assert.assertEquals(0x0, proxy.getFlags());
		}
		// We expect the event only for the root.
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_PLACED, EventRecord.Cause.NONE, target, 0, entityId), out_record[0]);
		out_record[0] = null;
		mutations.clear();
		
		// Apply the follow-up mutations.
		Assert.assertEquals(4, futureMutations.size());
		for (IMutationBlock mutation : futureMutations)
		{
			MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(mutation.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
			Assert.assertNull(out_record[0]);
		}
		futureMutations.clear();
		
		// Open the door - we should see 4 mutations to the blocks.
		EntityChangeSetBlockLogicState open = new EntityChangeSetBlockLogicState(target, true);
		Assert.assertTrue(open.applyChange(context, newEntity));
		Assert.assertEquals(4, mutations.size());
		for (IMutationBlock mutation : mutations)
		{
			MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(mutation.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
			Assert.assertEquals(door, proxy.getBlock());
			Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, proxy.getFlags());
		}
		mutations.clear();
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(0, 0, 1).getBlockAddress()));
		
		// Close the door - we should see 4 mutations to the blocks.
		EntityChangeSetBlockLogicState close = new EntityChangeSetBlockLogicState(target, false);
		Assert.assertTrue(close.applyChange(context, newEntity));
		Assert.assertEquals(4, mutations.size());
		for (IMutationBlock mutation : mutations)
		{
			MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(mutation.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
			Assert.assertEquals(door, proxy.getBlock());
			Assert.assertEquals(0x0, proxy.getFlags());
		}
		mutations.clear();
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, target.getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(0x0, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(0, 0, 1).getBlockAddress()));
		
		// Open it again - we should see 4 mutations to the blocks.
		Assert.assertTrue(open.applyChange(context, newEntity));
		Assert.assertEquals(4, mutations.size());
		for (IMutationBlock mutation : mutations)
		{
			MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(mutation.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
			Assert.assertEquals(door, proxy.getBlock());
			Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, proxy.getFlags());
		}
		mutations.clear();
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertEquals(door.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, target.getRelative(0, 0, 1).getBlockAddress()));
		
		// Break it in 2 steps across the surface and verify it is a closed door in the inventory.
		EntityChangeIncrementalBlockBreak breaker = new EntityChangeIncrementalBlockBreak(target);
		Assert.assertTrue(breaker.applyChange(context, newEntity));
		breaker = new EntityChangeIncrementalBlockBreak(target.getRelative(0, 0, 1));
		Assert.assertTrue(breaker.applyChange(context, newEntity));
		
		// Each break will send an incremental break to each block in the multi-block (4 blocks).
		Assert.assertEquals(8, mutations.size());
		for (int i = 0; i < 4; ++i)
		{
			IMutationBlock mutation = mutations.remove(0);
			MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(mutation.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
		}
		Assert.assertEquals(4, mutations.size());
		for (int i = 0; i < 4; ++i)
		{
			IMutationBlock mutation = mutations.remove(0);
			MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
			Assert.assertTrue(mutation.applyMutation(context, proxy));
			proxy.writeBack(cuboid);
			Assert.assertEquals(ENV.special.AIR, proxy.getBlock());
		}
		Assert.assertEquals(new EventRecord(EventRecord.Type.BLOCK_BROKEN, EventRecord.Cause.NONE, target, 0, entityId), out_record[0]);
		Assert.assertEquals(0, mutations.size());
		
		// We should see the inventory storage mutation here so apply it.
		Assert.assertNotNull(out_store[0]);
		Assert.assertTrue(out_store[0].applyChange(context, newEntity));
		out_record[0] = null;
		Assert.assertEquals(0, mutations.size());
		
		// Check our inventory.
		Inventory inventory = newEntity.freeze().inventory();
		Assert.assertEquals(1, inventory.sortedKeys().size());
		Assert.assertEquals(1, inventory.getCount(itemDoor));
	}

	@Test
	public void spawnCommand() throws Throwable
	{
		// Explicitly spawn a creature and make sure that it appears in the context.
		EntityType cow = ENV.creatures.getTypeById("op.cow");
		EntityLocation destination = new EntityLocation(15.5f, -50.0f, 11.0f);
		int[] count = new int[1];
		TickProcessingContext.ICreatureSpawner spawner = (EntityType type, EntityLocation location, byte health) -> {
			Assert.assertEquals(cow, type);
			Assert.assertEquals(destination, location);
			Assert.assertEquals(health, cow.maxHealth());
			count[0] += 1;
		};
		TickProcessingContext context = ContextBuilder.build()
				.tick(5L)
				.spawner(spawner)
				.finish()
		;
		
		EntityChangeOperatorSpawnCreature spawn = new EntityChangeOperatorSpawnCreature(cow, destination);
		Assert.assertTrue(spawn.applyChange(context, null));
		Assert.assertEquals(1, count[0]);
	}

	@Test
	public void useHoe() throws Throwable
	{
		// We will show that a hoe with 2 durability can be used precisely twice (its durability is different from other tools).
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(6.0f - ENV.creatures.PLAYER.volume().width(), 0.0f, 10.0f);
		Item hoeItem = ENV.items.getItemById("op.stone_hoe");
		Item dirtItem = ENV.items.getItemById("op.dirt");
		newEntity.newInventory.addNonStackableBestEfforts(new NonStackableItem(hoeItem, 2));
		// We assume that this is 1.
		newEntity.setSelectedKey(1);
		Assert.assertEquals(2, newEntity.newInventory.getNonStackableForKey(newEntity.getSelectedKey()).durability());
		
		AbsoluteLocation target0 = newEntity.newLocation.getBlockLocation().getRelative(1, 0, 0);
		AbsoluteLocation target1 = newEntity.newLocation.getBlockLocation().getRelative(1, 1, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, target0.getBlockAddress(), dirtItem.number());
		cuboid.setData15(AspectRegistry.BLOCK, target1.getBlockAddress(), dirtItem.number());
		// (we also need to make sure that we are standing on something)
		cuboid.setData15(AspectRegistry.BLOCK, newEntity.newLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), PLANK_ITEM.number());
		
		_ContextHolder holder = new _ContextHolder(cuboid, true, true);
		
		// Show that we can hoe both blocks.
		EntityChangeUseSelectedItemOnBlock use0 = new EntityChangeUseSelectedItemOnBlock(target0);
		Assert.assertTrue(use0.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation instanceof MutationBlockReplace);
		holder.mutation = null;
		Assert.assertEquals(1, newEntity.newInventory.getNonStackableForKey(newEntity.getSelectedKey()).durability());
		Assert.assertEquals(1, newEntity.newInventory.freeze().sortedKeys().size());
		
		// This counts as a special action so reset that.
		Assert.assertEquals(holder.context.currentTickTimeMillis, newEntity.ephemeral_lastSpecialActionMillis);
		newEntity.ephemeral_lastSpecialActionMillis = 0L;
		EntityChangeUseSelectedItemOnBlock use1 = new EntityChangeUseSelectedItemOnBlock(target1);
		Assert.assertTrue(use1.applyChange(holder.context, newEntity));
		Assert.assertTrue(holder.mutation instanceof MutationBlockReplace);
		Assert.assertEquals(0, newEntity.getSelectedKey());
		Assert.assertEquals(0, newEntity.newInventory.freeze().sortedKeys().size());
	}

	@Test
	public void simpleTopLevel()
	{
		// We just want to show that the entity can move using this top-level movement helper.
		// Note that it doesn't currently check the validity of movements.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation newVelocity = new EntityLocation(4.0f, 0.0f, 0.0f);
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		TickProcessingContext context = _createSimpleContext();
		EntityChangeTopLevelMovement<IMutablePlayerEntity> action = new EntityChangeTopLevelMovement<>(newLocation
			, newVelocity
			, EntityChangeTopLevelMovement.Intensity.WALKING
			, (byte)5
			, (byte)6
			, null
		);
		boolean didApply = action.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(newLocation, newEntity.newLocation);
		Assert.assertEquals(newVelocity, newEntity.newVelocity);
		Assert.assertEquals(EntityChangePeriodic.ENERGY_COST_PER_TICK_WALKING, newEntity.newEnergyDeficit);
		Assert.assertEquals(5, newEntity.newYaw);
		Assert.assertEquals(6, newEntity.newPitch);
	}

	@Test
	public void coastToStopTopLevel()
	{
		// Show that when we coast to a stop, while falling, the check on the change passes.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 5.0f);
		EntityLocation oldVelocity = new EntityLocation(0.8f, 0.0f, -1.0f);
		EntityLocation newLocation = new EntityLocation(0.04f, 0.0f, 4.0f);
		EntityLocation newVelocity = new EntityLocation(0.0f, 0.0f, -2.0f);
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		newEntity.newVelocity = oldVelocity;
		CuboidData water = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), WATER_SOURCE);
		TickProcessingContext context = ContextBuilder.build()
				.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), water), null)
				.finish()
		;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> action = new EntityChangeTopLevelMovement<>(newLocation
			, newVelocity
			, EntityChangeTopLevelMovement.Intensity.STANDING
			, (byte)5
			, (byte)6
			, null
		);
		boolean didApply = action.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(newLocation, newEntity.newLocation);
		Assert.assertEquals(newVelocity, newEntity.newVelocity);
	}

	@Test
	public void coastInAir()
	{
		// Show how we handle the case where the entity is coasting while hanging in the air on the client side (small time units).
		EntityLocation oldLocation = new EntityLocation(41.87f, -93.59f, 8.38f);
		EntityLocation oldVelocity = new EntityLocation(-1.95f, 0.0f, -3.92f);
		EntityLocation newLocation = new EntityLocation(41.85f, -93.59f, 8.31f);
		EntityLocation newVelocity = new EntityLocation(-0.97f, 0.0f, -4.09f);
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		newEntity.newVelocity = oldVelocity;
		CuboidData air = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, -4, 0), ENV.special.AIR);
		TickProcessingContext context = ContextBuilder.build()
				.millisPerTick(17L)
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), air), null)
				.finish()
		;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> action = new EntityChangeTopLevelMovement<>(newLocation
			, newVelocity
			, EntityChangeTopLevelMovement.Intensity.STANDING
			, (byte)5
			, (byte)6
			, null
		);
		
		boolean didApply = action.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(newLocation, newEntity.newLocation);
		Assert.assertEquals(newVelocity, newEntity.newVelocity);
	}

	@Test
	public void invalidCraft() throws Throwable
	{
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		
		// We will create a bogus context which just says that they are standing in a wall so they don't try to move.
		TickProcessingContext context = _createSimpleContext();
		
		// Craft some items to use these up and verify that the selection is cleared.
		EntityChangeCraft craft = new EntityChangeCraft(logToPlanks);
		EntityChangeTopLevelMovement<IMutablePlayerEntity> topLevel = new EntityChangeTopLevelMovement<>(newEntity.newLocation
			, newEntity.newVelocity
			, EntityChangeTopLevelMovement.Intensity.STANDING
			, (byte)0
			, (byte)0
			, craft
		);
		Assert.assertFalse(topLevel.applyChange(context, newEntity));
		
		// Make sure that we aren't crafting and and nothing is selected.
		Assert.assertNull(newEntity.newLocalCraftOperation);
		Assert.assertEquals(0, newEntity.newInventory.getCurrentEncumbrance());
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.getSelectedKey());
	}

	@Test
	public void craftOperationNullDefault() throws Throwable
	{
		Craft logToPlanks = ENV.crafting.getCraftById("op.log_to_planks");
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newInventory.addAllItems(LOG_ITEM, 1);
		newEntity.newInventory.addNonStackableAllowingOverflow(new NonStackableItem(IRON_SWORD_ITEM, 5));
		newEntity.newHotbar[0] = 1;
		newEntity.newHotbar[1] = 2;
		
		// We will create a bogus context which just says that they are standing in a wall so they don't try to move.
		TickProcessingContext context = _createSimpleContext();
		
		// We will only explicitly name the crafting operation the first time.
		EntityChangeCraft craft = new EntityChangeCraft(logToPlanks);
		Assert.assertTrue(craft.applyChange(context, newEntity));
		Assert.assertNotNull(newEntity.newLocalCraftOperation);
		
		// ... and use null for the follow-ups.
		for (long spent = context.millisPerTick; spent < logToPlanks.millisPerCraft; spent += context.millisPerTick)
		{
			craft = new EntityChangeCraft(null);
			Assert.assertTrue(craft.applyChange(context, newEntity));
		}
		
		// If nothing is selected, null should just fail.
		craft = new EntityChangeCraft(null);
		Assert.assertFalse(craft.applyChange(context, newEntity));
		
		// Verify that we completed this and what is left in our inventory.
		Assert.assertNull(newEntity.newLocalCraftOperation);
		Assert.assertEquals(0, newEntity.newInventory.getCount(LOG_ITEM));
		Assert.assertEquals(2, newEntity.newInventory.getCount(PLANK_ITEM));
		Assert.assertEquals(Entity.NO_SELECTION, newEntity.newHotbar[0]);
		Assert.assertEquals(2, newEntity.newHotbar[1]);
	}

	@Test
	public void topLevelBasicRunning()
	{
		// We just show that the validation works for moving at double normal speed.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation newLocation = new EntityLocation(0.8f, 0.0f, 0.0f);
		EntityLocation newVelocity = new EntityLocation(8.0f, 0.0f, 0.0f);
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		TickProcessingContext context = _createSimpleContext();
		EntityChangeTopLevelMovement<IMutablePlayerEntity> action = new EntityChangeTopLevelMovement<>(newLocation
			, newVelocity
			, EntityChangeTopLevelMovement.Intensity.RUNNING
			, (byte)5
			, (byte)6
			, null
		);
		boolean didApply = action.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(newLocation, newEntity.newLocation);
		Assert.assertEquals(newVelocity, newEntity.newVelocity);
		Assert.assertEquals(EntityChangePeriodic.ENERGY_COST_PER_TICK_RUNNING, newEntity.newEnergyDeficit);
		Assert.assertEquals(5, newEntity.newYaw);
		Assert.assertEquals(6, newEntity.newPitch);
	}

	@Test
	public void weaponTypes() throws Throwable
	{
		// We just want to try attacking an entity with different weapons to see the different damage amounts.
		int targetId = 1;
		int swordAttackerId = 2;
		int axeAttackerId = 3;
		int stoneAttackerId = 4;
		
		MutableEntity target = MutableEntity.createForTest(targetId);
		target.newLocation = new EntityLocation(10.0f, 10.0f, 0.0f);
		
		MutableEntity swordAttacker = MutableEntity.createForTest(swordAttackerId);
		swordAttacker.newLocation = new EntityLocation(11.0f, 10.0f, 0.0f);
		swordAttacker.newInventory.addNonStackableAllowingOverflow(new NonStackableItem(IRON_SWORD_ITEM, 100));
		swordAttacker.setSelectedKey(1);
		
		MutableEntity axeAttacker = MutableEntity.createForTest(axeAttackerId);
		axeAttacker.newLocation = new EntityLocation(10.0f, 11.0f, 0.0f);
		axeAttacker.newInventory.addNonStackableAllowingOverflow(new NonStackableItem(IRON_AXE_ITEM, 100));
		axeAttacker.setSelectedKey(1);
		
		MutableEntity stoneAttacker = MutableEntity.createForTest(stoneAttackerId);
		stoneAttacker.newLocation = new EntityLocation(9.0f, 10.0f, 0.0f);
		stoneAttacker.newInventory.addAllItems(STONE_ITEM, 2);
		stoneAttacker.setSelectedKey(1);
		
		Entity baselineTarget = target.freeze();
		Map<Integer, Entity> targetsById = Map.of(targetId, baselineTarget);
		List<EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>> changeHolder = new ArrayList<>();
		int[] eventCounter = new int[1];
		TickProcessingContext context = ContextBuilder.build()
			.tick(5L)
			.lookups(null, (Integer thisId) -> MinimalEntity.fromEntity(targetsById.get(thisId)))
			.sinks(null, new TickProcessingContext.IChangeSink() {
				@Override
				public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
				{
					Assert.assertEquals(targetId, targetEntityId);
					Assert.assertTrue(change instanceof EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>);
					changeHolder.add((EntityChangeTakeDamageFromEntity<IMutablePlayerEntity>) change);
				}
				@Override
				public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
				{
					Assert.fail("Not expected in tets");
				}
				@Override
				public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
				{
					Assert.fail("Not expected in tets");
				}
			})
			.eventSink(new TickProcessingContext.IEventSink()
			{
				@Override
				public void post(EventRecord event)
				{
					eventCounter[0] += 1;
				}
			})
			.finish()
		;
		
		// Have all the attackers attack.
		Assert.assertTrue(new EntityChangeAttackEntity(targetId).applyChange(context, swordAttacker));
		Assert.assertTrue(new EntityChangeAttackEntity(targetId).applyChange(context, axeAttacker));
		Assert.assertTrue(new EntityChangeAttackEntity(targetId).applyChange(context, stoneAttacker));
		Assert.assertEquals(3, changeHolder.size());
		Assert.assertEquals(0, eventCounter[0]);
		
		// Check applying these to the entity to see the damage they do.
		Assert.assertEquals(100, target.newHealth);
		target = MutableEntity.existing(baselineTarget);
		Assert.assertTrue(changeHolder.remove(0).applyChange(context, target));
		Assert.assertEquals(90, target.newHealth);
		target = MutableEntity.existing(baselineTarget);
		Assert.assertTrue(changeHolder.remove(0).applyChange(context, target));
		Assert.assertEquals(96, target.newHealth);
		target = MutableEntity.existing(baselineTarget);
		Assert.assertTrue(changeHolder.remove(0).applyChange(context, target));
		Assert.assertEquals(99, target.newHealth);
		Assert.assertEquals(3, eventCounter[0]);
	}

	@Test
	public void ladderMovement()
	{
		// We want to test that ascending and descending work as expected, but only when intersecting with a ladder.
		EntityLocation location1 = new EntityLocation(1.0f, 1.0f, 0.0f);
		EntityLocation location2 = new EntityLocation(5.0f, 5.0f, 0.5f);
		EntityLocation location3 = new EntityLocation(5.0f, 5.0f, 1.0f);
		MutableEntity mutable1 = MutableEntity.createForTest(1);
		MutableEntity mutable2 = MutableEntity.createForTest(2);
		MutableEntity mutable3 = MutableEntity.createForTest(3);
		mutable1.newLocation = location1;
		mutable2.newLocation = location2;
		mutable3.newLocation = location3;
		CuboidData air = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		air.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 0), LADDER.item().number());
		CuboidData stone = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(50L)
			.lookups((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(air.getCuboidAddress())
					? new BlockProxy(location.getBlockAddress(), air)
					: new BlockProxy(location.getBlockAddress(), stone)
				;
			}, null)
			.finish()
		;
		EntitySubActionLadderAscend<IMutablePlayerEntity> ascend = new EntitySubActionLadderAscend<>();
		EntitySubActionLadderDescend<IMutablePlayerEntity> descend = new EntitySubActionLadderDescend<>();
		Assert.assertFalse(ascend.applyChange(context, mutable1));
		Assert.assertEquals(location1, mutable1.newLocation);
		Assert.assertFalse(descend.applyChange(context, mutable1));
		Assert.assertEquals(location1, mutable1.newLocation);
		
		Assert.assertTrue(ascend.applyChange(context, mutable2));
		Assert.assertEquals(new EntityLocation(5.0f, 5.0f, 0.6f), mutable2.newLocation);
		Assert.assertTrue(descend.applyChange(context, mutable2));
		Assert.assertEquals(location2, mutable2.newLocation);
		
		Assert.assertFalse(ascend.applyChange(context, mutable3));
		Assert.assertEquals(location3, mutable3.newLocation);
		Assert.assertTrue(descend.applyChange(context, mutable3));
		Assert.assertEquals(new EntityLocation(5.0f, 5.0f, 0.9f), mutable3.newLocation);
		
		// Show that this still works as a sub-action.
		EntityLocation newLocation = new EntityLocation(5.0f, 5.1f, 0.8f);
		EntityChangeTopLevelMovement<IMutablePlayerEntity> topLevel = new EntityChangeTopLevelMovement<>(newLocation
			, new EntityLocation(0.0f, 2.0f, 0.0f)
			, EntityChangeTopLevelMovement.Intensity.WALKING
			, (byte)5
			, (byte)6
			, descend
		);
		Assert.assertTrue(topLevel.applyChange(context, mutable3));
		Assert.assertEquals(newLocation, mutable3.newLocation);
	}


	private static Item _selectedItemType(MutableEntity entity)
	{
		Items stack = entity.newInventory.getStackForKey(entity.getSelectedKey());
		return (null != stack)
				? stack.type()
				: null
		;
	}

	private static TickProcessingContext _createSimpleContext()
	{
		return _createSimpleContextWithEvents(null);
	}

	private static TickProcessingContext _createSimpleContextWithEvents(_Events events)
	{
		CuboidData air = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		TickProcessingContext context = ContextBuilder.build()
				.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), (location.z() >= 0) ? air : stone), null)
				.eventSink(events)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _createSingleCuboidContext(CuboidData cuboid)
	{
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
						return (location.getCuboidAddress().equals(cuboid.getCuboidAddress()))
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
						;
					}, null)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _createNextTick(TickProcessingContext context, long millisInTick)
	{
		return ContextBuilder.nextTick(context, 1L)
				.millisPerTick(millisInTick)
				.finish()
		;
	}

	private byte _runMutationInContext(CuboidData cuboid, _ContextHolder holder, Block expectedBlock, byte expectedFlags)
	{
		IMutationBlock mutation = holder.mutation;
		holder.mutation = null;
		MutableBlockProxy proxy = new MutableBlockProxy(mutation.getAbsoluteLocation(), cuboid);
		Assert.assertTrue(mutation.applyMutation(holder.context, proxy));
		Assert.assertEquals(expectedBlock, proxy.getBlock());
		Assert.assertEquals(expectedFlags, proxy.getFlags());
		byte logicBits = proxy.potentialLogicChangeBits();
		proxy.writeBack(cuboid);
		return logicBits;
	}

	private void _runMutationWithLogicUpdate(_ContextHolder holder
			, CuboidData cuboid
			, AbsoluteLocation triggerLocation
			, AbsoluteLocation listenerLocation
			, Block triggerBlock
			, byte expectedFlags
	)
	{
		CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
		byte logicBits = _runMutationInContext(cuboid, holder, triggerBlock, expectedFlags);
		Set<AbsoluteLocation> logicChanges = new HashSet<>();
		LogicLayerHelpers.populateSetWithPotentialLogicChanges(logicChanges, triggerLocation, logicBits);
		LazyLocationCache<MutableBlockProxy> lazyMutableBlockCache = new LazyLocationCache<>(
				(AbsoluteLocation location) -> {
					// This test assumes we just have the one cuboid.
					Assert.assertTrue(cuboid.getCuboidAddress().equals(location.getCuboidAddress()));
					return new MutableBlockProxy(location, cuboid);
				}
		);
		PropagationHelpers.processPreviousTickLogicUpdates((IMutationBlock update) -> {
					if (listenerLocation.equals(update.getAbsoluteLocation()))
					{
						Assert.assertTrue(null == holder.mutation);
						holder.mutation = update;
					}
				}
				, cuboidAddress
				, Map.of(cuboid.getCuboidAddress(), List.copyOf(logicChanges))
				, (AbsoluteLocation location) -> {
					return lazyMutableBlockCache.apply(location);
				}
				, (AbsoluteLocation location) -> {
					return holder.context.previousBlockLookUp.apply(location);
				}
		);
		
		for (MutableBlockProxy proxy : lazyMutableBlockCache.getCachedValues())
		{
			proxy.writeBack(cuboid);
		}
	}

	private static void _stand(TickProcessingContext context, IMutablePlayerEntity mutableEntity)
	{
		EntityLocation startLocation = mutableEntity.getLocation();
		ViscosityReader reader = new ViscosityReader(ENV, context.previousBlockLookUp);
		boolean fromAbove = false;
		float inverseViscosity = reader.getInverseViscosity(startLocation.getBlockLocation(), fromAbove);
		long millis = context.millisPerTick;
		EntityLocation startVelocity = mutableEntity.getVelocityVector();
		float newZ = EntityMovementHelpers.zVelocityAfterGravity(startVelocity.z(), inverseViscosity, millis);
		EntityLocation newVelocity = new EntityLocation(startVelocity.x(), startVelocity.y(), newZ);
		float seconds = (float)millis / 1000.0f;
		EntityLocation vectorToMove = new EntityLocation(seconds * (startVelocity.x() + newVelocity.x()) / 2.0f
			, seconds * (startVelocity.y() + newVelocity.y()) / 2.0f
			, seconds * (startVelocity.z() + newVelocity.z()) / 2.0f
		);
		
		EntityLocation[] outLocation = new EntityLocation[1];
		EntityLocation[] outVelocity = new EntityLocation[1];
		EntityMovementHelpers.interactiveEntityMove(startLocation, ENV.creatures.PLAYER.volume(), vectorToMove, new EntityMovementHelpers.InteractiveHelper()
		{
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				outLocation[0] = finalLocation;
				outVelocity[0] = new EntityLocation(cancelX ? 0.0f : newVelocity.x()
					, cancelY ? 0.0f : newVelocity.y() 
					, cancelZ ? 0.0f : newVelocity.z()
				);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
			{
				return reader.getViscosityFraction(location, fromAbove);
			}
		});
		
		EntityChangeTopLevelMovement<IMutablePlayerEntity> stand = new EntityChangeTopLevelMovement<>(outLocation[0]
			, outVelocity[0]
			, EntityChangeTopLevelMovement.Intensity.STANDING
			, (byte)0
			, (byte)0
			, null
		);
		Assert.assertTrue(stand.applyChange(context, mutableEntity));
	}


	private static class _ContextHolder
	{
		public final TickProcessingContext context;
		public IEntityAction<IMutablePlayerEntity> change;
		public IMutationBlock mutation;
		public final _Events events = new _Events();
		
		public _ContextHolder(IReadOnlyCuboidData cuboid, boolean allowEntityChange, boolean allowBlockMutation)
		{
			this.context = ContextBuilder.build()
					.tick(5L)
					.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid), null)
					.sinks(allowBlockMutation ? new TickProcessingContext.IMutationSink() {
							@Override
							public void next(IMutationBlock mutation)
							{
								Assert.assertNull(_ContextHolder.this.mutation);
								_ContextHolder.this.mutation = mutation;
							}
							@Override
							public void future(IMutationBlock mutation, long millisToDelay)
							{
								Assert.fail("Not used in test");
							}
						} : null
						, allowEntityChange ? new TickProcessingContext.IChangeSink() {
							@Override
							public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
							{
								Assert.assertNull(_ContextHolder.this.change);
								_ContextHolder.this.change = change;
							}
							@Override
							public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
							{
								Assert.fail("Not expected in tets");
							}
							@Override
							public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
							{
								Assert.fail("Not expected in tets");
							}
						} : null)
					.eventSink(this.events)
					.finish()
			;
		}
	}

	private static class _Events implements TickProcessingContext.IEventSink
	{
		private EventRecord _expected;
		public void expected(EventRecord expected)
		{
			Assert.assertNull(_expected);
			_expected = expected;
		}
		public boolean didPost()
		{
			return (null == _expected);
		}
		@Override
		public void post(EventRecord event)
		{
			Assert.assertEquals(_expected, event);
			_expected = null;
		}
	}
}
