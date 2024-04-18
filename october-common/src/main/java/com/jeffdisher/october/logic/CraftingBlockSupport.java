package com.jeffdisher.october.logic;

import java.util.Set;

import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.utils.Assert;


/**
 * The helper functions for running crafting operations within blocks.  The idea with these is to limit the amount of
 * special knowledge hard-coded into the mutations or associated to specific blocks.
 */
public class CraftingBlockSupport
{
	/**
	 * Runs a manual crafting operation in the given newBlock, potentially starting startCraft if non-null.
	 * NOTE:  This assumes that this block supports manual crafting.
	 * 
	 * @param env The environment.
	 * @param newBlock The block.
	 * @param startCraft The crafting operation to start, null to just continue what is happening.
	 * @param millisToApply The milliseconds of crafting work to apply.
	 * @return True if some crafting work was done.
	 */
	public static boolean runManual(Environment env, IMutableBlockProxy newBlock, Craft startCraft, long millisToApply)
	{
		boolean didApply;
		Block craftingBlock = newBlock.getBlock();
		long craftingMultiplier = env.crafting.craftingSpeedMultiplier(craftingBlock);
		Set<Craft.Classification> classifications = env.crafting.craftingClassifications(craftingBlock);
		// See if this is something new or if we are continuing.
		CraftOperation currentOperation = newBlock.getCrafting();
		Craft currentCraft = (null != currentOperation) ? currentOperation.selectedCraft() : null;
		if ((null == startCraft) || (startCraft == currentCraft))
		{
			// We are continuing but we may have already finished.
			if (null != currentOperation)
			{
				long effectiveMillis = craftingMultiplier * millisToApply;
				long completedMillis = currentOperation.completedMillis() + effectiveMillis;
				if (completedMillis >= currentCraft.millisPerCraft)
				{
					// We are done so try to apply the craft.
					Inventory inventory = newBlock.getInventory();
					if (classifications.contains(currentOperation.selectedCraft().classification) && CraftAspect.canApply(currentCraft, inventory))
					{
						MutableInventory mutable = new MutableInventory(inventory);
						CraftAspect.craft(currentCraft, mutable);
						newBlock.setInventory(mutable.freeze());
						newBlock.setCrafting(null);
					}
				}
				else
				{
					// Just save this back.
					CraftOperation updated = new CraftOperation(currentCraft, completedMillis);
					newBlock.setCrafting(updated);
				}
				// We changed something so say we applied.
				didApply = true;
			}
			else
			{
				// There is no operation so do nothing.
				didApply = false;
			}
		}
		else
		{
			// We are changing so see if the craft makes sense and then start it.
			// Make sure that this crafting operation can be done within a table.
			Inventory inventory = newBlock.getInventory();
			if (classifications.contains(startCraft.classification) && CraftAspect.canApply(startCraft, inventory))
			{
				long effectiveMillis = craftingMultiplier * millisToApply;
				CraftOperation updated = new CraftOperation(startCraft, effectiveMillis);
				newBlock.setCrafting(updated);
				// We changed something so say we applied.
				didApply = true;
			}
			else
			{
				// We couldn't start this operation.
				didApply = false;
			}
		}
		return didApply;
	}

