package com.jeffdisher.october.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.utils.Assert;


/**
 * The bindings for creatures which can be spawned during cuboid generation.
 */
public class CreatureBindings
{
	public final EntityType cow;

	public CreatureBindings(Environment env) throws IOException
	{
		Map<String, EntityType> mapping;
		try (InputStream stream  = getClass().getClassLoader().getResourceAsStream("creature_bindings.tablist"))
		{
			FlatTabListCallbacks<String, EntityType> callbacks = new FlatTabListCallbacks<>((String value) -> value, new IValueTransformer<>() {
				@Override
				public EntityType transform(String value) throws TabListReader.TabListException
				{
					EntityType type = env.creatures.getTypeById(value);
					if (null == type)
					{
						throw new TabListReader.TabListException("Not a known creature: \"" + value + "\"");
					}
					return type;
				}
			});
			TabListReader.readEntireFile(callbacks, stream);
			mapping = callbacks.data;
		}
		catch (TabListReader.TabListException e)
		{
			// TODO:  Determine a better way to handle this.
			throw Assert.unexpected(e);
		}
		
		// We will require that all of these be found.
		this.cow = _requiredCreature(mapping, "cow");
	}


	private EntityType _requiredCreature(Map<String, EntityType> mapping, String name)
	{
		EntityType creature = mapping.get(name);
		Assert.assertTrue(null != creature);
		return creature;
	}
}
