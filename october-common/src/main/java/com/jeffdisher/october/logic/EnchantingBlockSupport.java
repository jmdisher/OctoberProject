package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.EnchantmentRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.MutationBlockChargeEnchantment;
import com.jeffdisher.october.mutations.MutationBlockCleanEnchantment;
import com.jeffdisher.october.mutations.MutationBlockFetchSpecialForEnchantment;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EnchantingOperation;
import com.jeffdisher.october.types.Enchantment;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Infusion;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This class contains the helpers to manage the state machine of an enchanting block.  It manages all of the logic
 * required by the various mutations which pass input into the state machine and, in response, will advance charge time,
 * consume input items, produce output of enchantment/infusion when completed, and will also schedule the follow-up
 * mutations or spawn events, internally.
 * Note that these methods all assume the block is an enchanting table and is active.
 */
public class EnchantingBlockSupport
{
	public static void tryStartEnchantingOperation(Environment env
		, TickProcessingContext context
		, AbsoluteLocation blockLocation
		, IMutableBlockProxy proxy
	)
	{
		ItemSlot target = proxy.getSpecialSlot();
		List<Item> pedestals = _getPedestalItemTypes(env, context, blockLocation, proxy);
		
		// If there is already an enchantment, make sure that it is valid.
		EnchantingOperation operation = proxy.getEnchantingOperation();
		if ((null != operation) && ((null == target) || (null == pedestals) || !_isValidConfigurationForOperation(operation, target, pedestals)))
		{
			// We started an operation but the environment invalidated it.
			_discardOperation(env, context, blockLocation, operation);
			proxy.setEnchantingOperation(null);
			operation = null;
		}
		
		// If there is NOT an enchantment, see if there is a valid one which could start and then start it.
		if ((null == operation) && (null != target))
		{
			_tryStartNewOperation(env, context, blockLocation, proxy, target, pedestals);
		}
	}

	public static void chargeEnchantingOperation(Environment env
		, TickProcessingContext context
		, AbsoluteLocation blockLocation
		, IMutableBlockProxy proxy
		, long previousChargeMillis
	)
	{
		// Make sure that there is an enchantment operation, make sure that it is valid, make sure that it is charged to
		// "previousChargeMillis", then advance the charge by 1 tick.
		ItemSlot target = proxy.getSpecialSlot();
		List<Item> pedestals = _getPedestalItemTypes(env, context, blockLocation, proxy);
		EnchantingOperation operation = proxy.getEnchantingOperation();
		if ((null != operation) && ((null == target) || (null == pedestals) || !_isValidConfigurationForOperation(operation, target, pedestals)))
		{
			// We started an operation but the environment invalidated it.
			_discardOperation(env, context, blockLocation, operation);
			proxy.setEnchantingOperation(null);
			operation = null;
		}
		
		if (null != operation)
		{
			// This is still valid so see if the time matches and if we can advance.
			if (previousChargeMillis == operation.chargedMillis())
			{
				long millisRequired = (null != operation.enchantment())
					? operation.enchantment().millisToApply()
					: operation.infusion().millisToApply()
				;
				long newChargeMillis = Math.min(millisRequired, previousChargeMillis + context.millisPerTick);
				operation = new EnchantingOperation(newChargeMillis
					, operation.enchantment()
					, operation.infusion()
					, operation.consumedItems()
				);
				proxy.setEnchantingOperation(operation);
				
				// If there is still some charge remaining, schedule the next.  Otherwise, request the input from the other
				// pedestals, and the follow-up mutation to clean up any failures.
				if (newChargeMillis < millisRequired)
				{
					// Keep charging.
					MutationBlockChargeEnchantment charge = new MutationBlockChargeEnchantment(blockLocation, operation.chargedMillis());
					context.mutationSink.next(charge);
				}
				else
				{
					// Charging done so request the items from pedestals.
					List<AbsoluteLocation> pedestalLocations = env.composites.getExtensionsIfValid(env, context, blockLocation, proxy);
					if (null != pedestalLocations)
					{
						for (AbsoluteLocation loc : pedestalLocations)
						{
							MutationBlockFetchSpecialForEnchantment fetch = new MutationBlockFetchSpecialForEnchantment(loc, blockLocation);
							context.mutationSink.next(fetch);
						}
					}
					
					// We also take this opportunity to schedule the clean-up.
					// Note that this needs to arrive 3 ticks from now since tick 1 will be the fetch and tick 2 will be the receive so tick 3 will be where this should already be done.
					MutationBlockCleanEnchantment clean = new MutationBlockCleanEnchantment(blockLocation);
					context.mutationSink.future(clean, 3L * context.millisPerTick);
				}
			}
		}
	}

