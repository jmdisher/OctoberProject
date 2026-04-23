package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Assert;


/**
 * Used to describe a specific crafting operation, including how it is classified, inputs, output, and time cost.
 * Note that we currently assume that all inputs to crafting recipes are stackable but the outputs need not be.
 */
public class Craft
{
	public final String name;
	public final String classification;
	public final Items[] input;
	public final Item[] output;
	public final long millisPerCraft;

	public Craft(String name
		, String classification
		, Items[] input
		, Item[] output
		, long millisPerCraft
	)
	{
		// The name will be a small string (7-bit size) so make sure it is in range.
		Assert.assertTrue(name.length() > 0);
		Assert.assertTrue(name.length() <= Byte.MAX_VALUE);
		// Classification must exist.
		Assert.assertTrue(null != classification);
		// The input cannot be empty.
		Assert.assertTrue(input.length > 0);
		// The output cannot be empty.
		Assert.assertTrue(output.length > 0);
		// Crafting time must be a positive value.
		Assert.assertTrue(millisPerCraft > 0L);
		
		this.name = name;
		this.classification = classification;
		this.input = input;
		this.output = output;
		this.millisPerCraft = millisPerCraft;
	}
}
