package com.jeffdisher.october.worldgen;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


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
		StructureLoader loader = new StructureLoader(ENV.items, ENV.blocks);
		String[] zLayers = new String[] {
				"D\n",
		};
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		AbsoluteLocation target = new AbsoluteLocation(5, 6, 7);
		List<IMutationBlock> changes = structure.applyToCuboid(cuboid, target, Structure.REPLACE_ALL);
		Assert.assertTrue(changes.isEmpty());
		
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)4, (byte)5, (byte)6)));
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)6, (byte)7)));
	}

	@Test
	public void smallPrism()
	{
		StructureLoader loader = new StructureLoader(ENV.items, ENV.blocks);
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
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		Assert.assertEquals(new AbsoluteLocation(5, 3, 3), structure.totalVolume());
		
		AbsoluteLocation target = new AbsoluteLocation(5, 6, 7);
		List<IMutationBlock> changes = structure.applyToCuboid(cuboid, target, Structure.REPLACE_ALL);
		Assert.assertTrue(changes.isEmpty());
		
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 0).getBlockAddress()));
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(2, 2, 2).getBlockAddress()));
	}

	@Test
	public void offsetPrism()
	{
		StructureLoader loader = new StructureLoader(ENV.items, ENV.blocks);
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
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Offset with the positive edge.
		AbsoluteLocation target = new AbsoluteLocation(30, 30, 30);
		List<IMutationBlock> changes = structure.applyToCuboid(cuboid, target, Structure.REPLACE_ALL);
		Assert.assertTrue(changes.isEmpty());
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 0).getBlockAddress()));
		
		// Offset with the negative edge.
		AbsoluteLocation negativeTarget = target.getRelative(-32, -32, -32);
		changes = structure.applyToCuboid(cuboid, negativeTarget, Structure.REPLACE_ALL);
		Assert.assertTrue(changes.isEmpty());
		Assert.assertEquals(STONE_BRICK.item().number(), cuboid.getData15(AspectRegistry.BLOCK, negativeTarget.getRelative(2, 2, 2).getBlockAddress()));
	}

	@Test
	public void delayedPlacement()
	{
		// Make sure that things which grow or are light sources are replaced by air with mutations to place later.
		StructureLoader loader = new StructureLoader(ENV.items, ENV.blocks);
		String[] zLayers = new String[] {" P B S B L \n"};
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, DIRT);
		Structure structure = loader.loadFromStrings(zLayers);
		
		AbsoluteLocation target = new AbsoluteLocation(5, 6, 7);
		List<IMutationBlock> changes = structure.applyToCuboid(cuboid, target, Structure.REPLACE_ALL);
		// We expect 3 things here with x-offsets: +1, +5, +9.
		AbsoluteLocation wait1 = target.getRelative(1, 0, 0);
		AbsoluteLocation wait2 = target.getRelative(5, 0, 0);
		AbsoluteLocation wait3 = target.getRelative(9, 0, 0);
		Assert.assertEquals(3, changes.size());
		Set<AbsoluteLocation> locations = changes.stream().map((IMutationBlock mutation) -> mutation.getAbsoluteLocation()).collect(Collectors.toSet());
		locations.contains(wait1);
		locations.contains(wait2);
		locations.contains(wait3);
		
		Assert.assertEquals(DIRT.item().number(), cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, wait1.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, wait2.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), cuboid.getData15(AspectRegistry.BLOCK, wait3.getBlockAddress()));
	}

	@Test
	public void oreNode()
	{
		StructureLoader loader = new StructureLoader(ENV.items, ENV.blocks);
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
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		short stoneBlockValue = ENV.items.getItemById("op.stone").number();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		for (byte y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
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
		List<IMutationBlock> changes = structure.applyToCuboid(cuboid, target, stoneBlockValue);
		Assert.assertTrue(changes.isEmpty());
		for (byte z = 1; z < Structure.CUBOID_EDGE_SIZE; ++z)
		{
			for (byte y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
			{
				for (byte x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
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
		StructureLoader loader = new StructureLoader(ENV.items, ENV.blocks);
		String[] zLayers = new String[] {""
				+ "T \n"
				+ "  \n"
				, ""
				+ "EE\n"
				+ "EE\n"
		};
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Structure structure = loader.loadFromStrings(zLayers);
		
		// Load into the base of the cuboid and see that only the stone blocks have been replaced but the air left unchanged.
		short logValue = ENV.items.getItemById("op.log").number();
		short leafValue = ENV.items.getItemById("op.leaf").number();
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		List<IMutationBlock> changes = structure.applyToCuboid(cuboid, target, Structure.REPLACE_ALL);
		Assert.assertTrue(changes.isEmpty());
		Assert.assertEquals(logValue, cuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(leafValue, cuboid.getData15(AspectRegistry.BLOCK, target.getRelative(1, 1, 1).getBlockAddress()));
	}
}
