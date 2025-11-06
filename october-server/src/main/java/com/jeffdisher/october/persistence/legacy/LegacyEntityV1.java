package com.jeffdisher.october.persistence.legacy;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Reads the V1 version of the Entity object.
 * Note that this is a duplicate of the V1 version of Entity with some inlined reader code from CodecHelpers.
 */
public record LegacyEntityV1(int id
		, boolean isCreativeMode
		, EntityLocation location
		, EntityLocation velocity
		, Inventory inventory
		, int[] hotbarItems
		, int hotbarIndex
		, NonStackableItem[] armourSlots
		, CraftOperation localCraftOperation
		, byte health
		, byte food
		, byte breath
		, int energyDeficit
		, EntityLocation spawnLocation
)
{
	public static final int HOTBAR_SIZE = 9;

	public static LegacyEntityV1 load(DeserializationContext context)
	{
		// We just use the logic which was used in CodecHelpers to load this in V1.
		ByteBuffer buffer = context.buffer();
		int id = buffer.getInt();
		boolean isCreativeMode = CodecHelpers.readBoolean(buffer);
		EntityLocation location = CodecHelpers.readEntityLocation(buffer);
		EntityLocation velocity = CodecHelpers.readEntityLocation(buffer);
		Inventory inventory = CodecHelpers.readInventory(context);
		int[] hotbar = new int[HOTBAR_SIZE];
		for (int i = 0; i < hotbar.length; ++i)
		{
			hotbar[i] = buffer.getInt();
		}
		int hotbarIndex = buffer.getInt();
		NonStackableItem[] armour = new NonStackableItem[BodyPart.values().length];
		for (int i = 0; i < armour.length; ++i)
		{
			armour[i] = CodecHelpers.readNonStackableItem(context);
		}
		CraftOperation localCraftOperation = CodecHelpers.readCraftOperation(buffer);
		byte health = buffer.get();
		byte food = buffer.get();
		byte breath = buffer.get();
		int energyDeficit = buffer.getInt();
		EntityLocation spawn = CodecHelpers.readEntityLocation(buffer);
		
		return new LegacyEntityV1(id
				, isCreativeMode
				, location
				, velocity
				, inventory
				, hotbar
				, hotbarIndex
				, armour
				, localCraftOperation
				, health
				, food
				, breath
				, energyDeficit
				, spawn
		);
	}

	public Entity toEntity()
	{
		// We just drop localCraftingOperation and energyDeficity here since they are now ephemeral.
		byte yaw = 0;
		byte pitch = 0;
		return new Entity(this.id
				, this.isCreativeMode
				, this.location
				, this.velocity
				, yaw
				, pitch
				, this.inventory
				, this.hotbarItems
				, this.hotbarIndex
				, this.armourSlots
				, this.health
				, this.food
				, this.breath
				, this.spawnLocation
				, Entity.EMPTY_SHARED
				, Entity.EMPTY_LOCAL
		);
	}
}
