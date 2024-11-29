package com.jeffdisher.october.types;


public record EventRecord(Type type
		, Cause cause
		, AbsoluteLocation location
		, int entityTarget
		, int entitySource
) {
	public enum Type
	{
		BLOCK_BROKEN,
		BLOCK_PLACED,
		ENTITY_HURT,
		ENTITY_KILLED,
		LIQUID_REMOVED,
		LIQUID_PLACED,
	}

	public enum Cause
	{
		NONE,
		ATTACKED,
		STARVATION,
		SUFFOCATION,
		FALL,
	}
}
