package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.actions.EntityActionReceiveTrade;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Sends the player's part of a trade to a villager.  While the villager doesn't store any non-stackable meta-data, the
 * player will still send it in case the trade fails and needs to be returned.
 * The returned inventory, from either success or failure, will be sent in EntityActionStoreToInventory.
 */
public class EntitySubActionSendTrade implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.SEND_TRADE;

	public static EntitySubActionSendTrade deserializeFromContext(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int localInventoryId = buffer.getInt();
		int targetVillagerId = buffer.getInt();
		Item itemToRequest = CodecHelpers.readItem(buffer);
		return new EntitySubActionSendTrade(localInventoryId, targetVillagerId, itemToRequest);
	}

	/**
	 * Builds and returns a map of trade offers which can currently be sent to the villager, where the price given for
	 * each item is the "buying price" meaning that negative numbers are the prices for what the villager is selling.
	 * 
	 * @param env The environment.
	 * @param villager The villager to examine.
	 * @return The map of items to the prices for which the villager will buy (or negative for selling offers).
	 */
	public static Map<Item, Integer> villagerTradeOffers(Environment env, MinimalEntity villager)
	{
		// This call assumes that the given entity is a villager.
		Assert.assertTrue(env.creatures.getTypeById("op.villager") == villager.type());
		
		return _villagerTradeOffers(villager);
	}

	/**
	 * Finds the key of a valid instance of tradeType in the given playerEntity's inventory, or 0 if there isn't one.
	 * 
	 * @param env The environment.
	 * @param playerEntity The player whose inventory should be checked.
	 * @param tradeType The item type to trade.
	 * @return The key of the tradeType instance in playerEntity or 0 if not found.
	 */
	public static int findKeyOfAcceptableLocalItemToTrade(Environment env, Entity playerEntity, Item tradeType)
	{
		int localInventoryId;
		
		// We just extract the normal inventory but we might extend this to creative if we want to support this in the sub-action, proper.
		Inventory inventory = playerEntity.inventory();
		
		// First check if this is stackable or not.
		if (env.durability.isStackable(tradeType))
		{
			// This is the basic case so just return it (returns 0 if not found).
			localInventoryId = inventory.getIdOfStackableType(tradeType);
		}
		else
		{
			// We might have more than one so find one which only has a single property:  Durability which matches default.
			localInventoryId = 0;
			for (Integer key : inventory.sortedKeys())
			{
				NonStackableItem nonStackable = inventory.getNonStackableForKey(key);
				if (null != nonStackable)
				{
					Item type = nonStackable.type();
					if ((type == tradeType)
						&& (1 == nonStackable.properties().size())
						&& (env.durability.getDurability(type) == PropertyHelpers.getDurability(nonStackable))
					)
					{
						localInventoryId = key;
						break;
					}
				}
			}
		}
		
		return localInventoryId;
	}


	private final int _localInventoryId;
	private final int _targetVillagerId;
	private final Item _itemToRequest;

	public EntitySubActionSendTrade(int localInventoryId, int targetVillagerId, Item itemToRequest)
	{
		Assert.assertTrue(localInventoryId > 0);
		Assert.assertTrue(targetVillagerId < 0);
		Assert.assertTrue(null != itemToRequest);
		
		_localInventoryId = localInventoryId;
		_targetVillagerId = targetVillagerId;
		_itemToRequest = itemToRequest;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		
		// We need to check what item this is, in our inventory, and what item we are requesting, make sure that they
		// match (exactly one is a coin), and check that the target is a villager who is offering this trade.
		Item coinType = env.items.getItemById("op.coin");
		IMutableInventory inventory = newEntity.accessMutableInventory();
		ItemSlot localSlot = inventory.getSlotForKey(_localInventoryId);
		Item typeToSend = localSlot.getType();
		
		// Note that we will use a wording of what the "villager" is doing, since that is how the profession names things, so this might seem a bit backward.
		// The villager is "buying" if we are sending them something and requesting coins.
		// The villager is "selling" if we are sending them coins and requesting something.
		boolean isBuy = (coinType == _itemToRequest);
		boolean isSell = (coinType == typeToSend);
		
		// Make sure that a villager buying a non-stackable is only getting a full durability one.
		if (isBuy && (null != localSlot.nonStackable))
		{
			isBuy = (1 == localSlot.nonStackable.properties().size())
				&& (env.durability.getDurability(typeToSend) == PropertyHelpers.getDurability(localSlot.nonStackable))
			;
		}
		
		ItemSlot toSend = null;
		if (isBuy != isSell)
		{
			// We are trading exactly one coin and one non-coin.
			EntityType villagerType = env.creatures.getTypeById("op.villager");
			MinimalEntity villager = context.previousEntityLookUp.getById(_targetVillagerId);
			
			boolean isInRange;
			if (null != villager)
			{
				EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(newEntity.getLocation(), newEntity.getType().volume());
				float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, villager.location(), villager.type().volume());
				isInRange = (distance <= MiscConstants.REACH_ENTITY);
			}
			else
			{
				isInRange = false;
			}
			
			if ((villagerType == villager.type()) && isInRange)
			{
				// We are trading with a villager in range.
				
				// If the villager is buying something from us, make sure that they are buying something we send.
				// If the villager is selling something to us, make sure they are selling what we are requesting.
				Map<Item, Integer> positiveCostValidBuyOffers = _villagerTradeOffers(villager);
				if (isBuy && (positiveCostValidBuyOffers.getOrDefault(typeToSend, 0) > 0))
				{
					// Remove this and send it.
					if (null != localSlot.nonStackable)
					{
						inventory.removeNonStackableItems(_localInventoryId);
						newEntity.clearHotBarWithKey(_localInventoryId);
						
						toSend = localSlot;
					}
					else
					{
						inventory.removeStackableItems(typeToSend, 1);
						if (0 == inventory.getCount(typeToSend))
						{
							newEntity.clearHotBarWithKey(_localInventoryId);
						}
						
						toSend = ItemSlot.fromStack(new Items(typeToSend, 1));
					}
				}
				else if (isSell)
				{
					// Is what we are requesting something that they sell and do we have enough coins?
					int cost = -positiveCostValidBuyOffers.getOrDefault(_itemToRequest, 0);
					int currentMoney = localSlot.getCount();
					if ((cost > 0) && (currentMoney >= cost))
					{
						// This is valid so remove the money and send the trade.
						inventory.removeStackableItems(coinType, cost);
						if (0 == inventory.getCount(coinType))
						{
							newEntity.clearHotBarWithKey(_localInventoryId);
						}
						
						Items coinsToSend = new Items(coinType, cost);
						toSend = ItemSlot.fromStack(coinsToSend);
					}
					else
					{
						// It isn't valid or we can't afford it.
					}
				}
				else
				{
					// This is an invalid trade request.
				}
			}
		}
		
		boolean didApply;
		if (null != toSend)
		{
			EntityActionReceiveTrade trade = new EntityActionReceiveTrade(toSend, _itemToRequest, newEntity.getId());
			context.newChangeSink.creature(_targetVillagerId, trade);
			didApply = true;
		}
		else
		{
			didApply = false;
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_localInventoryId);
		buffer.putInt(_targetVillagerId);
		CodecHelpers.writeItem(buffer, _itemToRequest);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Default case.
		return true;
	}

	@Override
	public String toString()
	{
		return String.format("Send trade (item %d) to villager %d", _localInventoryId, _targetVillagerId);
	}


	private static Map<Item, Integer> _villagerTradeOffers(MinimalEntity villager)
	{
		Map<Item, Integer> villagerBuyOffers = new HashMap<>();
		ExtensionVillager.Data data = (ExtensionVillager.Data) villager.extendedData();
		TradingRegistry.Profession profession = data.profession();
		if (null != profession)
		{
			Map<Item, Integer> inv = data.inventory();
			Map<Item, Integer> target = profession.targetInventory();
			for (Map.Entry<Item, Integer> buy : profession.buyOffers().entrySet())
			{
				Item key = buy.getKey();
				int count = inv.getOrDefault(key, 0);
				if (count < target.get(key))
				{
					// They will buy this so include it.
					villagerBuyOffers.put(key, buy.getValue());
				}
			}
			for (Map.Entry<Item, Integer> sell : profession.sellOffers().entrySet())
			{
				Item key = sell.getKey();
				int count = inv.getOrDefault(key, 0);
				if (count > 0)
				{
					// They can sell this so include it with a negated price.
					villagerBuyOffers.put(key, -sell.getValue());
				}
			}
		}
		return Collections.unmodifiableMap(villagerBuyOffers);
	}
}
