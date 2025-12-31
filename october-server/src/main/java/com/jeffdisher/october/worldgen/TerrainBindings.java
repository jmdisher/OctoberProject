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
	public final Block stoneBrickBlock;
	public final Block dirtBlock;
	public final Block grassBlock;
	public final Block tilledSoilBlock;
	public final Block torchBlock;
	public final Block saplingBlock;
	public final Block doorBlock;
	public final Block portalStoneBlock;
	public final Block portalKeystoneBlock;
	public final Block pedestalBlock;
	public final Block sandBlock;
	public final Block basaltBlock;
	public final Block lanternBlock;
	public final Block coalOreBlock;
	public final Block ironOreBlock;
	public final Block copperOreBlock;
	public final Block diamondOreBlock;

	public final Block waterSourceBlock;
	public final Block lavaSourceBlock;

	public final Block wheatSeedlingBlock;
	public final Block carrotSeedlingBlock;
	public final Block wheatMatureBlock;
	public final Block carrotMatureBlock;
	public final Block logBlock;
	public final Block leafBlock;

	public TerrainBindings(Environment env)
	{
		this.airBlock = env.special.AIR;
		this.stoneBlock = env.blocks.fromItem(env.items.getItemById("op.stone"));
		this.stoneBrickBlock = env.blocks.fromItem(env.items.getItemById("op.stone_brick"));
		this.dirtBlock = env.blocks.fromItem(env.items.getItemById("op.dirt"));
		this.grassBlock = env.blocks.fromItem(env.items.getItemById("op.grass"));
		this.tilledSoilBlock = env.blocks.fromItem(env.items.getItemById("op.tilled_soil"));
		this.torchBlock = env.blocks.fromItem(env.items.getItemById("op.torch"));
		this.saplingBlock = env.blocks.fromItem(env.items.getItemById("op.sapling"));
		this.doorBlock = env.blocks.fromItem(env.items.getItemById("op.door"));
		this.portalStoneBlock = env.blocks.fromItem(env.items.getItemById("op.portal_stone"));
		this.portalKeystoneBlock = env.blocks.fromItem(env.items.getItemById("op.portal_keystone"));
		this.pedestalBlock = env.blocks.fromItem(env.items.getItemById("op.pedestal"));
		this.sandBlock = env.blocks.fromItem(env.items.getItemById("op.sand"));
		this.basaltBlock = env.blocks.fromItem(env.items.getItemById("op.basalt"));
		this.lanternBlock = env.blocks.fromItem(env.items.getItemById("op.lantern"));
		this.coalOreBlock = env.blocks.fromItem(env.items.getItemById("op.coal_ore"));
		this.ironOreBlock = env.blocks.fromItem(env.items.getItemById("op.iron_ore"));
		this.copperOreBlock = env.blocks.fromItem(env.items.getItemById("op.copper_ore"));
		this.diamondOreBlock = env.blocks.fromItem(env.items.getItemById("op.diamond_ore"));
		
		this.waterSourceBlock = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		this.lavaSourceBlock = env.blocks.fromItem(env.items.getItemById("op.lava_source"));
		
		this.wheatSeedlingBlock = env.blocks.fromItem(env.items.getItemById("op.wheat_seedling"));
		this.carrotSeedlingBlock = env.blocks.fromItem(env.items.getItemById("op.carrot_seedling"));
		this.wheatMatureBlock = env.blocks.fromItem(env.items.getItemById("op.wheat_mature"));
		this.carrotMatureBlock = env.blocks.fromItem(env.items.getItemById("op.carrot_mature"));
		this.logBlock = env.blocks.fromItem(env.items.getItemById("op.log"));
		this.leafBlock = env.blocks.fromItem(env.items.getItemById("op.leaf"));
	}
}
