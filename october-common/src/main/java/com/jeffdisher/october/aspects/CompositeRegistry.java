package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Item;
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
	public static final String FLAG_ACTIVE = "ACTIVE";
	public static final String FLAG_PASSIVE = "PASSIVE";

	public static CompositeRegistry load(ItemRegistry items
		, BlockAspect blocks
		, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		Map<Block, Map<AbsoluteLocation, Block>> activeComposites = new HashMap<>();
		Map<Block, Map<AbsoluteLocation, Block>> passiveComposites = new HashMap<>();
		
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			private Block _currentKeystone;
			private Map<Block, Map<AbsoluteLocation, Block>> _targetMap;
			private Map<AbsoluteLocation, Block> _extensions;
			
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListException
			{
				Block block = _mapToBlock(name);
				_currentKeystone = block;
				
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Missing ACTIVE/PASSIVE for: \"" + name + "\"");
				}
				if (FLAG_ACTIVE.equals(parameters[0]))
				{
					_targetMap = activeComposites;
				}
				else if (FLAG_PASSIVE.equals(parameters[0]))
				{
					_targetMap = passiveComposites;
				}
				else
				{
					throw new TabListReader.TabListException("Invalid flag for: \"" + name + "\" : \"" + parameters[0] + "\"");
				}
				_extensions = new HashMap<>();
			}
			@Override
			public void endRecord() throws TabListException
			{
				if (_targetMap.containsKey(_currentKeystone))
				{
					throw new TabListReader.TabListException("Duplicate key: \"" + _currentKeystone);
				}
				if (_extensions.isEmpty())
				{
					throw new TabListReader.TabListException("No extensions listed for: \"" + _currentKeystone);
				}
				_targetMap.put(_currentKeystone, Collections.unmodifiableMap(_extensions));
				_currentKeystone = null;
				_targetMap = null;
				_extensions = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListException
			{
				Block block = _mapToBlock(name);
				if (3 != parameters.length)
				{
					throw new TabListReader.TabListException("Sub-record missing x/y/z for: \"" + name + "\" under \"" + _currentKeystone + "\"");
				}
				AbsoluteLocation location;
				try
				{
					location = new AbsoluteLocation(Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]), Integer.parseInt(parameters[2]));
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException("Invalid x/y/z number for: \"" + name + "\" under \"" + _currentKeystone + "\"");
				}
				if (_extensions.containsKey(location))
				{
					throw new TabListReader.TabListException("Duplicate x/y/z location for: \"" + name + "\" under \"" + _currentKeystone + "\"");
				}
				_extensions.put(location, block);
			}
			private Block _mapToBlock(String name) throws TabListException
			{
				Item item = items.getItemById(name);
				if (null == item)
				{
					throw new TabListReader.TabListException("Not a valid item: \"" + name + "\"");
				}
				Block block = blocks.fromItem(item);
				if (null == block)
				{
					throw new TabListReader.TabListException("Not a block: \"" + name + "\"");
				}
				return block;
			}
		};
		TabListReader.readEntireFile(callbacks, stream);

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
			FacingDirection orientation = proxy.getOrientation();
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

	private boolean _matchBlockTypes(TickProcessingContext context, FacingDirection orientation, AbsoluteLocation base, Map<AbsoluteLocation, Block> relatives)
	{
		boolean isValid = true;
		for (Map.Entry<AbsoluteLocation, Block> ent : relatives.entrySet())
		{
			AbsoluteLocation target = ent.getKey();
			
			// Note that we need to correct this for orientation.
			AbsoluteLocation rotated = orientation.rotateAboutZ(target);
			AbsoluteLocation relative = base.getRelative(rotated.x(), rotated.y(), rotated.z());
			BlockProxy targetProxy = context.previousBlockLookUp.readBlock(relative);
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
