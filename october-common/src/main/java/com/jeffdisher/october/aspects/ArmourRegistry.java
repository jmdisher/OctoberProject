package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.SimpleTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Represents the subset of Item objects which can be worn as armour pieces.
 */
public class ArmourRegistry
{
	public static final String FIELD_REDUCTION = "reduction";

	/**
	 * Loads the armour locations from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist describing armour items.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static ArmourRegistry load(ItemRegistry items
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		IValueTransformer<Item> keyTransformer = new IValueTransformer.ItemTransformer(items);
		IValueTransformer<BodyPart> valueTransformer = new IValueTransformer<>() {
			@Override
			public BodyPart transform(String value) throws TabListException
			{
				BodyPart slot = BodyPart.valueOf(value.toUpperCase());
				if (null == slot)
				{
					throw new TabListReader.TabListException("Invalid armour slot: \"" + value + "\"");
				}
				return slot;
			}
		};
		IValueTransformer<Integer> reductionTransformer = new IValueTransformer.IntegerTransformer(FIELD_REDUCTION);
		SimpleTabListCallbacks<Item, BodyPart> callbacks = new SimpleTabListCallbacks<>(keyTransformer, valueTransformer);
		SimpleTabListCallbacks.SubRecordCapture<Item, Integer> reduction = callbacks.captureSubRecord(FIELD_REDUCTION, reductionTransformer, true);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new ArmourRegistry(callbacks.topLevel, reduction.recordData);
	}


	private final Map<Item, BodyPart> _armourSlots;
	private final Map<Item, Integer> _reductionValues;

	private ArmourRegistry(Map<Item, BodyPart> armourSlots
			, Map<Item, Integer> reductionValues
	)
	{
		_armourSlots = armourSlots;
		_reductionValues = reductionValues;
	}

	/**
	 * Looks up the armour slot for the given item.  Returns null if not armour.
	 * 
	 * @param item The item to look up.
	 * @return The slot where this can be worn or null, if not armour.
	 */
	public BodyPart getBodyPart(Item item)
	{
		return _armourSlots.get(item);
	}

	/**
	 * Finds the damage reduction for this armour type.  Note that this MUST be an armour type.
	 * 
	 * @param item The armour piece to look up.
	 * @return The reduction in damage afforded by this kind of armour.
	 */
	public int getDamageReduction(Item item)
	{
		Integer value = _reductionValues.get(item);
		Assert.assertTrue(null != value);
		return value.intValue();
	}
}
