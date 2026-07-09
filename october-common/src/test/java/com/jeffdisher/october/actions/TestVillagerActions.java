package com.jeffdisher.october.actions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.creatures.CommonBreedingLogic;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TargetedAction;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A test suite related to actions associated with villager interactions.  These are handled in there own suite since
 * there are some complex mechanics associated with villagers which might as well be tested and managed together.
 */
public class TestVillagerActions
{
	private static Environment ENV;
	private static Item COIN;
	private static Item SAPLING;
	private static Item STONE_HATCHET;
	private static Item LOG;
	private static Item STICK;
	private static Item APPLE;
	private static EntityType VILLAGER;
	private static TradingRegistry.Profession FORESTER;
	private static TradingRegistry.Profession TOOL_SMITH;

	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COIN = ENV.items.getItemById("op.coin");
		SAPLING = ENV.items.getItemById("op.sapling");
		STONE_HATCHET = ENV.items.getItemById("op.stone_hatchet");
		LOG = ENV.items.getItemById("op.log");
		STICK = ENV.items.getItemById("op.stick");
		APPLE = ENV.items.getItemById("op.apple");
		VILLAGER = ENV.creatures.getTypeById("op.villager");
		FORESTER = ENV.trading.getProfessionById("op.forester");
		TOOL_SMITH = ENV.trading.getProfessionById("op.tool_smith");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void storeBuyResponseToVillager()
	{
		// Show that a villager's buy response will correctly update its inventory.
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		mutable.newExtendedData = _emptyData(FORESTER);
		
		// We show both the stackable and non-stackable examples.
		NonStackableItem hatchetItem = PropertyHelpers.newItemWithDefaults(ENV, STONE_HATCHET);
		Items saplingStack = new Items(SAPLING, 1);
		
		EntityActionStoreToCreatureInventory storeHatchet = new EntityActionStoreToCreatureInventory(ItemSlot.fromNonStack(hatchetItem));
		EntityActionStoreToCreatureInventory storeSapling = new EntityActionStoreToCreatureInventory(ItemSlot.fromStack(saplingStack));
		
		// We will also show the failed/refund case.
		Items refund = new Items(COIN, 20);
		EntityActionStoreToCreatureInventory refundCoins = new EntityActionStoreToCreatureInventory(ItemSlot.fromStack(refund));
		
		Assert.assertTrue(storeHatchet.applyChange(null, mutable));
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().size());
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		Assert.assertTrue(storeSapling.applyChange(null, mutable));
		Assert.assertEquals(2, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().size());
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(SAPLING).intValue());
		Assert.assertTrue(refundCoins.applyChange(null, mutable));
		Assert.assertEquals(2, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().size());
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(SAPLING).intValue());
	}

	@Test
	public void receiveTradeVillagerBuy()
	{
		// Show how the villager handles receiving a trade for one of its buy offers.
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		mutable.newExtendedData = _data(FORESTER, Map.of(STONE_HATCHET, 1));
		
		// Only players can fulfil the buy orders (villagers may satisfy their own buy orders by asking for another villager's sell orders).
		int playerId = 1;
		
		// We will make requests to fulfil its stackable and non-stackable buy orders.
		NonStackableItem nonStack = PropertyHelpers.newItemWithDefaults(ENV, STONE_HATCHET);
		EntityActionReceiveTrade hatchetOrder = new EntityActionReceiveTrade(ItemSlot.fromNonStack(nonStack), COIN, playerId);
		EntityActionReceiveTrade saplingOrder = new EntityActionReceiveTrade(ItemSlot.fromStack(new Items(SAPLING, 1)), COIN, playerId);
		
		// Run these and verify how the state changes and what responses we see.
		CommonChangeSink changes = new CommonChangeSink(Set.of(playerId), null, null);
		TickProcessingContext context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertTrue(hatchetOrder.applyChange(context, mutable));
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().size());
		Assert.assertEquals(2, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		List<TargetedAction<ScheduledChange>> list = changes.takeExportedChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("Store to entity inventory Items[type=Item[id=op.coin, name=Coin, number=119], count=20]", list.get(0).action().change().toString());
		
		changes = new CommonChangeSink(Set.of(playerId), null, null);
		context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertTrue(saplingOrder.applyChange(context, mutable));
		Assert.assertEquals(2, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().size());
		Assert.assertEquals(2, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(SAPLING).intValue());
		list = changes.takeExportedChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("Store to entity inventory Items[type=Item[id=op.coin, name=Coin, number=119], count=1]", list.get(0).action().change().toString());
		
		// Run the hatchet order again to see that it fails since we are over limit.
		changes = new CommonChangeSink(Set.of(playerId), null, null);
		context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertFalse(hatchetOrder.applyChange(context, mutable));
		Assert.assertEquals(2, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().size());
		Assert.assertEquals(2, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		Assert.assertEquals(1, ((ExtensionVillager.Data)mutable.newExtendedData).inventory().get(SAPLING).intValue());
		list = changes.takeExportedChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals(String.format("Store to entity inventory %s", nonStack), list.get(0).action().change().toString());
	}

	@Test
	public void receiveTradeVillagerSellPlayer()
	{
		// Show how the villager handles receiving a trade for one of its sell offers from a player.
		// This requires that we have a forester and a tool maker, so we can test both stackable and non-stackable.
		MutableCreature forester = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		forester.newExtendedData = _data(FORESTER, Map.of(LOG, 1));
		MutableCreature toolSmith = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		toolSmith.newExtendedData = _data(TOOL_SMITH, Map.of(STONE_HATCHET, 2));
		
		int playerId = 1;
		
		// We will make requests to fulfil its stackable and non-stackable buy orders.
		EntityActionReceiveTrade logOrder = new EntityActionReceiveTrade(ItemSlot.fromStack(new Items(COIN, 4)), LOG, playerId);
		EntityActionReceiveTrade hatchetOrder = new EntityActionReceiveTrade(ItemSlot.fromStack(new Items(COIN, 7)), STONE_HATCHET, playerId);
		
		// Run these and verify how the state changes and what responses we see.
		CommonChangeSink changes = new CommonChangeSink(Set.of(playerId), null, null);
		TickProcessingContext context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertTrue(hatchetOrder.applyChange(context, toolSmith));
		Assert.assertEquals(1, ((ExtensionVillager.Data)toolSmith.newExtendedData).inventory().size());
		Assert.assertEquals(1, ((ExtensionVillager.Data)toolSmith.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		List<TargetedAction<ScheduledChange>> list = changes.takeExportedChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertTrue(list.get(0).action().change().toString().startsWith("Store to entity inventory NonStackableItem[type=Item[id=op.stone_hatchet,"));
		
		changes = new CommonChangeSink(Set.of(playerId), null, null);
		context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertTrue(logOrder.applyChange(context, forester));
		Assert.assertEquals(0, ((ExtensionVillager.Data)forester.newExtendedData).inventory().size());
		list = changes.takeExportedChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("Store to entity inventory Items[type=Item[id=op.log, name=Log, number=2], count=1]", list.get(0).action().change().toString());
		
		// Run the log order again to see that it fails since they no longer have any.
		changes = new CommonChangeSink(Set.of(playerId), null, null);
		context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertFalse(logOrder.applyChange(context, forester));
		Assert.assertEquals(0, ((ExtensionVillager.Data)forester.newExtendedData).inventory().size());
		list = changes.takeExportedChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("Store to entity inventory Items[type=Item[id=op.coin, name=Coin, number=119], count=4]", list.get(0).action().change().toString());
	}

	@Test
	public void receiveTradeVillagerSellVillager()
	{
		// Show how the villager handles receiving a trade for one of its sell offers from another villager.
		// This requires that we have a forester and a tool maker, so we can test both stackable and non-stackable.
		MutableCreature forester = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		forester.newExtendedData = _data(FORESTER, Map.of(LOG, 1));
		MutableCreature toolSmith = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		toolSmith.newExtendedData = _data(TOOL_SMITH, Map.of(STONE_HATCHET, 2));
		
		int villagerId = -2;
		
		// We will make requests to fulfil its stackable and non-stackable buy orders.
		EntityActionReceiveTrade logOrder = new EntityActionReceiveTrade(ItemSlot.fromStack(new Items(COIN, 4)), LOG, villagerId);
		EntityActionReceiveTrade hatchetOrder = new EntityActionReceiveTrade(ItemSlot.fromStack(new Items(COIN, 7)), STONE_HATCHET, villagerId);
		
		// Run these and verify how the state changes and what responses we see.
		CommonChangeSink changes = new CommonChangeSink(null, Set.of(villagerId), null);
		TickProcessingContext context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertTrue(hatchetOrder.applyChange(context, toolSmith));
		Assert.assertEquals(1, ((ExtensionVillager.Data)toolSmith.newExtendedData).inventory().size());
		Assert.assertEquals(1, ((ExtensionVillager.Data)toolSmith.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		List<TargetedAction<IEntityAction<MutableCreature>>> list = changes.takeExportedCreatureChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("Store to creature inventory: Item[id=op.stone_hatchet, name=Stone Hatchet, number=63]", list.get(0).action().toString());
		
		changes = new CommonChangeSink(null, Set.of(villagerId), null);
		context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertTrue(logOrder.applyChange(context, forester));
		Assert.assertEquals(0, ((ExtensionVillager.Data)forester.newExtendedData).inventory().size());
		list = changes.takeExportedCreatureChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("Store to creature inventory: Item[id=op.log, name=Log, number=2](1)", list.get(0).action().toString());
		
		// Run the log order again to see that it fails since they no longer have any.
		changes = new CommonChangeSink(null, Set.of(villagerId), null);
		context = ContextBuilder.build().sinks(null, changes).finish();
		Assert.assertFalse(logOrder.applyChange(context, forester));
		Assert.assertEquals(0, ((ExtensionVillager.Data)forester.newExtendedData).inventory().size());
		list = changes.takeExportedCreatureChanges();
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("Store to creature inventory: Item[id=op.coin, name=Coin, number=119](4)", list.get(0).action().toString());
	}

	@Test
	public void extensionBuyHelpers()
	{
		// Show that the special helpers added for buy trades in ExtensionVillager work.
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		ExtensionVillager extension = (ExtensionVillager)mutable.getType().extension();
		ItemSlot hatchetToBuy = ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, STONE_HATCHET));
		
		// Show when the inventory is empty.
		mutable.newExtendedData = _emptyData(FORESTER);
		MinimalEntity emptyMinimal = MinimalEntity.fromCreature(mutable.freeze());
		Assert.assertTrue(extension.canVillagerBuyItem(ENV, emptyMinimal, hatchetToBuy));
		Assert.assertEquals(20, extension.coinsToReturnForVillagerBuyTrade(ENV, mutable, hatchetToBuy));
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		
		// Show when the inventory is full.
		mutable.newExtendedData = _data(FORESTER, Map.of(STONE_HATCHET, 2));
		MinimalEntity fullMinimal = MinimalEntity.fromCreature(mutable.freeze());
		Assert.assertFalse(extension.canVillagerBuyItem(ENV, fullMinimal, hatchetToBuy));
		Assert.assertEquals(0, extension.coinsToReturnForVillagerBuyTrade(ENV, mutable, hatchetToBuy));
		Assert.assertEquals(2, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
	}

	@Test
	public void extensionSellHelpers()
	{
		// Show that the special helpers added for sell trades in ExtensionVillager work.
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		ExtensionVillager extension = (ExtensionVillager)mutable.getType().extension();
		
		// Show when the inventory has items.
		mutable.newExtendedData = _data(FORESTER, Map.of(LOG, 2));
		MinimalEntity itemMinimal = MinimalEntity.fromCreature(mutable.freeze());
		Assert.assertEquals(4, extension.coinCostOfVillagerTrade(ENV, itemMinimal, LOG));
		Assert.assertEquals(ItemSlot.fromStack(new Items(LOG, 1)), extension.purchaseToReturnForVillagerSellTrade(ENV, mutable, LOG, 4));
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(LOG).intValue());
		
		// Show when the inventory is empty.
		mutable.newExtendedData = _emptyData(FORESTER);
		MinimalEntity emptyMinimal = MinimalEntity.fromCreature(mutable.freeze());
		Assert.assertEquals(0, extension.coinCostOfVillagerTrade(ENV, emptyMinimal, LOG));
		Assert.assertEquals(null, extension.purchaseToReturnForVillagerSellTrade(ENV, mutable, LOG, 4));
	}

	@Test
	public void storeToInventory()
	{
		// Show that the special helper to store to an inventory works.
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		ExtensionVillager extension = (ExtensionVillager)mutable.getType().extension();
		
		mutable.newExtendedData = _emptyData(FORESTER);
		
		extension.storeItemsToVillagerInventory(ENV, mutable, STONE_HATCHET);
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		
		extension.storeItemsToVillagerInventory(ENV, mutable, STONE_HATCHET);
		Assert.assertEquals(2, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		
		extension.storeItemsToVillagerInventory(ENV, mutable, SAPLING);
		Assert.assertEquals(2, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(SAPLING).intValue());
	}

	@Test
	public void craft()
	{
		// Show that we will craft a required object (we rely on CreatureLogic to impose any kind of common cooldown logic).
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		ExtensionVillager extension = (ExtensionVillager)mutable.getType().extension();
		
		mutable.nextActionMillis = 2L * ContextBuilder.DEFAULT_MILLIS_PER_TICK;
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER
			, Map.of(STONE_HATCHET, 1
				, SAPLING, 5
			)
			, null
			, new CommonBreedingLogic(null).buildDefault()
			, 0
		);
		
		// This should fail when still on cooldown.
		TickProcessingContext context = ContextBuilder.build()
			.tick(1L)
			.finish()
		;
		Assert.assertTrue(extension.didTakeSpecialAction(mutable, context, null));
		Assert.assertEquals(0, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().getOrDefault(STONE_HATCHET, 0).intValue());
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(SAPLING).intValue());
		Assert.assertEquals(4, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STICK).intValue());
		Assert.assertEquals(8, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(LOG).intValue());
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(APPLE).intValue());
	}

	@Test
	public void findPurchaseTarget()
	{
		// Show that a villager will find a nearby villager who is selling something they need, if they don't have any of a recipe's crafted outputs.
		MutableCreature toolMutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(7.0f, 7.0f, 5.0f), 1000L));
		toolMutable.newExtendedData = ExtensionVillager.test_createData(TOOL_SMITH, Map.of(STONE_HATCHET, 1));
		
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-2, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		mutable.newExtendedData = ExtensionVillager.test_createData(FORESTER, Map.of(SAPLING, 5));
		
		// Show that the forester will enter this mode to purchase a tool when asked to find a target.
		ExtensionVillager extension = (ExtensionVillager) mutable.newType.extension();
		EntityCollection entityCollection = EntityCollection.fromMaps(Map.of(), Map.of(-1, toolMutable.freeze(), -2, mutable.freeze()));
		
		EntityType.TargetEntity target = extension.findDeliberateTarget(mutable, entityCollection);
		Assert.assertEquals(toolMutable.getId(), target.id());
		Assert.assertEquals(toolMutable.getLocation(), target.location());
		Assert.assertEquals(STONE_HATCHET, ((ExtensionVillager.Data)mutable.newExtendedData).itemToPurchase());
	}

	@Test
	public void sendBuyOrderWhenClose()
	{
		// Show that a villager targeting another will send the buy order for its goods when it is within range.
		MutableCreature toolMutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(6.0f, 6.0f, 5.0f), 1000L));
		toolMutable.newExtendedData = ExtensionVillager.test_createData(TOOL_SMITH, Map.of(STONE_HATCHET, 1));
		
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-2, VILLAGER, new EntityLocation(5.5f, 5.5f, 5.0f), 1000L));
		mutable.movementPlan = new CreatureEntity.MovementPlan(null
			, -1
			, toolMutable.newLocation
			, toolMutable.newLocation
		);
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER
			, Map.of(SAPLING, 5)
			, STONE_HATCHET
			, new CommonBreedingLogic(null).buildDefault()
			, 0
		);
		
		// We should see the action sent to the tool smith and the purchase plan disappear.
		ExtensionVillager extension = (ExtensionVillager) mutable.newType.extension();
		CommonChangeSink changeSink = new CommonChangeSink(Set.of(), Set.of(-1, -2), Set.of());
		TickProcessingContext context = ContextBuilder.build()
			.tick(1L)
			.sinks(null, changeSink)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.fromMaps(Map.of(), Map.of(-1, toolMutable.freeze(), -2, mutable.freeze()));
		Assert.assertTrue(extension.didTakeSpecialAction(mutable, context, entityCollection));
		Assert.assertEquals(null, mutable.movementPlan);
		Assert.assertEquals(null, ((ExtensionVillager.Data)mutable.newExtendedData).itemToPurchase());
		
		List<TargetedAction<IEntityAction<MutableCreature>>> changes = changeSink.takeExportedCreatureChanges();
		Assert.assertEquals(1, changes.size());
		Assert.assertEquals("TargetedAction[targetId=-1, action=Villager receive trade Item[id=op.coin, name=Coin, number=119](7), requesting Item[id=op.stone_hatchet, name=Stone Hatchet, number=63]]", changes.get(0).toString());
	}

	@Test
	public void checkFinalBreedingSteps()
	{
		// Show that the final steps in the breeding operations work correctly with villagers, when the breeding state is set for the test.
		// Define 2 villagers in love mode.
		MutableCreature toolMutable = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(6.0f, 6.0f, 5.0f), 1000L));
		toolMutable.newExtendedData = new ExtensionVillager.Data(TOOL_SMITH
			, Map.of()
			, null
			, new CommonBreedingLogic.Data(true, null, 0L)
			, 0
		);
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-2, VILLAGER, new EntityLocation(5.5f, 5.5f, 5.0f), 1000L));
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER
			, Map.of()
			, null
			, new CommonBreedingLogic.Data(true, null, 0L)
			, 0
		);
		
		// Show that they both target each other.
		ExtensionVillager extension = (ExtensionVillager) mutable.newType.extension();
		EntityCollection entityCollection = EntityCollection.fromMaps(Map.of(), Map.of(-1, toolMutable.freeze(), -2, mutable.freeze()));
		
		EntityType.TargetEntity toolTarget = extension.findDeliberateTarget(toolMutable, entityCollection);
		EntityType.TargetEntity foresterTarget = extension.findDeliberateTarget(mutable, entityCollection);
		Assert.assertEquals(mutable.getId(), toolTarget.id());
		Assert.assertEquals(mutable.newLocation, toolTarget.location());
		Assert.assertEquals(toolMutable.getId(), foresterTarget.id());
		Assert.assertEquals(toolMutable.newLocation, foresterTarget.location());
		
		// Normally, CreatureLogic creates the movement plan which we will rely on to determine our target so create that, now.
		toolMutable.movementPlan = new CreatureEntity.MovementPlan(null
			, mutable.getId()
			, null
			, null
		);
		mutable.movementPlan = new CreatureEntity.MovementPlan(null
			, toolMutable.getId()
			, null
			, null
		);
		
		// Show that the larger ID will impregnate the lesser.
		Map<Integer, MinimalEntity> map = Map.of(
			toolMutable.getId(), MinimalEntity.fromCreature(toolMutable.freeze())
			, mutable.getId(), MinimalEntity.fromCreature(mutable.freeze())
		);
		CommonChangeSink sink = new CommonChangeSink(null, map.keySet(), null);
		EntityLocation[] out_spawn = new EntityLocation[1];
		TickProcessingContext context = ContextBuilder.build()
			.tick(1L)
			.lookups(null, new TickProcessingContext.IEntitySearch() {
				@Override
				public MinimalEntity getById(int id)
				{
					return map.get(id);
				}
				@Override
				public int[] findEntityIdsInRegion(EntityLocation base, EntityLocation edge)
				{
					throw new AssertionError("Not in test");
				}
			}, null)
			.sinks(null, sink)
			.spawner((EntityType type, EntityLocation location) -> {
				Assert.assertNull(out_spawn[0]);
				Assert.assertEquals(ENV.creatures.getTypeById("op.villager_baby"), type);
				out_spawn[0] = location;
			})
			.finish()
		;
		Assert.assertTrue(extension.didTakeSpecialAction(toolMutable, context, entityCollection));
		Assert.assertFalse(extension.didTakeSpecialAction(mutable, context, entityCollection));
		List<TargetedAction<IEntityAction<MutableCreature>>> actions = sink.takeExportedCreatureChanges();
		Assert.assertEquals(1, actions.size());
		IEntityAction<MutableCreature> action = actions.get(0).action();
		Assert.assertEquals("Impregnate by sire at EntityLocation[x=6.0, y=6.0, z=5.0]", action.toString());
		
		// Apply the call to the other mutable and then verify that the spawn happens correctly on next special action.
		Assert.assertTrue(action.applyChange(context, mutable));
		Assert.assertFalse(extension.didTakeSpecialAction(toolMutable, context, entityCollection));
		Assert.assertTrue(extension.didTakeSpecialAction(mutable, context, entityCollection));
		Assert.assertEquals(new EntityLocation(5.75f, 5.75f, 5.0f), out_spawn[0]);
	}


	private static ExtensionVillager.Data _emptyData(TradingRegistry.Profession profession)
	{
		return ExtensionVillager.test_createData(profession, Map.of());
	}

	private static ExtensionVillager.Data _data(TradingRegistry.Profession profession, Map<Item, Integer> inventory)
	{
		return ExtensionVillager.test_createData(profession, inventory);
	}
}
