package com.jeffdisher.october.registries;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.utils.Assert;


/**
 * Just a container of all the Aspects defined in the base system.
 * The main reason for this, aside from documenting what they are, is to introduce a canonical index assignment.
 * In the future, this may be made into something more dynamic but there is no benefit to that, at this time.
 */
public class AspectRegistry
{
	public static final Aspect<Short> BLOCK = registerAspect(Short.class, (IOctree original) -> {
		return ((OctreeShort)original).cloneData();
	});
	public static final Aspect<Inventory> INVENTORY = registerAspect(Inventory.class, (IOctree original) -> {
		return ((OctreeObject)original).cloneData(Inventory.class, (Inventory originalValue) -> {
			return originalValue.copy();
		});
	});

	private static int _nextIndex = 0;
	public static final Aspect<?>[] ALL_ASPECTS;
	static {
		// Just verify indices are assigned as expected.
		Assert.assertTrue(0 == BLOCK.index());
		Assert.assertTrue(1 == INVENTORY.index());
		
		// Create the finished array, in-order.
		ALL_ASPECTS = new Aspect<?>[] {
			BLOCK,
			INVENTORY,
		};
	}


	public static <T> Aspect<T> registerAspect(Class<T> type, Function<IOctree, IOctree> deepMutableClone)
	{
		int index = _nextIndex;
		_nextIndex += 1;
		return new Aspect<>(index, type, deepMutableClone);
	}


	private AspectRegistry()
	{
		// No instantiation.
	}
}