	/**
	 * Runs a fueled craft within the given block.
	 * NOTE:  This assumes that this block supports fueled crafting.
	 * 
	 * @param env The environment.
	 * @param newBlock The block.
	 * @param millisToApply The milliseconds of crafting work to apply.
	 * @return The result tuple, describing whether some work was done and whether the caller should schedule another
	 * crafting operation against this block.
	 */
	public static FueledResult runFueled(Environment env, IMutableBlockProxy newBlock, long millisToApply)
	{
		boolean didApply;
		boolean shouldReschedule;
		// First, check the ephemeral object to make sure we didn't already do this (since this is initially scheduled via interaction but can only happen once per tick).
		if (null == newBlock.getEphemeralState())
		{
			CraftOperation craft = _getValidFueledCraft(env, newBlock);
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
					Item fuelType = inv.items.values().iterator().next().type();
					fuelAvailable = env.fuel.millisOfFuel(fuelType);
					fuelInventory.removeItems(fuelType, 1);
					fuel = new FuelState(fuelAvailable, fuelType, fuelInventory.freeze());
				}
				
				long millisRequired = craft.selectedCraft().millisPerCraft - craft.completedMillis();
				Assert.assertTrue(millisRequired >= 0L);
				int fuelToApply = Math.min((int)millisToApply, Math.min((int)millisRequired, fuelAvailable));
				fuelAvailable -= fuelToApply;
				Item currentFuel = (fuelAvailable > 0) ? fuel.currentFuel() : null;
				fuel = new FuelState(fuelAvailable, currentFuel, fuel.fuelInventory());
				craft = new CraftOperation(craft.selectedCraft(), craft.completedMillis() + (long)fuelToApply);
				if (craft.isCompleted())
				{
					// Complete the crafting operation.
					MutableInventory inv = new MutableInventory(newBlock.getInventory());
					CraftAspect.craft(craft.selectedCraft(), inv);
					newBlock.setInventory(inv.freeze());
					craft = null;
				}
				newBlock.setCrafting(craft);
				newBlock.setFuel(fuel);
				didApply = true;
				
				// After we have done everything and saving state, see if we have more crafting work to do or fuel to drain.
				if ((fuelAvailable > 0) || (null != _getValidFueledCraft(env, newBlock)))
				{
					shouldReschedule = true;
				}
				else
				{
					// We are done so stop scheduling.
					shouldReschedule = false;
				}
			}
			else
			{
				// Even if not actively crafting, we still want to burn through any existing fuel.
				FuelState fuel = newBlock.getFuel();
				// Nothing should schedule this if there is nothing to craft and nothing to burn (implies a bug elsewhere).
				Assert.assertTrue((null != fuel) && (fuel.millisFueled() > 0));
				int fuelRemaining = fuel.millisFueled() - (int)millisToApply;
				if (fuelRemaining < 0)
				{
					fuelRemaining = 0;
				}
				if (fuelRemaining > 0)
				{
					shouldReschedule = true;
				}
				else
				{
					// We are done so stop scheduling.
					shouldReschedule = false;
				}
				Item currentFuel = (fuelRemaining > 0) ? fuel.currentFuel() : null;
				newBlock.setFuel(new FuelState(fuelRemaining, currentFuel, fuel.fuelInventory()));
				didApply = true;
			}
			
			// Set the ephemeral state since we are done for this tick (we don't currently care what the object is).
			newBlock.setEphemeralState(Boolean.TRUE);
		}
		else
		{
			// We already did this for this tick.
			didApply = false;
			shouldReschedule = false;
		}
		return new FueledResult(didApply, shouldReschedule);
	}

	/**
	 * Gets the current or possible fueled crafting operation within the block under proxy.  Returns null if no
	 * operation is possible.
	 * 
	 * @param env The environment.
	 * @param proxy The block.
	 * @return The crafting operation which is either currently active or could be started.
	 */
	public static CraftOperation getValidFueledCraft(Environment env, IBlockProxy proxy)
	{
		return _getValidFueledCraft(env, proxy);
	}


	private static CraftOperation _getValidFueledCraft(Environment env, IBlockProxy proxy)
	{
		CraftOperation canCraft = null;
		Block craftingBlock = proxy.getBlock();
		// Note that we have no way to generalize this helper.
		if (env.crafting.craftingClassifications(craftingBlock).contains(Craft.Classification.SPECIAL_FURNACE))
		{
			// We know that a block with this classification must have crafting, inventory, and fueled aspects.
			CraftOperation runningCraft = proxy.getCrafting();
			
			// See if there is a possible crafting operation.
			Craft possibleCraft = null;
			if (null == runningCraft)
			{
				Inventory inv = proxy.getInventory();
				for (Craft craft : env.crafting.craftsForClassifications(Set.of(Craft.Classification.SPECIAL_FURNACE)))
				{
					// Just take the first match (we don't currently have a priority).
					if (CraftAspect.canApply(craft, inv))
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


	public static record FueledResult(boolean didApply, boolean shouldReschedule) {}
}
