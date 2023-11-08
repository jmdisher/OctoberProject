package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;


public interface IMutation
{
	AbsoluteLocation getAbsoluteLocation();
	boolean applyMutation(WorldState oldWorld, CuboidData newCuboid, Consumer<IMutation> newMutationSink);
}
