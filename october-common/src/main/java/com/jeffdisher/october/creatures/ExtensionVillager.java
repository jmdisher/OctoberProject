package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class ExtensionVillager implements EntityType.IExtension
{
	@Override
	public Object buildDefaultExtendedData(long gameTimeMillis)
	{
		// By default, the profession is null since we base it on our surroundings.
		return new Data(null, Map.of());
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
		return new Data(profession
			, Collections.unmodifiableMap(map)
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
			creature.newExtendedData = new Data(choice, data.inventory);
			didTakeAction = true;
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


	// Note that the profession defaults to null, since it is late-bound based on the surroundings.
	// The inventory is just a map since it doesn't care about non-stackable properties, just the total number of items.
	public static record Data(TradingRegistry.Profession profession
		, Map<Item, Integer> inventory
	) {}
}
