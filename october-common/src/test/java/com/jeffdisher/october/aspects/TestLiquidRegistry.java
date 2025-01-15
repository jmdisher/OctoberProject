package com.jeffdisher.october.aspects;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.Block;


/**
 * Tests for specific situations around the LiquidRegistry since it is normally only accessible via TickRunner (or
 * WorldProcessor).
 */
public class TestLiquidRegistry
{
	private static Environment ENV;

	private static Block WATER_SOURCE;
	private static Block WATER_STRONG;
	private static Block WATER_WEAK;
	private static Block LAVA_SOURCE;
	private static Block LAVA_STRONG;
	private static Block LAVA_WEAK;
	private static Block STONE;
	private static Block BASALT;

	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		WATER_STRONG = ENV.blocks.fromItem(ENV.items.getItemById("op.water_strong"));
		WATER_WEAK = ENV.blocks.fromItem(ENV.items.getItemById("op.water_weak"));
		LAVA_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_source"));
		LAVA_STRONG = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_strong"));
		LAVA_WEAK = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_weak"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		BASALT = ENV.blocks.fromItem(ENV.items.getItemById("op.basalt"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void infiniteSourceNotLava() throws Throwable
	{
		// Water creates infinite sources, not lava.
		Block target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, WATER_SOURCE, WATER_SOURCE, STONE, STONE, ENV.special.AIR, STONE);
		Assert.assertEquals(WATER_SOURCE, target);
		
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, LAVA_SOURCE, LAVA_SOURCE, STONE, STONE, ENV.special.AIR, STONE);
		Assert.assertEquals(LAVA_STRONG, target);
	}

	@Test
	public void solidification() throws Throwable
	{
		// Convert water.
		Block target = ENV.liquids.chooseEmptyLiquidBlock(ENV, WATER_SOURCE, STONE, STONE, STONE, STONE, LAVA_WEAK, STONE);
		Assert.assertEquals(STONE, target);
		
		// Convert lava.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, LAVA_STRONG, STONE, STONE, STONE, STONE, WATER_WEAK, STONE);
		Assert.assertEquals(BASALT, target);
	}

	@Test
	public void fallingWater() throws Throwable
	{
		// We show what happens in different falling water scenarios.
		// Under a water source.
		Block target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, WATER_SOURCE, ENV.special.AIR);
		Assert.assertEquals(WATER_WEAK, target);
		
		// Under water strong.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, WATER_STRONG, ENV.special.AIR);
		Assert.assertEquals(WATER_WEAK, target);
		
		// Under water weak.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, WATER_WEAK, ENV.special.AIR);
		Assert.assertEquals(WATER_WEAK, target);
		
		// Next to water source.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, WATER_SOURCE, ENV.special.AIR, ENV.special.AIR);
		Assert.assertEquals(WATER_WEAK, target);
		
		// Next to water strong.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, WATER_STRONG, ENV.special.AIR, ENV.special.AIR);
		Assert.assertEquals(WATER_WEAK, target);
	}

	@Test
	public void queries() throws Throwable
	{
		// Check the millisecond delays on flows (based on known constants in the config).
		Assert.assertEquals(100L, ENV.liquids.flowDelayMillis(ENV, WATER_SOURCE));
		Assert.assertEquals(100L, ENV.liquids.flowDelayMillis(ENV, WATER_STRONG));
		Assert.assertEquals(100L, ENV.liquids.flowDelayMillis(ENV, WATER_WEAK));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(ENV, LAVA_SOURCE));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(ENV, LAVA_STRONG));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(ENV, LAVA_WEAK));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(ENV, ENV.special.AIR));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(ENV, STONE));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(ENV, BASALT));
		Assert.assertEquals(100L, ENV.liquids.minFlowDelayMillis(ENV, WATER_SOURCE, BASALT));
	}

	@Test
	public void reflowOnUpdate() throws Throwable
	{
		// Show what happens when water flows over lava.
		// -we should flow weak over a lava flow.
		Block target = ENV.liquids.chooseEmptyLiquidBlock(ENV, ENV.special.AIR, WATER_SOURCE, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, LAVA_WEAK);
		Assert.assertEquals(WATER_WEAK, target);
		
		// -the lava should update to basalt.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, LAVA_WEAK, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, WATER_WEAK, ENV.special.AIR);
		Assert.assertEquals(BASALT, target);
		
		// -the weak flow above should now be strong.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, WATER_WEAK, WATER_SOURCE, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, BASALT);
		Assert.assertEquals(WATER_STRONG, target);
	}

	@Test
	public void standalone() throws Throwable
	{
		// We want to see what happens when we update a single location with no sources around it.
		// -weak flow surrounded by blocks
		Block target = ENV.liquids.chooseEmptyLiquidBlock(ENV, WATER_WEAK, STONE, STONE, STONE, STONE, STONE, STONE);
		Assert.assertEquals(ENV.special.AIR, target);
		
		// -weak flow with no blocks around
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, WATER_WEAK, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR);
		Assert.assertEquals(ENV.special.AIR, target);
		
		// -source surrounded by blocks
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, WATER_SOURCE, STONE, STONE, STONE, STONE, STONE, STONE);
		Assert.assertEquals(WATER_SOURCE, target);
		
		// -source without blocks
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, WATER_SOURCE, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR, ENV.special.AIR);
		Assert.assertEquals(WATER_SOURCE, target);
	}
}
