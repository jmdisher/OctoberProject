package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Assert;


/**
 * Used to describe a specific crafting operation, including how it is classified, inputs, output, and time cost.
 * Note that we currently assume that all inputs to crafting recipes are stackable but the outputs need not be.
 */
public class Craft
{
	public final short number;
	public final String name;
	public final String classification;
	public final Items[] input;
	public final Item[] output;
	public final long millisPerCraft;

	public Craft(short number
			, String name
			, String classification
			, Items[] input
			, Item[] output
			, long millisPerCraft
	)
	{
		Assert.assertTrue(number >= 0);
		Assert.assertTrue(null != classification);
		
		this.number = number;
		this.name = name;
		this.classification = classification;
		this.input = input;
		this.output = output;
		this.millisPerCraft = millisPerCraft;
	}
}
