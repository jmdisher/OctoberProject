package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Receives a trade from a player (sent with EntitySubActionSendTrade) or other villager (sent directly).  While the
 * villager doesn't store any non-stackable meta-data, the player will still send it in case the trade fails and needs
 * to be returned.
 * The returned inventory, from either success or failure, will be sent in EntityActionStoreToInventory (to a player) or
 * EntityActionStoreToCreatureInventory (to another villager).
 */
public class EntityActionReceiveTrade implements IEntityAction<MutableCreature>
{
	private final ItemSlot _sentItems;
	private final Item _requestedType;
	private final int _traderId;

	public EntityActionReceiveTrade(ItemSlot sentItems, Item requestedType, int traderId)
	{
		_sentItems = sentItems;
		_requestedType = requestedType;
		_traderId = traderId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableCreature newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply;
		
		// Only villagers participate in trade (the caller should have checked this).
		Assert.assertTrue(env.creatures.getTypeById("op.villager") == newEntity.newType);
		
		// See if we should check what this villager is buying (the requested type is coins) or selling (the sell item type).
		Item coinType = env.items.getItemById("op.coin");
		if (coinType == _requestedType)
		{
			// They are requesting coins from us so that means we are buying what they are sending.
			// TODO:  Move this into IExtension.
			ExtensionVillager.Data data = (ExtensionVillager.Data)newEntity.newExtendedData;
			TradingRegistry.Profession profession = data.profession();
			
			// We only move a single item at a time (unless coins).
			Assert.assertTrue(1 == _sentItems.getCount());
			
			Item itemWeAreBuying = _sentItems.getType();
			
			// The caller should have validated that the receiver could buy this.
			Assert.assertTrue(profession.buyOffers().containsKey(itemWeAreBuying));
			
			// Just check that we are within limits (2x target).
			int inventoryLimit = 2 * profession.targetInventory().get(itemWeAreBuying);
			int currentCount = data.inventory().getOrDefault(itemWeAreBuying, 0);
			if (currentCount < inventoryLimit)
			{
				// Update our inventory.
				Map<Item, Integer> newInventory = new HashMap<>(data.inventory());
				newInventory.put(itemWeAreBuying, currentCount + 1);
				newEntity.newExtendedData = new ExtensionVillager.Data(profession, Collections.unmodifiableMap(newInventory));
				
				// Send back the coins.
				int coinCount = profession.buyOffers().get(itemWeAreBuying);
				Items coins = new Items(coinType, coinCount);
				_sendBackToTrader(context, coins, null);
				
				didApply = true;
			}
			else
			{
				// We are over-limit (probably due to a race) so send back their item.
				_sendBackToTrader(context, _sentItems.stack, _sentItems.nonStackable);
				
				didApply = false;
			}
		}
		else
		{
			// In this case, they must have sent us coins.
			Assert.assertTrue(coinType == _sentItems.getType());
			
			// They are requesting an item so that means that we are selling what they requested.
			// TODO:  Move this into IExtension.
			ExtensionVillager.Data data = (ExtensionVillager.Data)newEntity.newExtendedData;
			TradingRegistry.Profession profession = data.profession();
			
			// The caller should have validated that the receiver is selling this.
			Assert.assertTrue(profession.sellOffers().containsKey(_requestedType));
			
			// The caller should have validated that they sent the right number of coins.
			Assert.assertTrue(_sentItems.getCount() == profession.sellOffers().get(_requestedType));
			
			// Just check that we have one to sell.
			int currentCount = data.inventory().getOrDefault(_requestedType, 0);
			if (currentCount > 0)
			{
				int newCount = currentCount - 1;
				Map<Item, Integer> newInventory = new HashMap<>(data.inventory());
				if (newCount > 0)
				{
					newInventory.put(_requestedType, currentCount - 1);
				}
				else
				{
					newInventory.remove(_requestedType);
				}
				newEntity.newExtendedData = new ExtensionVillager.Data(profession, Collections.unmodifiableMap(newInventory));
				
				// Send the item.
				Items stack;
				NonStackableItem nonStack;
				if (env.durability.isStackable(_requestedType))
				{
					stack = new Items(_requestedType, 1);
					nonStack = null;
				}
				else
				{
					stack = null;
					nonStack = PropertyHelpers.newItemWithDefaults(env, _requestedType);
				}
				_sendBackToTrader(context, stack, nonStack);
				
				didApply = true;
			}
			else
			{
				// Our supply is exhausted (probably due to a race) so send back their money.
				_sendBackToTrader(context, _sentItems.stack, _sentItems.nonStackable);
				
				didApply = false;
			}
		}
		
		return didApply;
	}

	@Override
	public EntityActionType getType()
	{
		// Not in creature-only types.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Not in creature-only types.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Not in creature-only types.
		throw Assert.unreachable();
	}

	@Override
	public String toString()
	{
		return String.format("Villager receive trade %s, requesting %s", _sentItems, _requestedType);
	}


	private void _sendBackToTrader(TickProcessingContext context, Items stack, NonStackableItem nonStack)
	{
		if (_traderId > 0)
		{
			EntityActionStoreToInventory store = new EntityActionStoreToInventory(stack, nonStack);
			context.newChangeSink.next(_traderId, store);
		}
		else
		{
			ItemSlot slot = (null != stack)
				? ItemSlot.fromStack(stack)
				: ItemSlot.fromNonStack(nonStack)
			;
			EntityActionStoreToCreatureInventory store = new EntityActionStoreToCreatureInventory(slot);
			context.newChangeSink.creature(_traderId, store);
		}
	}
}
