package com.jeffdisher.october.worldgen;

import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestStructureLoader
{
	private static Environment ENV;
	private static Block DIRT;
	private static Block STONE_BRICK;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		DIRT = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		STONE_BRICK = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void singleBlock()
	{
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {
				"D\n",
		};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		AbsoluteLocation target = new AbsoluteLocation(5, 6, 7);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
		Assert.assertTrue(followUp.isEmpty());
		
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(4, 5, 6)));
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 6, 7)));
	}

	@Test
	public void smallPrism()
	{
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {""
				+ "B B B\n"
				+ " D D \n"
				+ "B B B\n"
				, ""
				+ " D D \n"
				+ "B B B\n"
				+ " D D \n"
				, ""
				+ "B B B\n"
				+ " D D \n"
				+ "B B B\n"
		};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		Assert.assertEquals(new AbsoluteLocation(5, 3, 3), structure.totalVolume());
		
		AbsoluteLocation target = new AbsoluteLocation(5, 6, 7);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
		Assert.assertTrue(followUp.isEmpty());
		
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 0).getBlockAddress()));
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(2, 2, 2).getBlockAddress()));
	}

	@Test
	public void offsetPrism()
	{
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {""
				+ "B B B\n"
				+ " D D \n"
				+ "B B B\n"
				, ""
				+ " D D \n"
				+ "B B B\n"
				+ " D D \n"
				, ""
				+ "B B B\n"
				+ " D D \n"
				+ "B B B\n"
		};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Offset with the positive edge.
		AbsoluteLocation target = new AbsoluteLocation(30, 30, 30);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
		Assert.assertTrue(followUp.isEmpty());
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 0).getBlockAddress()));
		
		// Offset with the negative edge.
		AbsoluteLocation negativeTarget = target.getRelative(-32, -32, -32);
		followUp = structure.applyToCuboid(cuboid, negativeTarget, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
		Assert.assertTrue(followUp.isEmpty());
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, negativeTarget.getRelative(2, 2, 2).getBlockAddress()));
	}

	@Test
	public void delayedPlacement()
	{
		// Make sure that things which grow or are light sources are replaced by air with mutations to place later.
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {" P B S B L \n"};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, DIRT);
		Structure structure = loader.loadFromStrings(zLayers);
		
		AbsoluteLocation target = new AbsoluteLocation(5, 6, 7);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
		// We expect 3 things here with x-offsets: +1, +5, +9.
		AbsoluteLocation wait1 = target.getRelative(1, 0, 0);
		AbsoluteLocation wait2 = target.getRelative(5, 0, 0);
		AbsoluteLocation wait3 = target.getRelative(9, 0, 0);
		
		List<MutationBlockOverwriteInternal> changes = followUp.overwriteMutations();
		Map<BlockAddress, Long> periodicMutationMillis = followUp.periodicMutationMillis();
		Assert.assertEquals(1, changes.size());
		Assert.assertEquals(wait3, changes.get(0).getAbsoluteLocation());
		
		Assert.assertEquals(2, periodicMutationMillis.size());
		Assert.assertTrue(periodicMutationMillis.keySet().contains(wait1.getBlockAddress()));
		Assert.assertTrue(periodicMutationMillis.keySet().contains(wait2.getBlockAddress()));
		
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.wheat_seedling").number(), cuboid.getData15(AspectRegistry.BLOCK, wait1.getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.sapling").number(), cuboid.getData15(AspectRegistry.BLOCK, wait2.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, wait3.getBlockAddress()));
	}

	@Test
	public void oreNode()
	{
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {""
				+ "A A\n"
				+ "III\n"
				+ "A A\n"
				, ""
				+ " I \n"
				+ "III\n"
				+ " I \n"
				, ""
				+ "A A\n"
				+ "III\n"
				+ "A A\n"
		};
		// Create a cuboid where only the bottom layer is stone but the rest is air.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		short stoneBlockValue = ENV.items.getItemById("op.stone").number();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress(x, y, (byte)0), stoneBlockValue);
			}
		}
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Load into the base of the cuboid and see that only the stone blocks have been replaced but the air left unchanged.
		short airValue = ENV.special.AIR.item().number();
		short coalOreValue = ENV.items.getItemById("op.coal_ore").number();
		short ironOreValue = ENV.items.getItemById("op.iron_ore").number();
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.NORTH, stoneBlockValue);
		Assert.assertTrue(followUp.isEmpty());
		for (byte z = 1; z < Encoding.CUBOID_EDGE_SIZE; ++z)
		{
			for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
			{
				for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
				{
					Assert.assertEquals(airValue, cuboid.getData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z)));
				}
			}
		}
		Assert.assertEquals(coalOreValue, cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(ironOreValue, cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 0).getBlockAddress()));
	}

	@Test
	public void treeParts()
	{
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {""
				+ "T \n"
				+ "  \n"
				, ""
				+ "EE\n"
				+ "EE\n"
		};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Load into the base of the cuboid and see that only the stone blocks have been replaced but the air left unchanged.
		short logValue = ENV.items.getItemById("op.log").number();
		short leafValue = ENV.items.getItemById("op.leaf").number();
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
		Assert.assertTrue(followUp.isEmpty());
		Assert.assertEquals(logValue, cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(leafValue, cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 1).getBlockAddress()));
	}

	@Test
	public void customMapping()
	{
		Block voidStone = ENV.blocks.fromItem(ENV.items.getItemById("op.void_stone"));
		Block voidLamp = ENV.blocks.fromItem(ENV.items.getItemById("op.void_lamp"));
		Map<Character, Structure.AspectData> mapping = Map.of(
			'V', new Structure.AspectData(voidStone, null, null, null, null)
			, 'L', new Structure.AspectData(voidLamp, null, null, null, null)
		);
		StructureLoader loader = new StructureLoader(mapping);
		String[] zLayers = new String[] {""
				+ "VV\n"
				, ""
				+ "L \n"
		};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Load into the base of the cuboid and see that only the stone blocks have been replaced but the air left unchanged.
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
		
		// We expect 1 update since the void lamp is a composite structure.
		AbsoluteLocation wait1 = target.getRelative(0, 0, 1);
		List<MutationBlockOverwriteInternal> changes = followUp.overwriteMutations(); 
		Map<BlockAddress, Long> periodicMutationMillis = followUp.periodicMutationMillis();
		Assert.assertEquals(0, changes.size());
		Assert.assertEquals(1, periodicMutationMillis.size());
		Assert.assertTrue(periodicMutationMillis.keySet().contains(wait1.getBlockAddress()));
		
		Assert.assertEquals(voidStone.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(voidStone.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.void_lamp").number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 0, 1).getBlockAddress()));
	}

	@Test
	public void rotation()
	{
		// Test what happens when we try writing a rotated structure into the cuboid edges.
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		// NOTE:  The way that these are specified is west->east, south->north (NOT left->right, top->bottom).
		String[] zLayers = new String[] {""
				+ "DTB\n"
				+ "ATI\n"
		};
		CuboidAddress nwCuboidAddress = CuboidAddress.fromInt(-1, 0, 0);
		CuboidAddress neCuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress swCuboidAddress = CuboidAddress.fromInt(-1, -1, 0);
		CuboidAddress seCuboidAddress = CuboidAddress.fromInt(0, -1, 0);
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Load into the base of the cuboid and see that only the stone blocks have been replaced but the air left unchanged.
		short dirtValue = ENV.items.getItemById("op.dirt").number();
		short stoneBrickValue = ENV.items.getItemById("op.stone_brick").number();
		short coalOreValue = ENV.items.getItemById("op.coal_ore").number();
		short ironOreValue = ENV.items.getItemById("op.iron_ore").number();
		
		// North - this is the default - doesn't rotate.
		CuboidData nwCuboid = CuboidGenerator.createFilledCuboid(nwCuboidAddress, ENV.special.AIR);
		CuboidData neCuboid = CuboidGenerator.createFilledCuboid(neCuboidAddress, ENV.special.AIR);
		CuboidData swCuboid = CuboidGenerator.createFilledCuboid(swCuboidAddress, ENV.special.AIR);
		CuboidData seCuboid = CuboidGenerator.createFilledCuboid(seCuboidAddress, ENV.special.AIR);
		AbsoluteLocation rootLocation = new AbsoluteLocation(-1, -1, 7);
		Assert.assertTrue(structure.applyToCuboid(nwCuboid, rootLocation, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(neCuboid, rootLocation, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(swCuboid, rootLocation, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(seCuboid, rootLocation, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertEquals(coalOreValue, nwCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 0, 7)));
		Assert.assertEquals(ironOreValue, neCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 0, 7)));
		Assert.assertEquals(dirtValue, swCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 31, 7)));
		Assert.assertEquals(stoneBrickValue, seCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 31, 7)));
		
		// East - clockwise 90.
		nwCuboid = CuboidGenerator.createFilledCuboid(nwCuboidAddress, ENV.special.AIR);
		neCuboid = CuboidGenerator.createFilledCuboid(neCuboidAddress, ENV.special.AIR);
		swCuboid = CuboidGenerator.createFilledCuboid(swCuboidAddress, ENV.special.AIR);
		seCuboid = CuboidGenerator.createFilledCuboid(seCuboidAddress, ENV.special.AIR);
		rootLocation = new AbsoluteLocation(-1, 0, 7);
		Assert.assertTrue(structure.applyToCuboid(nwCuboid, rootLocation, OrientationAspect.Direction.EAST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(neCuboid, rootLocation, OrientationAspect.Direction.EAST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(swCuboid, rootLocation, OrientationAspect.Direction.EAST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(seCuboid, rootLocation, OrientationAspect.Direction.EAST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertEquals(dirtValue, nwCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 0, 7)));
		Assert.assertEquals(coalOreValue, neCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 7)));
		Assert.assertEquals(stoneBrickValue, swCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 30, 7)));
		Assert.assertEquals(ironOreValue, seCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 30, 7)));
		
		// South - clockwise 180.
		nwCuboid = CuboidGenerator.createFilledCuboid(nwCuboidAddress, ENV.special.AIR);
		neCuboid = CuboidGenerator.createFilledCuboid(neCuboidAddress, ENV.special.AIR);
		swCuboid = CuboidGenerator.createFilledCuboid(swCuboidAddress, ENV.special.AIR);
		seCuboid = CuboidGenerator.createFilledCuboid(seCuboidAddress, ENV.special.AIR);
		rootLocation = new AbsoluteLocation(0, 0, 7);
		Assert.assertTrue(structure.applyToCuboid(nwCuboid, rootLocation, OrientationAspect.Direction.SOUTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(neCuboid, rootLocation, OrientationAspect.Direction.SOUTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(swCuboid, rootLocation, OrientationAspect.Direction.SOUTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(seCuboid, rootLocation, OrientationAspect.Direction.SOUTH, Structure.REPLACE_ALL).isEmpty());
		Assert.assertEquals(stoneBrickValue, nwCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 0, 7)));
		Assert.assertEquals(dirtValue, neCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 7)));
		Assert.assertEquals(ironOreValue, swCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 31, 7)));
		Assert.assertEquals(coalOreValue, seCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 31, 7)));
		
		// West - clockwise 270.
		nwCuboid = CuboidGenerator.createFilledCuboid(nwCuboidAddress, ENV.special.AIR);
		neCuboid = CuboidGenerator.createFilledCuboid(neCuboidAddress, ENV.special.AIR);
		swCuboid = CuboidGenerator.createFilledCuboid(swCuboidAddress, ENV.special.AIR);
		seCuboid = CuboidGenerator.createFilledCuboid(seCuboidAddress, ENV.special.AIR);
		rootLocation = new AbsoluteLocation(0, -1, 7);
		Assert.assertTrue(structure.applyToCuboid(nwCuboid, rootLocation, OrientationAspect.Direction.WEST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(neCuboid, rootLocation, OrientationAspect.Direction.WEST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(swCuboid, rootLocation, OrientationAspect.Direction.WEST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertTrue(structure.applyToCuboid(seCuboid, rootLocation, OrientationAspect.Direction.WEST, Structure.REPLACE_ALL).isEmpty());
		Assert.assertEquals(ironOreValue, nwCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 1, 7)));
		Assert.assertEquals(stoneBrickValue, neCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 1, 7)));
		Assert.assertEquals(coalOreValue, swCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 31, 7)));
		Assert.assertEquals(dirtValue, seCuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 31, 7)));
	}

	@Test
	public void additionalData()
	{
		Block andGate = ENV.blocks.fromItem(ENV.items.getItemById("op.and_gate"));
		Block chest = ENV.blocks.fromItem(ENV.items.getItemById("op.chest"));
		Block pedestal = ENV.blocks.fromItem(ENV.items.getItemById("op.pedestal"));
		Item sword = ENV.items.getItemById("op.iron_sword");
		NonStackableItem customSword = new NonStackableItem(sword, Map.of(
			PropertyRegistry.DURABILITY, 10
			, PropertyRegistry.NAME, "Custom name"
		));
		Inventory inv = Inventory.start(10)
			.addStackable(DIRT.item(), 2)
			.addNonStackable(PropertyHelpers.newItemWithDefaults(ENV, sword))
			.finish()
		;
		Map<Character, Structure.AspectData> mapping = Map.of(
			'C', new Structure.AspectData(chest, inv, null, null, null)
			, 'I', new Structure.AspectData(pedestal, null, null, ItemSlot.fromNonStack(customSword), null)
			, 'N', new Structure.AspectData(andGate, null, OrientationAspect.Direction.NORTH, null, null)
			, 'E', new Structure.AspectData(andGate, null, OrientationAspect.Direction.EAST, null, null)
			, 'W', new Structure.AspectData(andGate, null, OrientationAspect.Direction.WEST, null, null)
			, 'S', new Structure.AspectData(andGate, null, OrientationAspect.Direction.SOUTH, null, null)
		);
		StructureLoader loader = new StructureLoader(mapping);
		String[] zLayers = new String[] {""
				+ " C I N E W S \n"
		};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, DIRT);
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Load into the base of the cuboid and see that only the stone blocks have been replaced but the air left unchanged.
		AbsoluteLocation target = new AbsoluteLocation(15, 15, 15);
		Structure.FollowUp followUp = structure.applyToCuboid(cuboid, target, OrientationAspect.Direction.EAST, Structure.REPLACE_ALL);
		Assert.assertTrue(followUp.isEmpty());
		
		Assert.assertEquals(inv, cuboid.getDataSpecial(AspectRegistry.INVENTORY, target.getRelative(0, -1, 0).getBlockAddress()));
		Assert.assertEquals("Custom name", cuboid.getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, target.getRelative(0, -3, 0).getBlockAddress()).nonStackable.properties().get(PropertyRegistry.NAME));
		Assert.assertEquals(OrientationAspect.Direction.EAST.ordinal(), cuboid.getData7(AspectRegistry.ORIENTATION, target.getRelative(0, -5, 0).getBlockAddress()));
		Assert.assertEquals(OrientationAspect.Direction.NORTH.ordinal(), cuboid.getData7(AspectRegistry.ORIENTATION, target.getRelative(0, -9, 0).getBlockAddress()));
	}

	@Test
	public void cuboidIntersection()
	{
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {
				"DDD\n",
		};
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		Structure structure = loader.loadFromStrings(zLayers);
		Assert.assertTrue(structure.doesIntersectCuboid(address, new AbsoluteLocation(-1, 0, 0), OrientationAspect.Direction.NORTH));
		Assert.assertFalse(structure.doesIntersectCuboid(address, new AbsoluteLocation(-1, 0, 0), OrientationAspect.Direction.EAST));
		Assert.assertTrue(structure.doesIntersectCuboid(address, new AbsoluteLocation(31, 0, 0), OrientationAspect.Direction.NORTH));
		Assert.assertFalse(structure.doesIntersectCuboid(address, new AbsoluteLocation(31, 40, 0), OrientationAspect.Direction.NORTH));
	}

	@Test
	public void bigCuboidIntersection()
	{
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(ENV.items, ENV.blocks));
		String[] zLayers = new String[] {
				"DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD\n",
		};
		Structure structure = loader.loadFromStrings(zLayers);
		AbsoluteLocation furtherTarget = new AbsoluteLocation(-1, 1000, 0);
		Assert.assertTrue(structure.doesIntersectCuboid(furtherTarget.getCuboidAddress(), furtherTarget, OrientationAspect.Direction.EAST));
		Assert.assertTrue(structure.doesIntersectCuboid(furtherTarget.getRelative(0, -32, 0).getCuboidAddress(), furtherTarget, OrientationAspect.Direction.EAST));
		Assert.assertFalse(structure.doesIntersectCuboid(furtherTarget.getRelative(0, 32, 0).getCuboidAddress(), furtherTarget, OrientationAspect.Direction.EAST));
	}

	@Test
	public void portalsInFlat()
	{
		// Make sure that the portals are properly placed in the FlatWorldGenerator.
		FlatWorldGenerator gen = new FlatWorldGenerator(ENV, true);
		
		AbsoluteLocation northFacingKeystone = new AbsoluteLocation(0, 5, -1);
		SuspendedCuboid<CuboidData> northFacing = gen.generateCuboid(new CreatureIdAssigner(), northFacingKeystone.getCuboidAddress(), 0L);
		// There are 2 portals in this cuboid:  (5, 0, -1) and (0, 5, -1).
		Assert.assertEquals(2, northFacing.periodicMutationMillis().size());
		Assert.assertTrue(northFacing.periodicMutationMillis().containsKey(northFacingKeystone.getBlockAddress()));
		// We see the placement of torches and the grass as needing to be placed in a follow-up tick since they change lighting or ground cover.
		Assert.assertEquals(21, northFacing.pendingMutations().size());
		AbsoluteLocation northFacingTarget = (AbsoluteLocation) northFacing.cuboid().getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, northFacingKeystone.getBlockAddress()).nonStackable.properties().get(PropertyRegistry.LOCATION);
		
		AbsoluteLocation southFacingKeystone = new AbsoluteLocation(0, 1005, -1);
		SuspendedCuboid<CuboidData> southFacing = gen.generateCuboid(new CreatureIdAssigner(), southFacingKeystone.getCuboidAddress(), 0L);
		Assert.assertEquals(southFacingKeystone.getBlockAddress(), southFacing.periodicMutationMillis().keySet().iterator().next());
		AbsoluteLocation southFacingTarget = (AbsoluteLocation) southFacing.cuboid().getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, southFacingKeystone.getBlockAddress()).nonStackable.properties().get(PropertyRegistry.LOCATION);
		
		Assert.assertEquals(southFacingKeystone, northFacingTarget);
		Assert.assertEquals(northFacingKeystone, southFacingTarget);
	}
}
