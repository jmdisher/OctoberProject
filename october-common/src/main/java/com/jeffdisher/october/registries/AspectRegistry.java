package com.jeffdisher.october.registries;

import java.util.function.Function;
import java.util.function.Supplier;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.IAspectCodec;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.InventoryAspectCodec;
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
	public static final Aspect<Short, OctreeShort> BLOCK = registerAspect(Short.class
			, OctreeShort.class
			, () -> OctreeShort.empty()
			, (OctreeShort original) -> {
				return original.cloneData();
			}
			// IAspectCodec only exists for OctreeObject types.
			, null
	);
	public static final Aspect<Inventory, OctreeObject> INVENTORY = registerAspect(Inventory.class
			, OctreeObject.class
			, () -> OctreeObject.create()
			, (OctreeObject original) -> {
				// Inventories are now immutable so just make a clone of the map.
				return original.cloneMapShallow();
			}
			, new InventoryAspectCodec()
	);

	private static int _nextIndex = 0;
	public static final Aspect<?,?>[] ALL_ASPECTS;
	static {
		// Just verify indices are assigned as expected.
		Assert.assertTrue(0 == BLOCK.index());
		Assert.assertTrue(1 == INVENTORY.index());
		
		// Create the finished array, in-order.
		ALL_ASPECTS = new Aspect<?,?>[] {
			BLOCK,
			INVENTORY,
		};
	}


	public static <T, O extends IOctree> Aspect<T, O> registerAspect(Class<T> type
			, Class<O> octreeType
			, Supplier<O> emptyTreeSupplier
			, Function<O, O> deepMutableClone
			, IAspectCodec<T> codec
	)
	{
		int index = _nextIndex;
		_nextIndex += 1;
		return new Aspect<>(index
				, type
				, octreeType
				, emptyTreeSupplier
				, deepMutableClone
				, codec
		);
	}


	private AspectRegistry()
	{
		// No instantiation.
	}
}
