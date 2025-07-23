package com.jeffdisher.october.aspects;

import java.util.function.Function;
import java.util.function.Supplier;

import com.jeffdisher.october.data.CraftingAspectCodec;
import com.jeffdisher.october.data.FuelledAspectCodec;
import com.jeffdisher.october.data.IObjectCodec;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.InventoryAspectCodec;
import com.jeffdisher.october.data.MultiBlockRootAspectCodec;
import com.jeffdisher.october.data.OctreeInflatedByte;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.AbsoluteLocation;
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
	public static final Aspect<Inventory, OctreeObject<Inventory>> INVENTORY = registerAspect(Inventory.class
			, OctreeObject.getDecoratedClass()
			, () -> OctreeObject.create()
			, (OctreeObject<Inventory> original) -> {
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
	public static final Aspect<CraftOperation, OctreeObject<CraftOperation>> CRAFTING = registerAspect(CraftOperation.class
			, OctreeObject.getDecoratedClass()
			, () -> OctreeObject.create()
			, (OctreeObject<CraftOperation> original) -> {
				// These are immutable so create the shallow clone.
				return original.cloneMapShallow();
			}
			, new CraftingAspectCodec()
	);
	/**
	 * FuelState objects, usually null.  These are used by things like furnaces to perform fuel-based crafting
	 * operations.
	 */
	public static final Aspect<FuelState, OctreeObject<FuelState>> FUELLED = registerAspect(FuelState.class
			, OctreeObject.getDecoratedClass()
			, () -> OctreeObject.create()
			, (OctreeObject<FuelState> original) -> {
				// These are immutable so create the shallow clone.
				return original.cloneMapShallow();
			}
			, new FuelledAspectCodec()
	);
	/**
	 * Block "light value".  This is usually 0 ("dark") but can be as high as 15.
	 */
	public static final Aspect<Byte, OctreeInflatedByte> LIGHT = registerAspect(Byte.class
			, OctreeInflatedByte.class
			, () -> OctreeInflatedByte.empty()
			, (OctreeInflatedByte original) -> {
				return original.cloneData();
			}
			// IAspectCodec only exists for OctreeObject types.
			, null
	);
	/**
	 * Block "logic value".  This is usually 0 ("off") but can be as high as 15.
	 * This is specifically for things like switches and logic wire.
	 */
	public static final Aspect<Byte, OctreeInflatedByte> LOGIC = registerAspect(Byte.class
			, OctreeInflatedByte.class
			, () -> OctreeInflatedByte.empty()
			, (OctreeInflatedByte original) -> {
				return original.cloneData();
			}
			// IAspectCodec only exists for OctreeObject types.
			, null
	);
	/**
	 * Block "special flags".  While most entries are "0", there are some special flags for things like "burning", etc.
	 */
	public static final Aspect<Byte, OctreeInflatedByte> FLAGS = registerAspect(Byte.class
			, OctreeInflatedByte.class
			, () -> OctreeInflatedByte.empty()
			, (OctreeInflatedByte original) -> {
				return original.cloneData();
			}
			// IAspectCodec only exists for OctreeObject types.
			, null
	);
	/**
	 * Block orientation is used by multi-block roots, in order to show how the extension blocks are arranged around the
	 * root, or for blocks which have directionality to them (hoppers, for example, when they output in a specific
	 * direction).
	 */
	public static final Aspect<Byte, OctreeInflatedByte> ORIENTATION = registerAspect(Byte.class
			, OctreeInflatedByte.class
			, () -> OctreeInflatedByte.empty()
			, (OctreeInflatedByte original) -> {
				return original.cloneData();
			}
			// IAspectCodec only exists for OctreeObject types.
			, null
	);
	/**
	 * The root block location reference for multi-block arrangements but null for all other block types.
	 */
	public static final Aspect<AbsoluteLocation, OctreeObject<AbsoluteLocation>> MULTI_BLOCK_ROOT = registerAspect(AbsoluteLocation.class
			, OctreeObject.getDecoratedClass()
			, () -> OctreeObject.create()
			, (OctreeObject<AbsoluteLocation> original) -> {
				// These are immutable so create the shallow clone.
				return original.cloneMapShallow();
			}
			, new MultiBlockRootAspectCodec()
	);

	private static int _nextIndex = 0;
	public static final Aspect<?,?>[] ALL_ASPECTS;
	static {
		// Just verify indices are assigned as expected.
		Assert.assertTrue(0 == BLOCK.index());
		Assert.assertTrue(1 == INVENTORY.index());
		Assert.assertTrue(2 == DAMAGE.index());
		Assert.assertTrue(3 == CRAFTING.index());
		Assert.assertTrue(4 == FUELLED.index());
		Assert.assertTrue(5 == LIGHT.index());
		Assert.assertTrue(6 == LOGIC.index());
		Assert.assertTrue(7 == FLAGS.index());
		Assert.assertTrue(8 == ORIENTATION.index());
		Assert.assertTrue(9 == MULTI_BLOCK_ROOT.index());
		
		// Create the finished array, in-order.
		ALL_ASPECTS = new Aspect<?,?>[] {
			BLOCK,
			INVENTORY,
			DAMAGE,
			CRAFTING,
			FUELLED,
			LIGHT,
			LOGIC,
			
			// These were added in version 5.
			FLAGS,
			ORIENTATION,
			MULTI_BLOCK_ROOT,
		};
	}


	public static <T, O extends IOctree<T>> Aspect<T, O> registerAspect(Class<T> type
			, Class<O> octreeType
			, Supplier<O> emptyTreeSupplier
			, Function<O, O> deepMutableClone
			, IObjectCodec<T> codec
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
