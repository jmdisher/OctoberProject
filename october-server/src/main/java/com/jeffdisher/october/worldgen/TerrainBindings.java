package com.jeffdisher.october.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.utils.Assert;


/**
 * The bindings for general terrain blocks used in world generation.
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

	public TerrainBindings(Environment env) throws IOException
	{
		Map<String, Block> mapping;
		try (InputStream stream  = getClass().getClassLoader().getResourceAsStream("terrain_bindings.tablist"))
		{
			FlatTabListCallbacks<String, Block> callbacks = new FlatTabListCallbacks<>((String value) -> value, new IValueTransformer.BlockTransformer(env.items, env.blocks));
			TabListReader.readEntireFile(callbacks, stream);
			mapping = callbacks.data;
		}
		catch (TabListReader.TabListException e)
		{
			// TODO:  Determine a better way to handle this.
			throw Assert.unexpected(e);
		}
		
		// We will require that all of these be found.
		this.airBlock = _requiredBlock(mapping, "airBlock");
		this.stoneBlock = _requiredBlock(mapping, "stoneBlock");
		this.dirtBlock = _requiredBlock(mapping, "dirtBlock");
		this.grassBlock = _requiredBlock(mapping, "grassBlock");
		this.tilledSoilBlock = _requiredBlock(mapping, "tilledSoilBlock");
		this.sandBlock = _requiredBlock(mapping, "sandBlock");
		this.basaltBlock = _requiredBlock(mapping, "basaltBlock");
		this.coalOreBlock = _requiredBlock(mapping, "coalOreBlock");
		this.ironOreBlock = _requiredBlock(mapping, "ironOreBlock");
		
		this.waterSourceBlock = _requiredBlock(mapping, "waterSourceBlock");
		this.lavaSourceBlock = _requiredBlock(mapping, "lavaSourceBlock");
		
		this.wheatMatureBlock = _requiredBlock(mapping, "wheatMatureBlock");
		this.carrotMatureBlock = _requiredBlock(mapping, "carrotMatureBlock");
		this.logBlock = _requiredBlock(mapping, "logBlock");
	}


	private Block _requiredBlock(Map<String, Block> mapping, String name)
	{
		Block block = mapping.get(name);
		Assert.assertTrue(null != block);
		return block;
	}
}
