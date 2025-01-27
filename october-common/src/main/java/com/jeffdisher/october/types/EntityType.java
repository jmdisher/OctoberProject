package com.jeffdisher.october.types;

import java.util.function.Function;

import com.jeffdisher.october.creatures.ICreatureStateMachine;


/**
 * This is used closely by CreatureRegistry to determine server-side behaviour but also client-side audio, etc.
 */
public record EntityType(byte number
		, String id
		, String name
		, EntityVolume volume
		, float blocksPerSecond
		, byte maxHealth
		, float viewDistance
		, float actionDistance
		, byte attackDamage
		, Items[] drops
		, Item breedingItem
		, Function<Object, ICreatureStateMachine> stateMachineFactory
)
{
	public float getPathDistance()
	{
		// Use 2x the view distance to account for obstacles.
		return 2.0f * this.viewDistance;
	}

	public boolean canDespawn()
	{
		// Anything which can't breed can despawn.
		return (null == this.breedingItem);
	}
}
