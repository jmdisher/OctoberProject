package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * A list of special references into the Environment data which are required for core mechanics.
 * TODO:  Move this into a declarative data file.
 */
public class SpecialConstants
{
	/**
	 * Required to generate item when a projectile hits a surface.
	 * This could be removed if the item type were included in the projectile data, somewhere.
	 */
	public final Item itemArrow;
	/**
	 * Has special use in portal logic.
	 */
	public final Item itemPortalOrb;
	/**
	 * Special "use on block" case.
	 */
	public final Item itemFertilizer;
	/**
	 * Special "use on block" case.
	 */
	public final Item itemStoneHoe;

	/**
	 * Air is a default "empty" block in many areas so it is defined here.
	 */
	public final Block AIR;
	/**
	 * Hopper mechanics are currently special.
	 */
	public final Block blockHopper;
	/**
	 * Used in plant growth logic.
	 */
	public final Block blockLog;
	/**
	 * Used in plant growth logic.
	 */
	public final Block blockLeaf;
	/**
	 * Has special use in portal logic.
	 */
	public final Block blockPortalKeystone;
	/**
	 * Has special use in portal logic.
	 */
	public final Block blockPortalSurface;
	/**
	 * This is a special one-off utility.
	 */
	public final Block blockCuboidLoader;
	/**
	 * Has special behaviour with setting spawn and changing time.
	 */
	public final Block blockBed;
	/**
	 * Special "use on block" case.
	 */
	public final Block blockDirt;
	/**
	 * Special "use on block" case.
	 */
	public final Block blockGrass;
	/**
	 * Special "use on block" case.
	 */
	public final Block blockTilledSoil;

	public SpecialConstants(ItemRegistry items, BlockAspect blocks)
	{
		this.itemArrow = items.getItemById("op.arrow");
		this.itemPortalOrb = items.getItemById("op.portal_orb");
		this.itemFertilizer = items.getItemById("op.fertilizer");
		this.itemStoneHoe = items.getItemById("op.stone_hoe");
		
		this.AIR = blocks.fromItem(items.getItemById("op.air"));
		this.blockHopper = blocks.fromItem(items.getItemById("op.hopper"));
		this.blockLog = blocks.fromItem(items.getItemById("op.log"));
		this.blockLeaf = blocks.fromItem(items.getItemById("op.leaf"));
		this.blockPortalKeystone = blocks.fromItem(items.getItemById("op.portal_keystone"));
		this.blockPortalSurface = blocks.fromItem(items.getItemById("op.portal_surface"));
		this.blockCuboidLoader = blocks.fromItem(items.getItemById("op.cuboid_loader"));
		this.blockBed = blocks.fromItem(items.getItemById("op.bed"));
		this.blockDirt = blocks.fromItem(items.getItemById("op.dirt"));
		this.blockGrass = blocks.fromItem(items.getItemById("op.grass"));
		this.blockTilledSoil = blocks.fromItem(items.getItemById("op.tilled_soil"));
	}
}
