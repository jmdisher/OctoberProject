package com.jeffdisher.october.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.NonStackableItem;
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

	public CommonStructures(Environment env) throws IOException
	{
		Block airBlock = env.blocks.fromItem(env.items.getItemById("op.air"));
		Block stoneBlock = env.blocks.fromItem(env.items.getItemById("op.stone"));
		Block stoneBrickBlock = env.blocks.fromItem(env.items.getItemById("op.stone_brick"));
		Block dirtBlock = env.blocks.fromItem(env.items.getItemById("op.dirt"));
		Block grassBlock = env.blocks.fromItem(env.items.getItemById("op.grass"));
		Block tilledSoilBlock = env.blocks.fromItem(env.items.getItemById("op.tilled_soil"));
		Block torchBlock = env.blocks.fromItem(env.items.getItemById("op.torch"));
		Block wheatBlock = env.blocks.fromItem(env.items.getItemById("op.wheat_seedling"));
		Block carrotBlock = env.blocks.fromItem(env.items.getItemById("op.carrot_seedling"));
		Block saplingBlock = env.blocks.fromItem(env.items.getItemById("op.sapling"));
		Block waterSourceBlock = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		Block voidStoneBlock = env.blocks.fromItem(env.items.getItemById("op.portal_stone"));
		Block doorBlock = env.blocks.fromItem(env.items.getItemById("op.door"));
		Block portalKeystoneBlock = env.blocks.fromItem(env.items.getItemById("op.portal_keystone"));
		Block pedestalBlock = env.blocks.fromItem(env.items.getItemById("op.pedestal"));
		
		Item portalOrb = env.items.getItemById("op.portal_orb");
		NonStackableItem northOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(0, 1000, 0)));
		NonStackableItem southOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(0, -1000, 0)));
		NonStackableItem eastOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(1000, 0, 0)));
		NonStackableItem westOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(-1000, 0, 0)));
		NonStackableItem reverseOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(0, -1000, 0)));
		
		// Special items to generate in pedestals.
		Item diamondPick = env.items.getItemById("op.diamond_pickaxe");
		NonStackableItem specialPick= new NonStackableItem(diamondPick, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondPick)
			, PropertyRegistry.NAME, "Hewing Pick Axe"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)5
		));
		Item diamondShovel = env.items.getItemById("op.diamond_shovel");
		NonStackableItem specialShovel= new NonStackableItem(diamondShovel, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondShovel)
			, PropertyRegistry.NAME, "Excavator Shovel"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)5
		));
		Item diamondAxe = env.items.getItemById("op.diamond_axe");
		NonStackableItem specialAxe= new NonStackableItem(diamondAxe, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondAxe)
			, PropertyRegistry.NAME, "Cleaving Axe"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)5
		));
		Item diamondSword = env.items.getItemById("op.diamond_sword");
		NonStackableItem specialSword = new NonStackableItem(diamondSword, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondSword)
			, PropertyRegistry.NAME, "Vorpal Blade"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_WEAPON_MELEE, (byte)5
		));
		
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
		Assert.assertTrue(null == mapping.put('N', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.NORTH, ItemSlot.fromNonStack(northOrb), null)));
		Assert.assertTrue(null == mapping.put('S', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.SOUTH, ItemSlot.fromNonStack(southOrb), null)));
		Assert.assertTrue(null == mapping.put('E', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.EAST, ItemSlot.fromNonStack(eastOrb), null)));
		Assert.assertTrue(null == mapping.put('W', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.WEST, ItemSlot.fromNonStack(westOrb), null)));
		Assert.assertTrue(null == mapping.put('R', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.NORTH, ItemSlot.fromNonStack(reverseOrb), null)));
		Assert.assertTrue(null == mapping.put('1', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialPick), null)));
		Assert.assertTrue(null == mapping.put('2', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialShovel), null)));
		Assert.assertTrue(null == mapping.put('3', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialAxe), null)));
		Assert.assertTrue(null == mapping.put('4', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialSword), null)));
		
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
