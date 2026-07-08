package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.actions.EntityActionReceiveTrade;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
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

	private final CommonBreedingLogic _breeding;

	public ExtensionVillager()
	{
		_breeding = new CommonBreedingLogic((Object embeddedData) -> {
			Data data = (Data)embeddedData;
			return data.breeding;
		});
	}
	
	@Override
	public Object buildDefaultExtendedData(long gameTimeMillis)
	{
		// By default, the profession is null since we base it on our surroundings.
		return new Data(null
			, Map.of()
			, null
			, _breeding.buildDefault()
		);
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
		
		// We never store the item we want to purchase since it depends on the movement plan.
		Item itemToPurchase = null;
		
		// We do store the breeding data, but that is a common external type.
		CommonBreedingLogic.Data breeding = _breeding.readData(buffer, gameTimeMillis);
		return new Data(profession
			, Collections.unmodifiableMap(map)
			, itemToPurchase
			, breeding
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
		
		// We never store the item we want to purchase since it depends on the movement plan.
		
		// We do store the common breeding data, though.
		_breeding.writeData(buffer, safe.breeding, gameTimeMillis);
	}

	@Override
	public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
	{
		// Check the common breeding mode, first.
		EntityType.TargetEntity newTarget = _breeding.findBreedingPartner(creature, entityCollection);
		
		if (null == newTarget)
		{
			// NOTE:  This will update the internal data object if non-null.
			newTarget = _findTradingTarget(creature, entityCollection);
		}
		
		return newTarget;
	}

	@Override
	public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
	{
		int targetId = creature.movementPlan.targetEntityId();
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetId);
		
		boolean isValid = false;
		if (_breeding.isMutableInLoveMode(creature))
		{
			// We must be looking at a partner so make sure that they are here and still in breeding mode.
			CreatureEntity partner = entityCollection.getCreatureById(targetId);
			if (null != partner)
			{
				isValid = _breeding.isCreatureInLoveMode(partner);
			}
			else
			{
				isValid = false;
			}
		}
		else
		{
			CreatureEntity target = entityCollection.getCreatureById(targetId);
			if (null != target)
			{
				// It is still valid if it still has the item we want to buy.
				Data safe = (Data)creature.newExtendedData;
				Data other = (Data)target.extendedData();
				Set<Item> otherInventoryItems = other.inventory.keySet();
				isValid = otherInventoryItems.contains(safe.itemToPurchase);
			}
		}
		
		return isValid;
	}

	@Override
	public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context, EntityCollection entityCollection)
	{
		// First, try any common breeding actions.
		CommonBreedingLogic.Data ifChanged = _breeding.spawnOffspring(context, creature);
		if (null == ifChanged)
		{
			ifChanged = _breeding.impregnateTarget(context, creature);
		}
		Data data = (Data) creature.newExtendedData;
		if (null != ifChanged)
		{
			creature.newExtendedData = new Data(data.profession
				, data.inventory
				, data.itemToPurchase
				, ifChanged
			);
		}
		boolean didTakeAction = (null != ifChanged);
		
		// Handle that special start-up case where the profession is selected late.
		if (null == data.profession)
		{
			// We can't have taken breeding actions if we have no profession.
			Assert.assertTrue(!didTakeAction);
			
			TradingRegistry.Profession choice = _selectDefaultProfession(entityCollection, creature.newType, creature.newLocation);
			creature.newExtendedData = new Data(choice
				, data.inventory
				, data.itemToPurchase
				, data.breeding
			);
			didTakeAction = true;
		}
		
		if (!didTakeAction)
		{
			// See if we can craft something.
			if (creature.nextActionMillis <= context.currentTickTimeMillis)
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
				creature.nextActionMillis = context.currentTickTimeMillis + MILLIS_CRAFTING_COOLDOWN;
				creature.newExtendedData = new Data(profession
					, inventory
					, data.itemToPurchase
					, data.breeding
				);
				didTakeAction = (null != chosenCraft);
			}
		}
		
		if (!didTakeAction)
		{
			// See if we wanted to buy something from another villager.
			if (null != data.itemToPurchase)
			{
				// If they are no longer our target, drop the purchase plan.
				int targetId = creature.movementPlan.targetEntityId();
				if (CreatureEntity.NO_TARGET_ENTITY_ID != targetId)
				{
					// If we are close enough to send the purchase request, do that and clear the purchase plan.
					CreatureEntity target = entityCollection.getCreatureById(targetId);
					EntityType thisType = creature.getType();
					
					EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), thisType.volume());
					float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, target.location(), target.type().volume());
					float actionDistance = thisType.actionDistance();
					
					if (distance <= actionDistance)
					{
						// Send the trade request.
						Data other = (Data) target.extendedData();
						int coins = 0;
						for (Map.Entry<Item, Integer> elt : other.profession.sellOffers().entrySet())
						{
							if (data.itemToPurchase == elt.getKey())
							{
								coins = elt.getValue();
								break;
							}
						}
						// We already decided they could sell us this item.
						Assert.assertTrue(coins > 0);
						Item coin = Environment.getShared().items.getItemById("op.coin");
						ItemSlot sentItems = ItemSlot.fromStack(new Items(coin, coins));
						EntityActionReceiveTrade action = new EntityActionReceiveTrade(sentItems, data.itemToPurchase, creature.getId());
						context.newChangeSink.creature(targetId, action);
						
						// We can now clear the target and crafting purchase plan.
						creature.movementPlan = null;
						// TODO:  Remove this reset of the movement plan once the call into this is split into a different path.
						creature.nextMovementPlanMillis = context.currentTickTimeMillis + CreatureLogic.MINIMUM_MILLIS_TO_ACTION;
						creature.newExtendedData = new Data(data.profession
							, data.inventory
							, null
							, data.breeding
						);
						didTakeAction = true;
					}
				}
				else
				{
					creature.newExtendedData = new Data(data.profession
						, data.inventory
						, null
						, data.breeding
					);
				}
			}
		}
		
		return didTakeAction;
	}

	@Override
	public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
	{
		boolean didBecomePregnant = false;
		CommonBreedingLogic.Data dataIfChanged = _breeding.setCreaturePregnant(creature, sireLocation, gameTimeMillis);
		if (null != dataIfChanged)
		{
			Data data = (Data) creature.newExtendedData;
			creature.newExtendedData = new Data(data.profession
				, data.inventory
				, data.itemToPurchase
				, dataIfChanged
			);
			didBecomePregnant = true;
		}
		return didBecomePregnant;
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
				, data.itemToPurchase
				, data.breeding
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
				, data.itemToPurchase
				, data.breeding
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
		
		// If this is a response to a buy request, we would have already cleared that when we sent it.
		villager.newExtendedData = new ExtensionVillager.Data(profession
			, Collections.unmodifiableMap(newMap)
			, data.itemToPurchase
			, data.breeding
		);
	}

	/**
	 * A utility method so that tests can instantiate a Data object for the common cases without needing to be updated
	 * whenever new data is added to the structure.
	 * 
	 * @param profession The profession of store.
	 * @param inventory The inventory to store.
	 * @return The new Data object.
	 */
	public static Data test_createData(TradingRegistry.Profession profession, Map<Item, Integer> inventory)
	{
		return new Data(profession
			, inventory
			, null
			, new CommonBreedingLogic(null).buildDefault()
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
				shouldTry = _canCraft(inventory, craft);
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

	private boolean _canCraft(Map<Item, Integer> inventory, TradingRegistry.TradeCraft craft)
	{
		boolean canCraft = true;
		for (Map.Entry<Item, Integer> elt : craft.inputs().entrySet())
		{
			Item key = elt.getKey();
			int requirement = elt.getValue();
			if (inventory.getOrDefault(key, 0) < requirement)
			{
				// We can't do this one.
				canCraft = false;
				break;
			}
		}
		return canCraft;
	}

	private EntityType.TargetEntity _findTradingTarget(MutableCreature creature, EntityCollection entityCollection)
	{
		// We will choose a deliberate path if there is something we want to buy from another villager, and we find one selling it.
		// Note that "want to buy" is defined in a somewhat complex way:
		// -we have 0 of something we normally sell
		// -we don't have all of the ingredients required to craft it
		// -there is a villager in view distance who sells one of the missing ingredients and has some
		Data safe = (Data)creature.newExtendedData;
		
		// Check if we are missing anything we want to sell (profession can be null if not initialized).
		TradingRegistry.Profession profession = safe.profession;
		Map<Item, Integer> inventory = safe.inventory;
		Set<Item> itemsForSale = (null != profession)
			? profession.sellOffers().keySet()
			: Set.of()
		;
		List<TradingRegistry.TradeCraft> crafts = (null != profession)
			? profession.crafts()
			: List.of()
		;
		
		Set<Item> itemsWeCouldRequest = new HashSet<>();
		for (Item forSale : itemsForSale)
		{
			if (!inventory.containsKey(forSale))
			{
				// We are missing this item so see what we need to craft it.
				for (TradingRegistry.TradeCraft craft : crafts)
				{
					for (Map.Entry<Item, Integer> elt : craft.inputs().entrySet())
					{
						Item requirement = elt.getKey();
						if (inventory.getOrDefault(requirement, 0) < elt.getValue())
						{
							// We don't have enough of these to craft so see if we can buy one.
							itemsWeCouldRequest.add(requirement);
						}
					}
				}
			}
		}
		
		EntityType.TargetEntity target = null;
		if (!itemsWeCouldRequest.isEmpty())
		{
			EntityType type = creature.newType;
			Item[] itemToBuy = new Item[1];
			EntityType.TargetEntity[] out = new EntityType.TargetEntity[1];
			entityCollection.walkCreaturesInViewDistance(creature, (CreatureEntity entity) -> {
				if ((null == out[0]) && (type == entity.type()))
				{
					// This is a villager so see if they are selling any of the objects we need and if they have them in stock.
					Data data = (Data)entity.extendedData();
					Set<Item> otherInventoryItems = data.inventory.keySet();
					Set<Item> otherSales = data.profession.sellOffers().keySet();
					for (Item item : otherSales)
					{
						if (itemsWeCouldRequest.contains(item) && otherInventoryItems.contains(item))
						{
							itemToBuy[0] = item;
							out[0] = new EntityType.TargetEntity(entity.id(), entity.location());
						}
					}
				}
			});
			if (null != itemToBuy[0])
			{
				creature.newExtendedData = new Data(safe.profession
					, safe.inventory
					, itemToBuy[0]
					, safe.breeding
				);
			}
			target = out[0];
		}
		
		return target;
	}


	// Note that the profession defaults to null, since it is late-bound based on the surroundings.
	// The inventory is just a map since it doesn't care about non-stackable properties, just the total number of items.
	public static record Data(TradingRegistry.Profession profession
		, Map<Item, Integer> inventory
		// The item we want to purchase from our target villager.
		, Item itemToPurchase
		// Villagers can breed so we also use embed related data here.
		, CommonBreedingLogic.Data breeding
	) {}
}
