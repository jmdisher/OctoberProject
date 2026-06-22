package com.jeffdisher.october.actions;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.NonStackableItem;


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
	private static EntityType VILLAGER;
	private static TradingRegistry.Profession FORESTER;

	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COIN = ENV.items.getItemById("op.coin");
		SAPLING = ENV.items.getItemById("op.sapling");
		STONE_HATCHET = ENV.items.getItemById("op.stone_hatchet");
		VILLAGER = ENV.creatures.getTypeById("op.villager");
		FORESTER = ENV.trading.getProfessionById("op.forester");
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
}
