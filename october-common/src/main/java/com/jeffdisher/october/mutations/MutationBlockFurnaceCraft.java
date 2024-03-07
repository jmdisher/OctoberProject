package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.Set;

import com.jeffdisher.october.aspects.FuelAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Called by MutationBlockStoreItems or itself in order to advance the crafting progress of a furnace.
 * Note that this relies on the ephemeral object for the block so that only one of these applied and potentially
 * rescheduled per tick.
 */
public class MutationBlockFurnaceCraft implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.CRAFT_IN_FURNACE;

	public static MutationBlockFurnaceCraft deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockFurnaceCraft(location);
	}

	public static CraftOperation canCraft(IMutableBlockProxy proxy)
	{
		CraftOperation canCraft = null;
		// Check that this block is a furnace (could have been changed).
		if (ItemRegistry.FURNACE == proxy.getBlock().asItem())
		{
			// See if there is an active crafting operation.
			CraftOperation runningCraft = proxy.getCrafting();
			
			// See if there is a possible crafting operation.
			Craft possibleCraft = null;
			if (null == runningCraft)
			{
				Inventory inv = proxy.getInventory();
				for (Craft craft : Craft.craftsForClassifications(Set.of(Craft.Classification.SPECIAL_FURNACE)))
				{
					// Just take the first match (we don't currently have a priority).
					if (craft.canApply(inv))
					{
						possibleCraft = craft;
						break;
					}
				}
			}
			
			// See if we have fuel.
			FuelState state = proxy.getFuel();
			boolean hasFuel = (state.millisFueled() > 0) || (state.fuelInventory().currentEncumbrance > 0);
			
			if (((null != runningCraft) || (null != possibleCraft)) && hasFuel)
			{
				canCraft = (null != runningCraft)
						? runningCraft
						: new CraftOperation(possibleCraft, 0)
				;
			}
		}
		return canCraft;
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockFurnaceCraft(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// First, check the ephemeral object to make sure we didn't already do this.
		boolean didApply = false;
		if (null == newBlock.getEphemeralState())
		{
			// TODO:  Stop using this constant once we pass this tick time through the context.
			long craftMillisRemaining = 100L;
			CraftOperation craft = canCraft(newBlock);
			boolean shouldReschedule = false;
			if (null != craft)
			{
				// Advance the craft and store it and the fuel back.
				FuelState fuel = newBlock.getFuel();
				int fuelAvailable = fuel.millisFueled();
				if (0 == fuelAvailable)
				{
					// Consume fuel - we will just get the first one as there is no sort rule, here.
					Inventory inv = fuel.fuelInventory();
					MutableInventory fuelInventory = new MutableInventory(inv);
					Item fuelType = inv.items.keySet().iterator().next();
					fuelAvailable = FuelAspect.millisOfFuel(fuelType);
					fuelInventory.removeItems(fuelType, 1);
					fuel = new FuelState(fuelAvailable, fuelInventory.freeze());
				}
				
				long millisRequired = craft.selectedCraft().millisPerCraft - craft.completedMillis();
				Assert.assertTrue(millisRequired >= 0L);
				int fuelToApply = Math.min((int)craftMillisRemaining, Math.min((int)millisRequired, fuelAvailable));
				fuelAvailable -= fuelToApply;
				fuel = new FuelState(fuelAvailable, fuel.fuelInventory());
				craft = new CraftOperation(craft.selectedCraft(), craft.completedMillis() + (long)fuelToApply);
				if (craft.isCompleted())
				{
					// Complete the crafting operation.
					MutableInventory inv = new MutableInventory(newBlock.getInventory());
					craft.selectedCraft().craft(inv);
					newBlock.setInventory(inv.freeze());
					craft = null;
				}
				newBlock.setCrafting(craft);
				newBlock.setFuel(fuel);
				didApply = true;
				
				// After we have done everything and saving state, see if we have more crafting work to do or fuel to drain.
				if ((fuelAvailable > 0) || (null != canCraft(newBlock)))
				{
					shouldReschedule = true;
				}
			}
			else
			{
				// Even if not actively crafting, we still want to burn through any existing fuel.
				FuelState fuel = newBlock.getFuel();
				// Nothing should schedule this if there is nothing to craft and nothing to burn (implies a bug elsewhere).
				Assert.assertTrue((null != fuel) && (fuel.millisFueled() > 0));
				int fuelRemaining = fuel.millisFueled() - (int)craftMillisRemaining;
				if (fuelRemaining < 0)
				{
					fuelRemaining = 0;
				}
				if (fuelRemaining > 0)
				{
					shouldReschedule = true;
				}
				newBlock.setFuel(new FuelState(fuelRemaining, fuel.fuelInventory()));
				didApply = true;
			}
			
			if (shouldReschedule)
			{
				context.newMutationSink.accept(new MutationBlockFurnaceCraft(_blockLocation));
			}
			// Set the ephemeral state since we are done for this tick (we don't currently care what the object is).
			newBlock.setEphemeralState(Boolean.TRUE);
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
	}
}
