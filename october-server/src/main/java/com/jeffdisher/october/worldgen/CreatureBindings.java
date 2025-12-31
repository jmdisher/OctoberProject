package com.jeffdisher.october.worldgen;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.EntityType;


/**
 * The bindings for creatures which can be spawned during cuboid generation.
 * TODO:  Move this into declarative data.
 */
public class CreatureBindings
{
	public final EntityType cow;

	public CreatureBindings(Environment env)
	{
		this.cow = env.creatures.getTypeById("op.cow");
	}
}
