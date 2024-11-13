package com.jeffdisher.october.persistence.legacy;

import java.nio.ByteBuffer;

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

	public static LegacyEntityV1 load(ByteBuffer buffer)
	{
		// We just use the logic which was used in CodecHelpers to load this in V1.
		int id = buffer.getInt();
		boolean isCreativeMode = CodecHelpers.readBoolean(buffer);
		EntityLocation location = CodecHelpers.readEntityLocation(buffer);
		EntityLocation velocity = CodecHelpers.readEntityLocation(buffer);
		Inventory inventory = CodecHelpers.readInventory(buffer);
		int[] hotbar = new int[HOTBAR_SIZE];
		for (int i = 0; i < hotbar.length; ++i)
		{
			hotbar[i] = buffer.getInt();
		}
		int hotbarIndex = buffer.getInt();
		NonStackableItem[] armour = new NonStackableItem[BodyPart.values().length];
		for (int i = 0; i < armour.length; ++i)
		{
			armour[i] = CodecHelpers.readNonStackableItem(buffer);
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

	public void test_writeToBuffer(ByteBuffer buffer)
	{
		// NOTE:  This is just for testing.
		// We just use the logic which was used in CodecHelpers to load this in V1.
		int id = this.id();
		boolean isCreativeMode = this.isCreativeMode();
		EntityLocation location = this.location();
		EntityLocation velocity = this.velocity();
		Inventory inventory = this.inventory();
		int[] hotbar = this.hotbarItems();
		int hotbarIndex = this.hotbarIndex();
		NonStackableItem[] armour = this.armourSlots();
		CraftOperation localCraftOperation = this.localCraftOperation();
		
		buffer.putInt(id);
		CodecHelpers.writeBoolean(buffer, isCreativeMode);
		CodecHelpers.writeEntityLocation(buffer, location);
		CodecHelpers.writeEntityLocation(buffer, velocity);
		CodecHelpers.writeInventory(buffer, inventory);
		for (int key : hotbar)
		{
			buffer.putInt(key);
		}
		buffer.putInt(hotbarIndex);
		for (NonStackableItem piece : armour)
		{
			CodecHelpers.writeNonStackableItem(buffer, piece);
		}
		CodecHelpers.writeCraftOperation(buffer, localCraftOperation);
		buffer.put(this.health());
		buffer.put(this.food());
		buffer.put(this.breath());
		buffer.putInt(this.energyDeficit());
		CodecHelpers.writeEntityLocation(buffer, this.spawnLocation());
	}

	public Entity toEntity()
	{
		byte yaw = 0;
		byte pitch = 0;
		long ephemeral_lastSpecialActionMillis = 0L;
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
				, this.localCraftOperation
				, this.health
				, this.food
				, this.breath
				, this.energyDeficit
				, this.spawnLocation
				, ephemeral_lastSpecialActionMillis
		);
	}
}
