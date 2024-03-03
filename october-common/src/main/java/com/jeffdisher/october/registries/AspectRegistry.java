package com.jeffdisher.october.registries;

import java.util.function.Function;
import java.util.function.Supplier;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.CraftingAspectCodec;
import com.jeffdisher.october.data.FueledAspectCodec;
import com.jeffdisher.october.data.IAspectCodec;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.InventoryAspectCodec;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.utils.Assert;


/**
 * Just a container of all the Aspects defined in the base system.
 * The main reason for this, aside from documenting what they are, is to introduce a canonical index assignment.
 * In the future, this may be made into something more dynamic but there is no benefit to that, at this time.
 */
public class AspectRegistry
{
	/**
	 * Block types - "air", "stone", etc.
	 */
	public static final Aspect<Short, OctreeShort> BLOCK = registerAspect(Short.class
			, OctreeShort.class
			, () -> OctreeShort.empty()
			, (OctreeShort original) -> {
				return original.cloneData();
			}
			// IAspectCodec only exists for OctreeObject types.
			, null
	);
	/**
	 * Inventory objects, usually null.
	 */
	public static final Aspect<Inventory, OctreeObject> INVENTORY = registerAspect(Inventory.class
			, OctreeObject.class
			, () -> OctreeObject.create()
			, (OctreeObject original) -> {
				// Inventories are now immutable so just make a clone of the map.
				return original.cloneMapShallow();
			}
			, new InventoryAspectCodec()
	);
	/**
	 * Block "damage value".  This is usually 0 ("not damaged") but can be as high as 32000, used to handle things like
	 * incremental block breaking, etc.
	 * In theory, this could probably be made into something non-persistent if we wanted to save storage space, but the
	 * benefit would probably be negligible.
	 */
	public static final Aspect<Short, OctreeShort> DAMAGE = registerAspect(Short.class
			, OctreeShort.class
			, () -> OctreeShort.empty()
			, (OctreeShort original) -> {
				return original.cloneData();
			}
			// IAspectCodec only exists for OctreeObject types.
			, null
	);
	/**
	 * CraftOperation objects, usually null.  Note that these must be combined with Inventory aspect in order to craft.
	 */
	public static final Aspect<CraftOperation, OctreeObject> CRAFTING = registerAspect(CraftOperation.class
			, OctreeObject.class
			, () -> OctreeObject.create()
			, (OctreeObject original) -> {
				// These are immutable so create the shallow clone.
				return original.cloneMapShallow();
			}
			, new CraftingAspectCodec()
	);
	/**
	 * FuelState objects, usually null.  These are used by things like furnaces to perform fuel-based crafting
	 * operations.
	 */
	public static final Aspect<FuelState, OctreeObject> FUELED = registerAspect(FuelState.class
			, OctreeObject.class
			, () -> OctreeObject.create()
			, (OctreeObject original) -> {
				// These are immutable so create the shallow clone.
				return original.cloneMapShallow();
			}
			, new FueledAspectCodec()
	);

	private static int _nextIndex = 0;
	public static final Aspect<?,?>[] ALL_ASPECTS;
	static {
		// Just verify indices are assigned as expected.
		Assert.assertTrue(0 == BLOCK.index());
		Assert.assertTrue(1 == INVENTORY.index());
		Assert.assertTrue(2 == DAMAGE.index());
		Assert.assertTrue(3 == CRAFTING.index());
		Assert.assertTrue(4 == FUELED.index());
		
		// Create the finished array, in-order.
		ALL_ASPECTS = new Aspect<?,?>[] {
			BLOCK,
			INVENTORY,
			DAMAGE,
			CRAFTING,
			FUELED,
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
