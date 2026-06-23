package com.jeffdisher.october.aspects;

import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockVolume;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.DropChance;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.SubBlock;


public class TestMiscAspects
{
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void armourRegistry() throws Throwable
	{
		Item helmet = ENV.items.getItemById("op.iron_helmet");
		Item dirt = ENV.items.getItemById("op.dirt");
		Item bucket = ENV.items.getItemById("op.bucket");
		
		Assert.assertEquals(BodyPart.HEAD, ENV.armour.getBodyPart(helmet));
		Assert.assertNull(ENV.armour.getBodyPart(dirt));
		Assert.assertNull(ENV.armour.getBodyPart(bucket));
		
		Assert.assertEquals(10, ENV.armour.getDamageReduction(helmet));
	}

	@Test
	public void blockDropProbabilities() throws Throwable
	{
		// This just makes sure that these were parsed correctly.
		Block leaf = ENV.blocks.fromItem(ENV.items.getItemById("op.leaf"));
		Item sapling = ENV.items.getItemById("op.sapling");
		Item stick = ENV.items.getItemById("op.stick");
		Item apple = ENV.items.getItemById("op.apple");
		
		DropChance[] leafChances = ENV.blocks.possibleDropsOnBreak(leaf);
		Assert.assertEquals(3, leafChances.length);
		Assert.assertEquals(new DropChance(sapling, 50), leafChances[0]);
		Assert.assertEquals(new DropChance(stick, 20), leafChances[1]);
		Assert.assertEquals(new DropChance(apple, 10), leafChances[2]);
	}

	@Test
	public void blockDamage() throws Throwable
	{
		Block air = ENV.special.AIR;
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		Block lavaWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_weak"));
		
		Assert.assertEquals(0, ENV.blocks.getBlockDamage(air));
		Assert.assertEquals(0, ENV.blocks.getBlockDamage(waterSource));
		Assert.assertEquals(10, ENV.blocks.getBlockDamage(lavaWeak));
	}

	@Test
	public void orientationRotate() throws Throwable
	{
		AbsoluteLocation start = new AbsoluteLocation(1, 2, 3);
		Assert.assertEquals(start, FacingDirection.NORTH.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(-2, 1, 3), FacingDirection.WEST.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(-1, -2, 3), FacingDirection.SOUTH.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(2, -1, 3), FacingDirection.EAST.rotateAboutZ(start));
		Assert.assertArrayEquals(new float[] { -2.4f, -1.1f, 0.6f }, FacingDirection.EAST.rotateTripletAboutZ(new float[] { 1.1f, -2.4f, 0.6f }), 0.01f);
		Assert.assertArrayEquals(new float[] { 1.1f, 0.6f, 2.4f }, FacingDirection.DOWN.rotateTripletAboutZ(new float[] { 1.1f, -2.4f, 0.6f }), 0.01f);
	}

	@Test
	public void multiBlock() throws Throwable
	{
		Block doorClosed = ENV.blocks.fromItem(ENV.items.getItemById("op.double_door_base"));
		AbsoluteLocation start = new AbsoluteLocation(0, 0, 0);
		List<AbsoluteLocation> extensions = ENV.multiBlocks.getExtensions(doorClosed, start, FacingDirection.NORTH);
		Assert.assertEquals(3, extensions.size());
		Assert.assertEquals(new AbsoluteLocation(0, 0, 1), extensions.get(0));
		Assert.assertEquals(new AbsoluteLocation(1, 0, 0), extensions.get(1));
		Assert.assertEquals(new AbsoluteLocation(1, 0, 1), extensions.get(2));
		Assert.assertEquals(new BlockVolume(2, 1, 2), ENV.multiBlocks.getDefaultVolume(doorClosed));
	}

