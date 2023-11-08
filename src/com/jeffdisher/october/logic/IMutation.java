package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.data.CuboidData;


public interface IMutation
{
	int[] getAbsoluteLocation();
	boolean applyMutation(WorldState oldWorld, CuboidData newCuboid, Consumer<IMutation> newMutationSink);
}
