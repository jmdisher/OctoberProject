package com.jeffdisher.october.aspects;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Composite structures are those who have an emergent behaviour in a special cornerstone block when other specific
 * blocks are arranged around it.
 * These helpers are used apply that emergent behaviour and change corresponding block states or schedule follow-up
 * mutations in response.
 * Note that there are ACTIVE and PASSIVE composite structures:
 * -ACTIVE: These structures automatically check if they are valid and set an "ACTIVE" flag state within themselves.
 * -PASSIVE: These structures do nothing on their own and are only declared so that other helpers can ask about
 *  extension block locations for their own reasons.
 */
public class CompositeRegistry
{
	/**
	 * We will poll the composite cornerstone every 5 seconds to see if it should change its active state.
	 * Ideally, this would be replaced with an event-based solution but these may not be in the same cuboid so it would
	 * require some kind of "on load" event for other blocks in the composition which will complicate the system a lot
	 * for something which is otherwise quite low-cost, even if hack-ish with this 5-second delay.
	 */
	public static final long COMPOSITE_CHECK_FREQUENCY = 5_000L;
	public static final String VOID_STONE_ID = "op.portal_stone";
	public static final String VOID_LAMP_ID = "op.void_lamp";
	public static final String PORTAL_KEYSTONE_ID = "op.portal_keystone";
	public static final String ENCHANTING_TABLE_ID = "op.enchanting_table";
	public static final String PEDESTAL_ID = "op.pedestal";

	public static CompositeRegistry load(ItemRegistry items, BlockAspect blocks)
	{
		Block voidStone = blocks.fromItem(items.getItemById(VOID_STONE_ID));
		Block voidLamp = blocks.fromItem(items.getItemById(VOID_LAMP_ID));
		Block portalKeystone = blocks.fromItem(items.getItemById(PORTAL_KEYSTONE_ID));
		Block enchantingTable = blocks.fromItem(items.getItemById(ENCHANTING_TABLE_ID));
		Block pedestal = blocks.fromItem(items.getItemById(PEDESTAL_ID));
		
		Set<AbsoluteLocation> stoneSet = Set.of(
			new AbsoluteLocation(-1, 0, 0)
			, new AbsoluteLocation(-2, 0, 0)
			, new AbsoluteLocation(-2, 0, 1)
			, new AbsoluteLocation(-2, 0, 2)
			, new AbsoluteLocation(-2, 0, 3)
			, new AbsoluteLocation(-2, 0, 4)
			, new AbsoluteLocation(-1, 0, 4)
			, new AbsoluteLocation( 0, 0, 4)
			, new AbsoluteLocation( 1, 0, 4)
			, new AbsoluteLocation( 2, 0, 4)
			, new AbsoluteLocation( 2, 0, 3)
			, new AbsoluteLocation( 2, 0, 2)
			, new AbsoluteLocation( 2, 0, 1)
			, new AbsoluteLocation( 2, 0, 0)
			, new AbsoluteLocation( 1, 0, 0)
		);
		Map<AbsoluteLocation, Block> portalStoneMap = stoneSet.stream()
			.collect(Collectors.toMap((AbsoluteLocation key) -> key, (AbsoluteLocation value) -> voidStone))
		;
		Set<AbsoluteLocation> pedestalSet = Set.of(
			new AbsoluteLocation(-2, 0, 0)
			, new AbsoluteLocation(2, 0, 0)
			, new AbsoluteLocation(0, -2, 0)
			, new AbsoluteLocation(0, 2, 0)
		);
		Map<AbsoluteLocation, Block> pedestalMap = pedestalSet.stream()
			.collect(Collectors.toMap((AbsoluteLocation key) -> key, (AbsoluteLocation value) -> pedestal))
		;
		
		Map<Block, Map<AbsoluteLocation, Block>> activeComposites = Map.of(voidLamp, Map.of(new AbsoluteLocation(0, 0, -1), voidStone)
			, portalKeystone, portalStoneMap
		);
		Map<Block, Map<AbsoluteLocation, Block>> passiveComposites = Map.of(enchantingTable, pedestalMap
		);
		
		return new CompositeRegistry(activeComposites, passiveComposites);
	}


	private final Map<Block, Map<AbsoluteLocation, Block>> _activeComposites;
	private final Map<Block, Map<AbsoluteLocation, Block>> _passiveComposites;

	private CompositeRegistry(Map<Block, Map<AbsoluteLocation, Block>> activeComposites, Map<Block, Map<AbsoluteLocation, Block>> passiveComposites)
	{
		_activeComposites = activeComposites;
		_passiveComposites = passiveComposites;
	}