	@Test
	public void orientationRelative() throws Throwable
	{
		AbsoluteLocation blockLocation = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation outputNorth = blockLocation.getRelative(0, 1, 0);
		AbsoluteLocation outputDown = blockLocation.getRelative(0, 0, -1);
		AbsoluteLocation outputUp = blockLocation.getRelative(0, 0, 1);
		Assert.assertEquals(FacingDirection.NORTH, FacingDirection.getRelativeDirection(blockLocation, outputNorth));
		Assert.assertEquals(FacingDirection.DOWN, FacingDirection.getRelativeDirection(blockLocation, outputDown));
		Assert.assertEquals(FacingDirection.UP, FacingDirection.getRelativeDirection(blockLocation, outputUp));
	}

	@Test
	public void logic() throws Throwable
	{
		Block switchBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.switch"));
		Block stoneBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		
		Assert.assertTrue(ENV.logic.isManual(switchBlock));
		Assert.assertFalse(ENV.logic.isManual(stoneBlock));
	}

	@Test
	public void voidComposite() throws Throwable
	{
		Block voidStone = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_stone"));
		Block voidLamp = ENV.blocks.fromItem(ENV.items.getItemById("op.void_lamp"));
		Block enchantingTable = ENV.blocks.fromItem(ENV.items.getItemById("op.enchanting_table"));
		
		Assert.assertFalse(ENV.composites.isActiveCornerstone(voidStone));
		Assert.assertTrue(ENV.composites.isActiveCornerstone(voidLamp));
		Assert.assertFalse(ENV.composites.isPassiveCornerstone(voidStone));
		Assert.assertFalse(ENV.composites.isPassiveCornerstone(voidStone));
		Assert.assertFalse(ENV.composites.isActiveCornerstone(enchantingTable));
		Assert.assertTrue(ENV.composites.isPassiveCornerstone(enchantingTable));
		Assert.assertEquals(0, ENV.lighting.getLightEmission(voidLamp, false));
		Assert.assertEquals(LightAspect.MAX_LIGHT, ENV.lighting.getLightEmission(voidLamp, true));
	}

	@Test
	public void specialSlot() throws Throwable
	{
		Block pedestal = ENV.blocks.fromItem(ENV.items.getItemById("op.pedestal"));
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		
		Assert.assertTrue(ENV.specialSlot.hasSpecialSlot(pedestal));
		Assert.assertFalse(ENV.specialSlot.hasSpecialSlot(stone));
	}

	@Test
	public void bowDetails() throws Throwable
	{
		// The bow introduces some new tool information so verify that those are as expected.
		Item arrow = ENV.items.getItemById("op.arrow");
		Item bow = ENV.items.getItemById("op.bow");
		
		Assert.assertEquals(1, ENV.tools.toolWeaponDamage(arrow));
		Assert.assertEquals(0, ENV.tools.getChargeMillis(arrow));
		Assert.assertEquals(null, ENV.tools.getAmmunitionType(arrow));
		
		Assert.assertEquals(1, ENV.tools.toolWeaponDamage(bow));
		Assert.assertEquals(3000, ENV.tools.getChargeMillis(bow));
		Assert.assertEquals(arrow, ENV.tools.getAmmunitionType(bow));
	}

	@Test
	public void liquidFlow() throws Throwable
	{
		// Show liquid flow strength.
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		Block waterStrong = ENV.blocks.fromItem(ENV.items.getItemById("op.water_strong"));
		Block waterWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.water_weak"));
		
		Assert.assertEquals(LiquidRegistry.FLOW_NONE, ENV.liquids.getFlowStrength(stone));
		Assert.assertEquals(LiquidRegistry.FLOW_WEAK, ENV.liquids.getFlowStrength(waterWeak));
		Assert.assertEquals(LiquidRegistry.FLOW_STRONG, ENV.liquids.getFlowStrength(waterStrong));
		Assert.assertEquals(LiquidRegistry.FLOW_SOURCE, ENV.liquids.getFlowStrength(waterSource));
	}

