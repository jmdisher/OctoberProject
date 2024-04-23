package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;


/**
 * A list of special references into the Environment data which are required for core mechanics.
 * In the future, this list will likely shrink as more of these special constants become generalized into data
 * descriptions.
 */
public class SpecialConstants
{
	/**
	 * Air is a default "empty" block in many areas so it is defined here.
	 */
	public final Block AIR;
	/**
	 * Water-related blocks have special update mechanics so they are defined here.
	 */
	public final Block WATER_SOURCE;
	public final Block WATER_STRONG;
	public final Block WATER_WEAK;

	public SpecialConstants(ItemRegistry items, BlockAspect blocks)
	{
		this.AIR = blocks.fromItem(items.getItemById("op.air"));
		this.WATER_SOURCE = blocks.fromItem(items.getItemById("op.water_source"));
		this.WATER_STRONG = blocks.fromItem(items.getItemById("op.water_strong"));
		this.WATER_WEAK = blocks.fromItem(items.getItemById("op.water_weak"));
	}
}
