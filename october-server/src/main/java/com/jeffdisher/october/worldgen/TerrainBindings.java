package com.jeffdisher.october.worldgen;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Block;


/**
 * The bindings for general terrain blocks used in world generation.
 * TODO:  Move this into declarative data.
 */
public class TerrainBindings
{
	public final Block airBlock;
	public final Block stoneBlock;
	public final Block dirtBlock;
	public final Block grassBlock;
	public final Block tilledSoilBlock;
	public final Block sandBlock;
	public final Block basaltBlock;
	public final Block coalOreBlock;
	public final Block ironOreBlock;

	public final Block waterSourceBlock;
	public final Block lavaSourceBlock;

	public final Block wheatMatureBlock;
	public final Block carrotMatureBlock;
	public final Block logBlock;

	public TerrainBindings(Environment env)
	{
		this.airBlock = env.blocks.fromItem(env.items.getItemById("op.air"));
		this.stoneBlock = env.blocks.fromItem(env.items.getItemById("op.stone"));
		this.dirtBlock = env.blocks.fromItem(env.items.getItemById("op.dirt"));
		this.grassBlock = env.blocks.fromItem(env.items.getItemById("op.grass"));
		this.tilledSoilBlock = env.blocks.fromItem(env.items.getItemById("op.tilled_soil"));
		this.sandBlock = env.blocks.fromItem(env.items.getItemById("op.sand"));
		this.basaltBlock = env.blocks.fromItem(env.items.getItemById("op.basalt"));
		this.coalOreBlock = env.blocks.fromItem(env.items.getItemById("op.coal_ore"));
		this.ironOreBlock = env.blocks.fromItem(env.items.getItemById("op.iron_ore"));
		
		this.waterSourceBlock = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		this.lavaSourceBlock = env.blocks.fromItem(env.items.getItemById("op.lava_source"));
		
		this.wheatMatureBlock = env.blocks.fromItem(env.items.getItemById("op.wheat_mature"));
		this.carrotMatureBlock = env.blocks.fromItem(env.items.getItemById("op.carrot_mature"));
		this.logBlock = env.blocks.fromItem(env.items.getItemById("op.log"));
	}
}