	public static void receiveConsumedInputItem(Environment env
		, TickProcessingContext context
		, AbsoluteLocation blockLocation
		, IMutableBlockProxy proxy
		, ItemSlot input
	)
	{
		// Make sure that there is an enchantment operation, make sure that it is valid, make sure that it is fully
		// charged, and make sure that this input is valid, then add it to the operation.
		// (If this slot became invalid, we will clean this up in the post-pass)
		ItemSlot target = proxy.getSpecialSlot();
		EnchantingOperation operation = proxy.getEnchantingOperation();
		boolean shouldDrop = false;
		if ((null != operation)
			&& (null != target)
			&& (operation.getRequiredCentralType() == target.getType())
			&& (operation.chargedMillis() == operation.getRequiredChargeMillis())
		)
		{
			// This is fully charged so make sure that this input is something required after accounting for what we have already consumed.
			int existingSize = operation.consumedItems().size();
			int existingIndex = 0;
			int insertPoint = -1;
			for (Item required : operation.getRequiredItems())
			{
				if ((existingIndex < existingSize) && (required == operation.consumedItems().get(existingIndex).getType()))
				{
					existingIndex += 1;
				}
				else if (required == input.getType())
				{
					insertPoint = existingIndex;
					break;
				}
			}
			if (insertPoint >= 0)
			{
				// We found somewhere to add this (in the correct sorted order, too).
				List<ItemSlot> consumed = new ArrayList<>(operation.consumedItems());
				consumed.add(insertPoint, input);
				operation = new EnchantingOperation(operation.chargedMillis()
					, operation.enchantment()
					, operation.infusion()
					, Collections.unmodifiableList(consumed)
				);
				
				// If this satisfies all external consumed block requirements and this block's special slot contains the target
				// block, then complete the operation (spawning the result as a passive above it), and clear it.
				if (operation.getRequiredItems().size() == consumed.size())
				{
					// See if we have the correct item in the table and either take it to complete the operation or discard the operation.
					if (null != operation.enchantment())
					{
						PropertyType<Byte> toApply = operation.enchantment().enchantmentToApply();
						if (EnchantmentRegistry.canApplyToTarget(target.nonStackable, toApply))
						{
							// Apply the enchantment and clear it.
							NonStackableItem enchanted = _applyEnchantment(target.nonStackable, toApply);
							context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT
								, blockLocation.getRelative(0, 0, 1).toEntityLocation()
								, new EntityLocation(0.0f, 0.0f, 0.0f)
								, ItemSlot.fromNonStack(enchanted)
							);
							proxy.setEnchantingOperation(null);
							proxy.setSpecialSlot(null);
							
							// Report the event (in block, so no entity ID).
							context.eventSink.post(new EventRecord(EventRecord.Type.ENCHANT_COMPLETE
								, EventRecord.Cause.NONE
								, blockLocation
								, 0
								, 0
							));
						}
						else
						{
							// If we can't apply this, just handle clean-up in the common post-pass case.
						}
					}
					else
					{
						Item result = operation.infusion().outputItem();
						
						// Note that the result we are infusing may have changed to be non-stackable.
						ItemSlot slot;
						if (env.durability.isStackable(result))
						{
							slot = ItemSlot.fromStack(new Items(result, 1));
						}
						else
						{
							slot = ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(env, result));
						}
						context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT
							, blockLocation.getRelative(0, 0, 1).toEntityLocation()
							, new EntityLocation(0.0f, 0.0f, 0.0f)
							, slot
						);
						
						// Infusions can act on stackable central items so this might not be empty.
						if (target.getCount() > 1)
						{
							ItemSlot updatedSlot = ItemSlot.fromStack(new Items(target.getType(), target.getCount() - 1));
							proxy.setSpecialSlot(updatedSlot);
							proxy.setEnchantingOperation(null);
							
							// See if this could still be a valid infusion (this will update enchanting operation if one starts).
							List<Item> pedestals = _getPedestalItemTypes(env, context, blockLocation, proxy);
							_tryStartNewOperation(env, context, blockLocation, proxy, target, pedestals);
						}
						else
						{
							proxy.setSpecialSlot(null);
							proxy.setEnchantingOperation(null);
						}
					}
				}
				else
				{
					// Not ready yet so write-back.
					proxy.setEnchantingOperation(operation);
				}
			}
			else
			{
				// We received something incorrect so just drop it.
				shouldDrop = true;
			}
		}
		else
		{
			// Not existing or ready so drop it.
			shouldDrop = true;
		}
		
		if (shouldDrop)
		{
			context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT
				, blockLocation.getRelative(0, 0, 1).toEntityLocation()
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, input
			);
		}
	}

	public static void cleanUpOrphanedOperations(Environment env
		, TickProcessingContext context
		, AbsoluteLocation blockLocation
		, IMutableBlockProxy proxy
	)
	{
		// If there is a fully-charged operation in the block, discard it and clear it.
		EnchantingOperation operation = proxy.getEnchantingOperation();
		if ((null != operation)
			&& (operation.chargedMillis() == operation.getRequiredChargeMillis())
		)
		{
			_discardOperation(env, context, blockLocation, operation);
			proxy.setEnchantingOperation(null);
		}
	}


	private static List<Item> _getPedestalItemTypes(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// We need to look up the 4 pedestals around the enchanting table and see what is in their special slots.
		List<AbsoluteLocation> pedestalLocations = env.composites.getExtensionsIfValid(env, context, location, proxy);
		List<Item> result;
		if (null != pedestalLocations)
		{
			// Fetch these and sort them.
			List<Item> unsorted = pedestalLocations.stream().map((AbsoluteLocation one) -> {
				// We know that we can load the proxies since CompositeHelpers decided these are valid.
				ItemSlot slot = context.previousBlockLookUp.apply(one).getSpecialSlot();
				Item type = null;
				if (null != slot)
				{
					type = slot.getType();
				}
				return type;
			}).filter((Item found) -> (null != found)).toList();
			result = EnchantmentRegistry.getCanonicallySortedList(unsorted);
		}
		else
		{
			// This is invalid so we return null.
			result = null;
		}
		return result;
	}

	private static boolean _isValidConfigurationForOperation(EnchantingOperation operation, ItemSlot target, List<Item> pedestals)
	{
		// Note that we can assume target is non-null here.
		boolean isValid;
		if (null != operation.enchantment())
		{
			Enchantment enchantment = operation.enchantment();
			isValid = (enchantment.targetItem() == target.getType()) && enchantment.consumedItems().equals(pedestals);
		}
		else
		{
			Infusion infusion = operation.infusion();
			isValid = (infusion.centralItem() == target.getType()) && infusion.consumedItems().equals(pedestals);
		}
		return isValid;
	}

	private static void _discardOperation(Environment env
		, TickProcessingContext context
		, AbsoluteLocation blockLocation
		, EnchantingOperation operation
	)
	{
		// In this case, we just spawn everything inside this.
		for (ItemSlot slot : operation.consumedItems())
		{
			context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT
				, blockLocation.getRelative(0, 0, 1).toEntityLocation()
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, slot
			);
		}
	}

	private static NonStackableItem _applyEnchantment(NonStackableItem nonStackable, PropertyType<Byte> toApply)
	{
		Map<PropertyType<?>, Object> props = new HashMap<>(nonStackable.properties());
		byte value = PropertyHelpers.getBytePropertyValue(props, toApply);
		
		// We already verified this.
		Assert.assertTrue(value < (byte)127);
		value += 1;
		
		props.put(toApply, value);
		return new NonStackableItem(nonStackable.type(), Collections.unmodifiableMap(props));
	}

	private static void _tryStartNewOperation(Environment env
		, TickProcessingContext context
		, AbsoluteLocation blockLocation
		, IMutableBlockProxy proxy
		, ItemSlot target
		, List<Item> pedestals
	)
	{
		Block table = proxy.getBlock();
		NonStackableItem targetNonStackable = target.nonStackable;
		Enchantment enchantment = (null != targetNonStackable)
			? env.enchantments.getEnchantment(table, targetNonStackable, pedestals)
			: null
		;
		EnchantingOperation newOperation = null;
		if (null != enchantment)
		{
			newOperation = new EnchantingOperation(context.millisPerTick
				, enchantment
				, null
				, List.of()
			);
		}
		else
		{
			Infusion infusion = env.enchantments.getInfusion(table, target.getType(), pedestals);
			if (null != infusion)
			{
				newOperation = new EnchantingOperation(context.millisPerTick
					, null
					, infusion
					, List.of()
				);
			}
		}
		
		// If we created a new operation, store it and request charging.
		if (null != newOperation)
		{
			proxy.setEnchantingOperation(newOperation);
			MutationBlockChargeEnchantment charge = new MutationBlockChargeEnchantment(blockLocation, newOperation.chargedMillis());
			context.mutationSink.next(charge);
		}
	}
}
