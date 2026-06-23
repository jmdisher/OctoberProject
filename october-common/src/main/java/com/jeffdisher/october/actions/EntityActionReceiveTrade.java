package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.ExtensionVillager;
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
			
			// We only move a single item at a time (unless coins).
			Assert.assertTrue(1 == _sentItems.getCount());
			
			// Note that we down-cast the IExtension here since we already know the type and there isn't a reason to generalize this.
			ExtensionVillager extension = (ExtensionVillager)newEntity.newType.extension();
			
			// Check that we can satisfy this trade.
			int coinCount = extension.coinsToReturnForVillagerBuyTrade(env, newEntity, _sentItems);
			if (coinCount > 0)
			{
				// Success, so send the coins back.
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
			
			// Note that we down-cast the IExtension here since we already know the type and there isn't a reason to generalize this.
			ExtensionVillager extension = (ExtensionVillager)newEntity.newType.extension();
			
			// See if this was a success.
			ItemSlot success = extension.purchaseToReturnForVillagerSellTrade(env, newEntity, _requestedType, _sentItems.getCount());
			if (null != success)
			{
				_sendBackToTrader(context, success.stack, success.nonStackable);
				
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
