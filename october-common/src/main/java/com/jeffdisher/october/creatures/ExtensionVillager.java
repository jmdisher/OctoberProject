package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class ExtensionVillager implements EntityType.IExtension
{
	/**
	 * The timeout between attempts at crafting operations (10s).
	 */
	public static final long MILLIS_CRAFTING_COOLDOWN = 10L * 1000L;

	@Override
	public Object buildDefaultExtendedData(long gameTimeMillis)
	{
		// By default, the profession is null since we base it on our surroundings.
		return new Data(null, Map.of(), 0L);
	}

	@Override
	public Object readExtendedData(ByteBuffer buffer, long gameTimeMillis)
	{
		// We store the profession as the ID, but an empty string will be null.
		String professionId = CodecHelpers.readString(buffer);
		TradingRegistry.Profession profession = (0 == professionId.length())
			? null
			: Environment.getShared().trading.getProfessionById(professionId)
		;
		
		// Then, we read the size of the map (these are small, so just a byte).
		byte size = buffer.get();
		Map<Item, Integer> map = new HashMap<>();
		for (byte i = 0; i < size; ++i)
		{
			Item item = CodecHelpers.readItem(buffer);
			Assert.assertTrue(null != item);
			
			int count = buffer.getInt();
			Assert.assertTrue(count > 0);
			
			Object old = map.put(item, count);
			Assert.assertTrue(null == old);
		}
		long craftingCooldownRelativeMillis = buffer.getLong();
		return new Data(profession
			, Collections.unmodifiableMap(map)
			, craftingCooldownRelativeMillis + gameTimeMillis
		);
	}

	@Override
	public void writeExtendedData(ByteBuffer buffer, Object extendedData, long gameTimeMillis)
	{
		Data safe = (Data)extendedData;
		String professionId = (null != safe.profession)
			? safe.profession.id()
			: ""
		;
		CodecHelpers.writeString(buffer, professionId);
		
		byte size = (byte)safe.inventory.size();
		buffer.put(size);
		for (Map.Entry<Item, Integer> ent : safe.inventory.entrySet())
		{
			CodecHelpers.writeItem(buffer, ent.getKey());
			int count = ent.getValue();
			Assert.assertTrue(count > 0);
			buffer.putInt(count);
		}
		
		long craftingCooldownRelativeMillis = safe.craftingReadyMillis - gameTimeMillis;
		if (craftingCooldownRelativeMillis < 0L)
		{
			craftingCooldownRelativeMillis = 0L;
		}
		buffer.putLong(craftingCooldownRelativeMillis);
	}

	@Override
	public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
	{
		// These never have deliberate paths.
		return null;
	}

	@Override
	public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
	{
		throw Assert.unreachable();
	}

	@Override
	public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context, EntityCollection entityCollection)
	{
		boolean didTakeAction = false;
		Data data = (Data) creature.newExtendedData;
		
		// The only special action we will take is choosing an occupation if we don't already have one.
		if (null == data.profession)
		{
			TradingRegistry.Profession choice = _selectDefaultProfession(entityCollection, creature.newType, creature.newLocation);
			creature.newExtendedData = new Data(choice
				, data.inventory
				, data.craftingReadyMillis
			);
			didTakeAction = true;
		}
		
		if (!didTakeAction)
		{
			// See if we can craft something.
			if (data.craftingReadyMillis <= context.currentTickTimeMillis)
			{
				TradingRegistry.Profession profession = data.profession;
				Map<Item, Integer> inventory = data.inventory;
				
				// Check the profession's crafting recipes and see if we can and should complete any of them (we will only choose one).
				TradingRegistry.TradeCraft chosenCraft = _findCraftToRun(profession, inventory);
				if (null != chosenCraft)
				{
					inventory = _applyCraftToInventory(chosenCraft, inventory);
				}
				
				// Whether we chose something or not, we want to update our timeout.
				long nextReadyMillis = context.currentTickTimeMillis + MILLIS_CRAFTING_COOLDOWN;
				creature.newExtendedData = new Data(profession
					, inventory
					, nextReadyMillis
				);
				didTakeAction = (null != chosenCraft);
			}
		}
		
		return didTakeAction;
	}

	@Override
	public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
	{
		throw Assert.unreachable();
	}

	@Override
	public boolean shouldDespawn(MutableCreature creature, TickProcessingContext context)
	{
		// Villagers never automatically despawn.
		return false;
	}

	@Override
	public boolean canApplyItemToCreature(MinimalEntity creature, Item itemType, long gameTimeMillis)
	{
		// We don't do direct item application to villagers.
		return false;
	}

	@Override
	public boolean applyItemToCreature(MutableCreature creature, Item itemType, long gameTimeMillis)
	{
		// We don't do direct item application to villagers.
		return false;
	}

	/**
	 * A read-only check to see if villager can buy itemSlotForVillagerToBuy.
	 * 
	 * @param env The environment.
	 * @param villager The minimal villager.
	 * @param itemSlotForVillagerToBuy The item slot containing the item we want the villager to buy.
	 * @return True if this villager may be able to buy the item (assuming they have inventory space when it is truly
	 * offered).
	 */
	public boolean canVillagerBuyItem(Environment env, MinimalEntity villager, ItemSlot itemSlotForVillagerToBuy)
	{
		ExtensionVillager.Data data = (ExtensionVillager.Data) villager.extendedData();
		TradingRegistry.Profession profession = data.profession();
		Item itemTypeToBuy = itemSlotForVillagerToBuy.getType();
		
		// Make sure that this is a valid buy offer and that we have inventory space for it.
		boolean canBuy = false;
		if (profession.buyOffers().containsKey(itemTypeToBuy)
			&& (data.inventory().getOrDefault(itemTypeToBuy, 0) < profession.targetInventory().get(itemTypeToBuy))
		)
		{
			// Make sure that this is valid (either a stack or full durability).
			if ((null == itemSlotForVillagerToBuy.nonStackable)
				|| (env.durability.getDurability(itemTypeToBuy) == PropertyHelpers.getDurability(itemSlotForVillagerToBuy.nonStackable))
			)
			{
				canBuy = true;
			}
		}
		return canBuy;
	}

	/**
	 * A read-only check to see if villager is able to sell itemToRequest.
	 * 
	 * @param env The environment.
	 * @param villager The minimal villager.
	 * @param itemToRequest The item showing what we want the villager to sell.
	 * @return The cost of this item, in coins, or 0 if not available.  Note that the villager may still fail to sell if
	 * it has no inventory when truly asked.
	 */
	public int coinCostOfVillagerTrade(Environment env, MinimalEntity villager, Item itemToRequest)
	{
		ExtensionVillager.Data data = (ExtensionVillager.Data) villager.extendedData();
		TradingRegistry.Profession profession = data.profession();
		
		// Make sure that this is a valid sell offer and that we have the item in stock (return 0 if not valid).
		int coinCost = 0;
		if (profession.sellOffers().containsKey(itemToRequest) && data.inventory.containsKey(itemToRequest))
		{
			coinCost = profession.sellOffers().get(itemToRequest);
		}
		return coinCost;
	}

	/**
	 * The number of coins a villager should return when it buys itemSlotForVillagerToBuy, 0 if it didn't buy it.
	 * NOTE:  This call modifies villager.
	 * 
	 * @param env The environment.
	 * @param villager The mutable villager (can be modified by this call).
	 * @param itemSlotForVillagerToBuy The item slot containing the item we want the villager to buy.
	 * @return The number of coins to return if the trade was accepted, 0 if it should be rejected.
	 */
	public int coinsToReturnForVillagerBuyTrade(Environment env, MutableCreature villager, ItemSlot itemSlotForVillagerToBuy)
	{
		ExtensionVillager.Data data = (ExtensionVillager.Data) villager.newExtendedData;
		TradingRegistry.Profession profession = data.profession();
		Item itemTypeToBuy = itemSlotForVillagerToBuy.getType();
		
		// This is only called from an internal action, so it must be a valid buy offer.
		Assert.assertTrue(profession.buyOffers().containsKey(itemTypeToBuy));
		
		// Just check that we are within limits.
		int inventoryLimit = profession.targetInventory().get(itemTypeToBuy);
		int currentCount = data.inventory().getOrDefault(itemTypeToBuy, 0);
		int coinsToReturn;
		if (currentCount < inventoryLimit)
		{
			// Update our inventory.
			Map<Item, Integer> newInventory = new HashMap<>(data.inventory());
			newInventory.put(itemTypeToBuy, currentCount + 1);
			villager.newExtendedData = new ExtensionVillager.Data(profession
				, Collections.unmodifiableMap(newInventory)
				, data.craftingReadyMillis
			);
			
			// Send back the coins.
			coinsToReturn = profession.buyOffers().get(itemTypeToBuy);
		}
		else
		{
			// We return 0 coins on error.
			coinsToReturn = 0;
		}
		return coinsToReturn;
	}

	/**
	 * The item slot a villager should send back to the buyer or an item it has just sold, or null if it couldn't sell
	 * the itemToRequest item.
	 * 
	 * @param env The environment.
	 * @param villager The mutable villager (can be modified by this call).
	 * @param itemToRequest The item type requested.
	 * @param coinsProvided The number of coins provided.
	 * @return The item to send back, if the trade is a success, or null if the trade should be rejected.
	 */
	public ItemSlot purchaseToReturnForVillagerSellTrade(Environment env, MutableCreature villager, Item itemToRequest, int coinsProvided)
	{
		ExtensionVillager.Data data = (ExtensionVillager.Data) villager.newExtendedData;
		TradingRegistry.Profession profession = data.profession();
		
		// This is only called from an internal action, so it must be a valid sell offer.
		Assert.assertTrue(profession.sellOffers().containsKey(itemToRequest));
		
		// The caller should have validated that they sent the right number of coins.
		Assert.assertTrue(coinsProvided == profession.sellOffers().get(itemToRequest));
		
		// Just check that we have one to sell.
		int currentCount = data.inventory().getOrDefault(itemToRequest, 0);
		ItemSlot toReturn;
		if (currentCount > 0)
		{
			int newCount = currentCount - 1;
			Map<Item, Integer> newInventory = new HashMap<>(data.inventory());
			if (newCount > 0)
			{
				newInventory.put(itemToRequest, currentCount - 1);
			}
			else
			{
				newInventory.remove(itemToRequest);
			}
			villager.newExtendedData = new ExtensionVillager.Data(profession
				, Collections.unmodifiableMap(newInventory)
				, data.craftingReadyMillis
			);
			
			// Package up what we should send them.
			if (env.durability.isStackable(itemToRequest))
			{
				Items stack = new Items(itemToRequest, 1);
				toReturn = ItemSlot.fromStack(stack);
			}
			else
			{
				NonStackableItem nonStack = PropertyHelpers.newItemWithDefaults(env, itemToRequest);
				toReturn = ItemSlot.fromNonStack(nonStack);
			}
		}
		else
		{
			// Our supply is exhausted (probably due to a race) so we will fail.
			toReturn = null;
		}
		return toReturn;
	}

	/**
	 * Stores items into a villager's inventory, but only if it is something the villager can buy.
	 * 
	 * @param env The environment.
	 * @param villager The mutable villager (will be modified by this call).
	 * @param itemType The item type to store one of.
	 */
	public void storeItemsToVillagerInventory(Environment env, MutableCreature villager, Item itemType)
	{
		ExtensionVillager.Data data = (ExtensionVillager.Data) villager.newExtendedData;
		TradingRegistry.Profession profession = data.profession();
		
		// This must be something we are trying to buy if we bought it from another villager.
		Assert.assertTrue(profession.buyOffers().containsKey(itemType));
		
		// Store it in our inventory.
		Map<Item, Integer> inventory = data.inventory();
		int oldCount = inventory.getOrDefault(itemType, 0);
		int newCount = oldCount + 1;
		
		// The profession is read-only so rebuild its map.
		Map<Item, Integer> newMap = new HashMap<>(inventory);
		newMap.put(itemType, newCount);
		
		villager.newExtendedData = new ExtensionVillager.Data(profession
			, Collections.unmodifiableMap(newMap)
			, data.craftingReadyMillis
		);
	}


	private static TradingRegistry.Profession _selectDefaultProfession(EntityCollection entityCollection
		, EntityType type
		, EntityLocation location
	)
	{
		Environment env = Environment.getShared();
		Map<TradingRegistry.Profession, Integer> existingCount = new HashMap<>();
		for (TradingRegistry.Profession prof : env.trading.getAllProfessions())
		{
			existingCount.put(prof, 0);
		}
		
		float vision = type.viewDistance();
		entityCollection.findIntersections(env
			, location
			, vision
			, null
			, (CreatureEntity match, EntityLocation centre, float radius) -> {
				if (match.type() == type)
				{
					// This is also a villager so consider its profession.
					Data other = (Data) match.extendedData();
					if (null != other.profession)
					{
						int count = existingCount.get(other.profession);
						existingCount.put(other.profession, count + 1);
					}
				}
			}
		);
		
		// Just pick the first one with the lowest count (we may want to randomize this in the future to avoid all the villagers in a cuboid making the same decision).
		TradingRegistry.Profession choice = null;
		int threshold = Integer.MAX_VALUE;
		for (Map.Entry<TradingRegistry.Profession, Integer> ent : existingCount.entrySet())
		{
			int weight = ent.getValue();
			if (weight < threshold)
			{
				choice = ent.getKey();
				threshold = weight;
			}
		}
		Assert.assertTrue(null != choice);
		return choice;
	}

	private TradingRegistry.TradeCraft _findCraftToRun(TradingRegistry.Profession profession
		, Map<Item, Integer> inventory
	)
	{
		TradingRegistry.TradeCraft chosenCraft = null;
		Map<Item, Integer> target = profession.targetInventory();
		for (TradingRegistry.TradeCraft craft : profession.crafts())
		{
			// If none of the outputs are currently full, we can try this one.
			boolean shouldTry = true;
			for (Item output : craft.outputs().keySet())
			{
				if (inventory.getOrDefault(output, 0) >= target.get(output))
				{
					shouldTry = false;
					break;
				}
			}
			if (shouldTry)
			{
				// So long as none of the requirements are missing, we will choose this one.
				for (Map.Entry<Item, Integer> elt : craft.inputs().entrySet())
				{
					Item key = elt.getKey();
					int requirement = elt.getValue();
					if (inventory.getOrDefault(key, 0) < requirement)
					{
						// We can't do this one.
						shouldTry = false;
						break;
					}
				}
				if (shouldTry)
				{
					chosenCraft = craft;
					break;
				}
			}
		}
		return chosenCraft;
	}

	private Map<Item, Integer> _applyCraftToInventory(TradingRegistry.TradeCraft chosenCraft
		, Map<Item, Integer> inventory
	)
	{
		Map<Item, Integer> mutable = new HashMap<>(inventory);
		for (Map.Entry<Item, Integer> elt : chosenCraft.inputs().entrySet())
		{
			Item key = elt.getKey();
			int count = mutable.get(key) - elt.getValue();
			if (count > 0)
			{
				mutable.put(key, count);
			}
			else
			{
				mutable.remove(key);
			}
		}
		for (Map.Entry<Item, Integer> elt : chosenCraft.outputs().entrySet())
		{
			Item key = elt.getKey();
			int count = mutable.getOrDefault(key, 0) + elt.getValue();
			mutable.put(key, count);
		}
		return Collections.unmodifiableMap(mutable);
	}


	// Note that the profession defaults to null, since it is late-bound based on the surroundings.
	// The inventory is just a map since it doesn't care about non-stackable properties, just the total number of items.
	public static record Data(TradingRegistry.Profession profession
		, Map<Item, Integer> inventory
		// The gameTimeMillis when crafting becomes available again (cooldown).
		, long craftingReadyMillis
	) {}
}