	@Test
	public void bedSubBlocks() throws Throwable
	{
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		Block bed = ENV.blocks.fromItem(ENV.items.getItemById("op.bed"));
		
		// Note that bed and stone are always null, when active, since they have no active variants.
		Assert.assertNull(ENV.blocks.getSubBlocks(stone, true));
		Assert.assertNull(ENV.blocks.getSubBlocks(bed, true));
		
		Assert.assertNull(ENV.blocks.getSubBlocks(stone, false));
		long bits = ENV.blocks.getSubBlocks(bed, false);
		
		// We expect the mask to be the bottom 3/4 of the block.
		long expectedMask = 0x0;
		for (byte z = 0; z < (SubBlock.SUB_BLOCK_EDGE - 1); ++z)
		{
			for (byte y = 0; y < SubBlock.SUB_BLOCK_EDGE; ++y)
			{
				for (byte x = 0; x < SubBlock.SUB_BLOCK_EDGE; ++x)
				{
					SubBlock sub = SubBlock.fromInt(x, y, z);
					expectedMask |= sub.getMask();
				}
			}
		}
		Assert.assertEquals(expectedMask, bits);
	}

	@Test
	public void slabSubBlocks() throws Throwable
	{
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		
		Assert.assertNull(ENV.blocks.getSubBlocks(slab, true));
		long bits = ENV.blocks.getSubBlocks(slab, false);
		
		// The slab has orientation so we expect it to fill the NORTH half of the block, so this can be correctly rotated.
		long expectedMask = 0x0;
		for (byte z = 0; z < SubBlock.SUB_BLOCK_EDGE; ++z)
		{
			for (byte y = 2; y < SubBlock.SUB_BLOCK_EDGE; ++y)
			{
				for (byte x = 0; x < SubBlock.SUB_BLOCK_EDGE; ++x)
				{
					SubBlock sub = SubBlock.fromInt(x, y, z);
					expectedMask |= sub.getMask();
				}
			}
		}
		Assert.assertEquals(expectedMask, bits);
		Assert.assertNotEquals(0L, bits | SubBlock.fromInt(0, 3, 0).getMask());
		Assert.assertNotEquals(0L, bits | FacingDirection.DOWN.inverseRotateInSubBlock(SubBlock.fromInt(0, 0, 0)).getMask());
		Assert.assertNotEquals(0L, bits | FacingDirection.UP.inverseRotateInSubBlock(SubBlock.fromInt(1, 3, 1)).getMask());
	}

	@Test
	public void stairSubBlocks() throws Throwable
	{
		Block stair = ENV.blocks.fromItem(ENV.items.getItemById("op.wood_plank_stair"));
		
		Assert.assertNull(ENV.blocks.getSubBlocks(stair, true));
		long bits = ENV.blocks.getSubBlocks(stair, false);
		
		// The stair has orientation so we expect it to fill the NORTH and DOWN halves of the block, so this can be correctly rotated.
		long expectedMask = 0x0;
		for (byte z = 0; z < SubBlock.SUB_BLOCK_EDGE; ++z)
		{
			for (byte y = 0; y < SubBlock.SUB_BLOCK_EDGE; ++y)
			{
				for (byte x = 0; x < SubBlock.SUB_BLOCK_EDGE; ++x)
				{
					boolean isNorth = (y >= 2);
					boolean isDown = (z <= 1);
					if (isNorth || isDown)
					{
						SubBlock sub = SubBlock.fromInt(x, y, z);
						expectedMask |= sub.getMask();
					}
				}
			}
		}
		Assert.assertEquals(expectedMask, bits);
		Assert.assertNotEquals(0L, bits | SubBlock.fromInt(0, 1, 2).getMask());
		Assert.assertNotEquals(0L, bits | FacingDirection.SOUTH.inverseRotateInSubBlock(SubBlock.fromInt(0, 2, 2)).getMask());
	}