	/**
	 * Checks if the given block is a cornerstone type with an active nature.
	 * 
	 * @param block The block type to check.
	 * @return True if this is an active-type cornerstone.
	 */
	public boolean isActiveCornerstone(Block block)
	{
		return _isActiveCornerstone(block);
	}

	/**
	 * Checks if the given block is a cornerstone type with a passive nature.
	 * 
	 * @param block The block type to check.
	 * @return True if this is a passive-type cornerstone.
	 */
	public boolean isPassiveCornerstone(Block block)
	{
		return _passiveComposites.containsKey(block);
	}

	/**
	 * Called when a cornerstone block is placed or receives a periodic update event in order to check if any state
	 * needs to change.
	 * This call also re-requests the periodic update for this block.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param location The location of the cornerstone block.
	 * @param proxy The mutable proxy for the cornerstone block.
	 */
	public void processCornerstoneUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// Note that this only operates on active cornerstone types and we assume it was called for that reason.
		Assert.assertTrue(_isActiveCornerstone(proxy.getBlock()));
		
		boolean shouldBeActive = (null != _getExtensionsIfValid(env, context, location, proxy));
		byte flags = proxy.getFlags();
		boolean wasActive = FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE);
		if (shouldBeActive != wasActive)
		{
			byte newFlags = shouldBeActive
				? FlagsAspect.set(flags, FlagsAspect.FLAG_ACTIVE)
				: FlagsAspect.clear(flags, FlagsAspect.FLAG_ACTIVE)
			;
			proxy.setFlags(newFlags);
		}
		proxy.requestFutureMutation(COMPOSITE_CHECK_FREQUENCY);
	}

	/**
	 * Will return the list of extension block locations if they are the correct type for the composite root at the given
	 * location with proxy.  This is read-only and ignores checking if the cornerstone is active or not.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param location The location of the cornerstone block.
	 * @param proxy The mutable proxy for the cornerstone block.
	 * @return The list of extension locations, if they are valid for this composite, or null if invalid.
	 */
	public List<AbsoluteLocation> getExtensionsIfValid(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		return _getExtensionsIfValid(env, context, location, proxy);
	}


	private List<AbsoluteLocation> _getExtensionsIfValid(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		List<AbsoluteLocation> validExtensions = null;
		// TODO:  In the future, these hard-coded IDs and relative mappings need to be defined in a data file.
		Block type = proxy.getBlock();
		
		Map<AbsoluteLocation, Block> relativeExtensionMap = _activeComposites.get(type);
		if (null == relativeExtensionMap)
		{
			relativeExtensionMap = _passiveComposites.get(type);
		}
		
		if (null != relativeExtensionMap)
		{
			OrientationAspect.Direction orientation = proxy.getOrientation();
			if (_matchBlockTypes(context, orientation, location, relativeExtensionMap))
			{
				validExtensions = relativeExtensionMap.keySet().stream().map((AbsoluteLocation loc) -> location.getRelative(loc.x(), loc.y(), loc.z()) ).toList();
			}
		}
		else
		{
			// NOTE:  This can ONLY be called on a valid cornerstone so this would be a usage error.
			throw Assert.unreachable();
		}
		return validExtensions;
	}

	private boolean _matchBlockTypes(TickProcessingContext context, OrientationAspect.Direction orientation, AbsoluteLocation base, Map<AbsoluteLocation, Block> relatives)
	{
		boolean isValid = true;
		for (Map.Entry<AbsoluteLocation, Block> ent : relatives.entrySet())
		{
			AbsoluteLocation target = ent.getKey();
			
			// Note that we need to correct this for orientation.
			AbsoluteLocation rotated = orientation.rotateAboutZ(target);
			AbsoluteLocation relative = base.getRelative(rotated.x(), rotated.y(), rotated.z());
			BlockProxy targetProxy = context.previousBlockLookUp.apply(relative);
			if (null == targetProxy)
			{
				// Request that this is loaded since remove portals are sometimes checked.
				context.keepAliveSink.accept(target.getCuboidAddress());
				
				// We can't answer in the affirmative so fail out.
				isValid = false;
				break;
			}
			else if (ent.getValue() != targetProxy.getBlock())
			{
				// Not the correct block so fail out.
				isValid = false;
				break;
			}
		}
		return isValid;
	}

	private boolean _isActiveCornerstone(Block block)
	{
		return _activeComposites.containsKey(block);
	}
}
