package com.jeffdisher.october.aspects;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockVolume;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.DropChance;
import com.jeffdisher.october.types.Item;


public class TestMiscAspects
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
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
		Assert.assertEquals(start, OrientationAspect.Direction.NORTH.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(-2, 1, 3), OrientationAspect.Direction.WEST.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(-1, -2, 3), OrientationAspect.Direction.SOUTH.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(2, -1, 3), OrientationAspect.Direction.EAST.rotateAboutZ(start));
		Assert.assertArrayEquals(new float[] { -2.4f, -1.1f }, OrientationAspect.Direction.EAST.rotateXYTupleAboutZ(new float[] { 1.1f, -2.4f }), 0.01f);
	}

	@Test
	public void multiBlock() throws Throwable
	{
		Block doorClosed = ENV.blocks.fromItem(ENV.items.getItemById("op.double_door_base"));
		AbsoluteLocation start = new AbsoluteLocation(0, 0, 0);
		List<AbsoluteLocation> extensions = ENV.multiBlocks.getExtensions(doorClosed, start, OrientationAspect.Direction.NORTH);
		Assert.assertEquals(3, extensions.size());
		Assert.assertEquals(new AbsoluteLocation(0, 0, 1), extensions.get(0));
		Assert.assertEquals(new AbsoluteLocation(1, 0, 1), extensions.get(1));
		Assert.assertEquals(new AbsoluteLocation(1, 0, 0), extensions.get(2));
		Assert.assertEquals(new BlockVolume(2, 1, 2), ENV.multiBlocks.getDefaultVolume(doorClosed));
	}

	@Test
	public void orientationRelative() throws Throwable
	{
		AbsoluteLocation blockLocation = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation outputNorth = blockLocation.getRelative(0, 1, 0);
		AbsoluteLocation outputDown = blockLocation.getRelative(0, 0, -1);
		Assert.assertEquals(OrientationAspect.Direction.NORTH, OrientationAspect.getRelativeDirection(blockLocation, outputNorth));
		Assert.assertEquals(OrientationAspect.Direction.DOWN, OrientationAspect.getRelativeDirection(blockLocation, outputDown));
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
	public void logicSpecialChange() throws Throwable
	{
		Block switchBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.switch"));
		Block loaderBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.cuboid_loader"));
		
		Assert.assertTrue(ENV.logic.isManual(switchBlock));
		Assert.assertTrue(ENV.logic.isManual(loaderBlock));
		Assert.assertFalse(ENV.logic.hasSpecialChangeLogic(switchBlock));
		Assert.assertTrue(ENV.logic.hasSpecialChangeLogic(loaderBlock));
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
}