	@Test
	public void farmerOccupation() throws Throwable
	{
		// The farmer occupation is probably the most complex so we use it for this basic test of examining it.
		TradingRegistry.Profession profession = ENV.trading.getProfessionById("op.farmer");
		Assert.assertEquals("op.farmer", profession.id());
		Assert.assertEquals("Farmer", profession.name());
		Assert.assertEquals(4, profession.buyOffers().size());
		Assert.assertEquals(20, profession.buyOffers().get(ENV.items.getItemById("op.stone_hoe")).intValue());
		Assert.assertEquals(3, profession.buyOffers().get(ENV.items.getItemById("op.fertilizer")).intValue());
		Assert.assertEquals(1, profession.buyOffers().get(ENV.items.getItemById("op.wheat_seed")).intValue());
		Assert.assertEquals(1, profession.buyOffers().get(ENV.items.getItemById("op.carrot_seed")).intValue());
		Assert.assertEquals(2, profession.sellOffers().size());
		Assert.assertEquals(3, profession.sellOffers().get(ENV.items.getItemById("op.wheat_item")).intValue());
		Assert.assertEquals(3, profession.sellOffers().get(ENV.items.getItemById("op.carrot_item")).intValue());
		Assert.assertEquals(2, profession.crafts().size());
		
		TradingRegistry.TradeCraft wheat = profession.crafts().get(0);
		Assert.assertEquals(3, wheat.inputs().size());
		Assert.assertEquals(1, wheat.outputs().size());
		Assert.assertEquals(1, wheat.inputs().get(ENV.items.getItemById("op.stone_hoe")).intValue());
		Assert.assertEquals(8, wheat.inputs().get(ENV.items.getItemById("op.fertilizer")).intValue());
		Assert.assertEquals(16, wheat.inputs().get(ENV.items.getItemById("op.wheat_seed")).intValue());
		Assert.assertEquals(32, wheat.outputs().get(ENV.items.getItemById("op.wheat_item")).intValue());
		
		TradingRegistry.TradeCraft carrot = profession.crafts().get(1);
		Assert.assertEquals(3, carrot.inputs().size());
		Assert.assertEquals(1, carrot.outputs().size());
		Assert.assertEquals(1, carrot.inputs().get(ENV.items.getItemById("op.stone_hoe")).intValue());
		Assert.assertEquals(8, carrot.inputs().get(ENV.items.getItemById("op.fertilizer")).intValue());
		Assert.assertEquals(16, carrot.inputs().get(ENV.items.getItemById("op.carrot_seed")).intValue());
		Assert.assertEquals(32, carrot.outputs().get(ENV.items.getItemById("op.carrot_item")).intValue());
		
		Assert.assertEquals(6, profession.targetInventory().size());
		Assert.assertEquals(2, profession.targetInventory().get(ENV.items.getItemById("op.stone_hoe")).intValue());
		Assert.assertEquals(16, profession.targetInventory().get(ENV.items.getItemById("op.fertilizer")).intValue());
		Assert.assertEquals(32, profession.targetInventory().get(ENV.items.getItemById("op.wheat_seed")).intValue());
		Assert.assertEquals(32, profession.targetInventory().get(ENV.items.getItemById("op.carrot_seed")).intValue());
		Assert.assertEquals(64, profession.targetInventory().get(ENV.items.getItemById("op.wheat_item")).intValue());
		Assert.assertEquals(64, profession.targetInventory().get(ENV.items.getItemById("op.carrot_item")).intValue());
	}

	@Test
	public void checkOccupationsProfitable() throws Throwable
	{
		// The occupations should be profitable for the villagers (the outputs of a craft are worth more than inputs).
		int validatedCount = 0;
		for (TradingRegistry.Profession profession : ENV.trading.getAllProfessions())
		{
			for(TradingRegistry.TradeCraft craft : profession.crafts())
			{
				int inputCost = 0;
				for (Map.Entry<Item, Integer> input : craft.inputs().entrySet())
				{
					int unit = profession.buyOffers().get(input.getKey());
					int total = unit * input.getValue();
					inputCost += total;
				}
				int outputValue = 0;
				for (Map.Entry<Item, Integer> output : craft.outputs().entrySet())
				{
					int unit = profession.sellOffers().get(output.getKey());
					int total = unit * output.getValue();
					outputValue += total;
				}
				Assert.assertTrue(outputValue > inputCost);
			}
			validatedCount += 1;
		}
		Assert.assertEquals(6, validatedCount);
	}
}
