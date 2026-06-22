package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The final step in a trade (whether it passed or failed) received by the the initiator of a trade action, sent by the
 * target via EntityActionReceiveTrade.
 * Note that this is specifically for the case where the initiator was another villager.  In the case where the
 * initiator was a player entity, EntityActionStoreToInventory is used.
 */
public class EntityActionStoreToCreatureInventory implements IEntityAction<MutableCreature>
{
	private final ItemSlot _itemSlot;

	public EntityActionStoreToCreatureInventory(ItemSlot itemSlot)
	{
		_itemSlot = itemSlot;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableCreature newEntity)
	{
		Environment env = Environment.getShared();
		
		// Only villagers have inventory.
		Assert.assertTrue(env.creatures.getTypeById("op.villager") == newEntity.newType);
		
		// This can be coins (if the trade failed) or items (if the trade was a success).
		Item slotType = _itemSlot.getType();
		if (env.items.getItemById("op.coin") == slotType)
		{
			// Coins are created/destroyed by villagers so just drop these.
		}
		else
		{
			// The trade was a success so this better be something we need as a trade input.
			// TODO:  Move this into IExtension.
			ExtensionVillager.Data data = (ExtensionVillager.Data)newEntity.newExtendedData;
			TradingRegistry.Profession profession = data.profession();
			
			// This must be something we are trying to buy if we bought it from another villager.
			Assert.assertTrue(profession.buyOffers().containsKey(slotType));
			
			// Store it in our inventory (note that we drop non-stackable properties at this point).
			Map<Item, Integer> inventory = data.inventory();
			int oldCount = inventory.getOrDefault(slotType, 0);
			int newCount = oldCount + _itemSlot.getCount();
			
			// The profession is read-only so rebuild its map.
			Map<Item, Integer> newMap = new HashMap<>(inventory);
			newMap.put(slotType, newCount);
			
			newEntity.newExtendedData = new ExtensionVillager.Data(profession, Collections.unmodifiableMap(newMap));
		}
		
		// Both of these are valid since we assert that the sender only talked to possibly-valid targets,
		return true;
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
		return String.format("Store to creature inventory: %s", _itemSlot);
	}
}
