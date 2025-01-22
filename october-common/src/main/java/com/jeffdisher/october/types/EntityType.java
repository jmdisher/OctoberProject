package com.jeffdisher.october.types;


/**
 * This is used closely by CreatureRegistry to determine server-side behaviour but also client-side audio, etc.
 */
public record EntityType(byte number
		, String id
		, String name
		, EntityVolume volume
		, float blocksPerSecond
		, byte maxHealth
) {}
