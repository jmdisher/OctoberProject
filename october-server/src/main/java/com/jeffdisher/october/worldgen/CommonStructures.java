package com.jeffdisher.october.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.utils.Assert;


/**
 * This is a container of some of the common complex structures used in the world generators which are defined here
 * since the details of how to encode extended aspect data in declarative data files hasn't yet been settled.
 */
public class CommonStructures
{
	public static final int CASTLE_X =  -8;
	public static final int CASTLE_Y =  -8;
	public static final int CASTLE_Z = -10;
	public static final int TOWER_NORTH_X = -5;
	public static final int TOWER_NORTH_Y = 1002;
	public static final int TOWER_SOUTH_X =  5;
	public static final int TOWER_SOUTH_Y = -1002;
	public static final int TOWER_EAST_X = 1002;
	public static final int TOWER_EAST_Y = 5;
	public static final int TOWER_WEST_X =  -1002;
	public static final int TOWER_WEST_Y = -5;
	public static final int TOWER_Z = -6;

	public final Structure nexusCastle;
	public final Structure distanceTower;

	public CommonStructures(Environment env, TerrainBindings terrainBindings, SpecialItemReferences specialItems) throws IOException
	{
		Block airBlock = terrainBindings.airBlock;
		Block stoneBlock = terrainBindings.stoneBlock;
		Block stoneBrickBlock = terrainBindings.stoneBrickBlock;
		Block dirtBlock = terrainBindings.dirtBlock;
		Block grassBlock = terrainBindings.grassBlock;
		Block tilledSoilBlock = terrainBindings.tilledSoilBlock;
		Block torchBlock = terrainBindings.torchBlock;
		Block wheatBlock = terrainBindings.wheatSeedlingBlock;
		Block carrotBlock = terrainBindings.carrotSeedlingBlock;
		Block saplingBlock = terrainBindings.saplingBlock;
		Block waterSourceBlock = terrainBindings.waterSourceBlock;
		Block voidStoneBlock = terrainBindings.portalStoneBlock;
		Block doorBlock = terrainBindings.doorBlock;
		Block portalKeystoneBlock = terrainBindings.portalKeystoneBlock;
		Block pedestalBlock = terrainBindings.pedestalBlock;
		
		Map<Character, Structure.AspectData> mapping = new HashMap<>();
		Assert.assertTrue(null == mapping.put('A', new Structure.AspectData(airBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('#', new Structure.AspectData(stoneBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('B', new Structure.AspectData(stoneBrickBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('D', new Structure.AspectData(dirtBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('G', new Structure.AspectData(grassBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('i', new Structure.AspectData(torchBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('w', new Structure.AspectData(wheatBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('c', new Structure.AspectData(carrotBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('t', new Structure.AspectData(saplingBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('O', new Structure.AspectData(tilledSoilBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('T', new Structure.AspectData(waterSourceBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('V', new Structure.AspectData(voidStoneBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('[', new Structure.AspectData(doorBlock, null, FacingDirection.NORTH, null, null)));
		Assert.assertTrue(null == mapping.put(']', new Structure.AspectData(doorBlock, null, null, null, new AbsoluteLocation(0, 0, -1))));
		Assert.assertTrue(null == mapping.put('N', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.NORTH, ItemSlot.fromNonStack(specialItems.northOrb), null)));
		Assert.assertTrue(null == mapping.put('S', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.SOUTH, ItemSlot.fromNonStack(specialItems.southOrb), null)));
		Assert.assertTrue(null == mapping.put('E', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.EAST, ItemSlot.fromNonStack(specialItems.eastOrb), null)));
		Assert.assertTrue(null == mapping.put('W', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.WEST, ItemSlot.fromNonStack(specialItems.westOrb), null)));
		Assert.assertTrue(null == mapping.put('R', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.NORTH, ItemSlot.fromNonStack(specialItems.reverseOrb), null)));
		Assert.assertTrue(null == mapping.put('1', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialItems.specialPick), null)));
		Assert.assertTrue(null == mapping.put('2', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialItems.specialShovel), null)));
		Assert.assertTrue(null == mapping.put('3', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialItems.specialAxe), null)));
		Assert.assertTrue(null == mapping.put('4', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialItems.specialSword), null)));
		
		StructureLoader loader = new StructureLoader(mapping);
		ClassLoader classLoader = getClass().getClassLoader();
		try (InputStream stream  = classLoader.getResourceAsStream("nexus_castle.structure"))
		{
			String[] zLayers = StructureLoader.splitStreamIntoZLayerStrings(stream);
			this.nexusCastle = loader.loadFromStrings(zLayers);
		}
		try (InputStream stream  = classLoader.getResourceAsStream("distance_tower.structure"))
		{
			String[] zLayers = StructureLoader.splitStreamIntoZLayerStrings(stream);
			this.distanceTower = loader.loadFromStrings(zLayers);
		}
	}
}
