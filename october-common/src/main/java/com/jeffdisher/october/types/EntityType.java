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
		, Function<Object, ICreatureStateMachine> stateMachineFactory
) {}
