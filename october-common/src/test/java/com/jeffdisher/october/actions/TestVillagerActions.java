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
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.logic.CommonChangeSink;
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
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of());
		
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
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of(STONE_HATCHET, 1));
		
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
		forester.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of(LOG, 1));
		MutableCreature toolSmith = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		toolSmith.newExtendedData = new ExtensionVillager.Data(TOOL_SMITH, Map.of(STONE_HATCHET, 2));
		
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
		forester.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of(LOG, 1));
		MutableCreature toolSmith = MutableCreature.existing(CreatureEntity.create(-1, VILLAGER, new EntityLocation(5.0f, 5.0f, 5.0f), 1000L));
		toolSmith.newExtendedData = new ExtensionVillager.Data(TOOL_SMITH, Map.of(STONE_HATCHET, 2));
		
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
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of());
		MinimalEntity emptyMinimal = MinimalEntity.fromCreature(mutable.freeze());
		Assert.assertTrue(extension.canVillagerBuyItem(ENV, emptyMinimal, hatchetToBuy));
		Assert.assertEquals(20, extension.coinsToReturnForVillagerBuyTrade(ENV, mutable, hatchetToBuy));
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		
		// Show when the inventory is full.
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of(STONE_HATCHET, 2));
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
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of(LOG, 2));
		MinimalEntity itemMinimal = MinimalEntity.fromCreature(mutable.freeze());
		Assert.assertEquals(4, extension.coinCostOfVillagerTrade(ENV, itemMinimal, LOG));
		Assert.assertEquals(ItemSlot.fromStack(new Items(LOG, 1)), extension.purchaseToReturnForVillagerSellTrade(ENV, mutable, LOG, 4));
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(LOG).intValue());
		
		// Show when the inventory is empty.
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of());
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
		
		mutable.newExtendedData = new ExtensionVillager.Data(FORESTER, Map.of());
		
		extension.storeItemsToVillagerInventory(ENV, mutable, STONE_HATCHET);
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		
		extension.storeItemsToVillagerInventory(ENV, mutable, STONE_HATCHET);
		Assert.assertEquals(2, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		
		extension.storeItemsToVillagerInventory(ENV, mutable, SAPLING);
		Assert.assertEquals(2, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(STONE_HATCHET).intValue());
		Assert.assertEquals(1, ((ExtensionVillager.Data) mutable.newExtendedData).inventory().get(SAPLING).intValue());
	}
}
