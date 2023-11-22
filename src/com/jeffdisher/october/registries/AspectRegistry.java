package com.jeffdisher.october.registries;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.utils.Assert;


/**
 * Just a container of all the Aspects defined in the base system.
 * The main reason for this, aside from documenting what they are, is to introduce a canonical index assignment.
 * In the future, this may be made into something more dynamic but there is no benefit to that, at this time.
 */
public class AspectRegistry
{
	public static final Aspect<Short> BLOCK = registerAspect(Short.class);
	public static final Aspect<Inventory> INVENTORY = registerAspect(Inventory.class);

	private static int _nextIndex = 0;
	static {
		// Just verify indices are assigned as expected.
		Assert.assertTrue(0 == BLOCK.index());
		Assert.assertTrue(1 == INVENTORY.index());
	}


	public static <T> Aspect<T> registerAspect(Class<T> type)
	{
		int index = _nextIndex;
		_nextIndex += 1;
		return new Aspect<>(index, type);
	}


	private AspectRegistry()
	{
		// No instantiation.
	}
}
