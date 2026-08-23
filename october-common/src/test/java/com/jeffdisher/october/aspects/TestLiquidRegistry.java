package com.jeffdisher.october.aspects;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Pair;


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
	public static void setup() throws Throwable
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
		LiquidRegistry.LiquidBlock waterSource = ENV.liquids.test_liquidBlock(WATER_SOURCE, 0);
		LiquidRegistry.LiquidBlock lavaSource = ENV.liquids.test_liquidBlock(LAVA_SOURCE, 0);
		Pair<Block, LiquidRegistry.LiquidBlock> target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, waterSource, waterSource, null, null, null, STONE);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(0, target.two().distance());
		
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, lavaSource, lavaSource, null, null, null, STONE);
		Assert.assertEquals(LAVA_SOURCE, target.two().sourceType());
		Assert.assertEquals(1, target.two().distance());
	}

	@Test
	public void solidification() throws Throwable
	{
		// Convert water.
		LiquidRegistry.LiquidBlock waterSource = ENV.liquids.test_liquidBlock(WATER_SOURCE, 0);
		LiquidRegistry.LiquidBlock lavaWeak = ENV.liquids.test_liquidBlock(LAVA_SOURCE, 2);
		LiquidRegistry.LiquidBlock lavaStrong = ENV.liquids.test_liquidBlock(LAVA_SOURCE, 0);
		LiquidRegistry.LiquidBlock waterWeak = ENV.liquids.test_liquidBlock(WATER_SOURCE, 2);
		Pair<Block, LiquidRegistry.LiquidBlock> target = ENV.liquids.chooseEmptyLiquidBlock(ENV, waterSource, null, null, null, null, lavaWeak, STONE);
		Assert.assertEquals(STONE, target.one());
		
		// Convert lava.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, lavaStrong, null, null, null, null, waterWeak, STONE);
		Assert.assertEquals(BASALT, target.one());
	}

	@Test
	public void fallingWater() throws Throwable
	{
		// We show what happens in different falling water scenarios.
		LiquidRegistry.LiquidBlock waterSource = ENV.liquids.test_liquidBlock(WATER_SOURCE, 0);
		LiquidRegistry.LiquidBlock waterStrong = ENV.liquids.test_liquidBlock(WATER_SOURCE, 1);
		LiquidRegistry.LiquidBlock waterWeak = ENV.liquids.test_liquidBlock(WATER_SOURCE, 2);
		
		// Under a water source.
		Pair<Block, LiquidRegistry.LiquidBlock> target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, null, null, null, null, waterSource, null);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(2, target.two().distance());
		
		// Under water strong.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, null, null, null, null, waterStrong, null);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(2, target.two().distance());
		
		// Under water weak.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, null, null, null, null, waterWeak, null);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(2, target.two().distance());
		
		// Next to water source.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, null, null, null, waterSource, null, null);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(2, target.two().distance());
		
		// Next to water strong.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, null, null, null, waterStrong, null, null);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(2, target.two().distance());
	}

	@Test
	public void queries() throws Throwable
	{
		// Check the millisecond delays on flows (based on known constants in the config).
		Assert.assertEquals(100L, ENV.liquids.flowDelayMillis(WATER_SOURCE));
		Assert.assertEquals(100L, ENV.liquids.flowDelayMillis(WATER_STRONG));
		Assert.assertEquals(100L, ENV.liquids.flowDelayMillis(WATER_WEAK));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(LAVA_SOURCE));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(LAVA_STRONG));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(LAVA_WEAK));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(ENV.special.AIR));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(STONE));
		Assert.assertEquals(1000L, ENV.liquids.flowDelayMillis(BASALT));
		Assert.assertEquals(100L, ENV.liquids.minFlowDelayMillis(WATER_SOURCE, BASALT));
	}

	@Test
	public void reflowOnUpdate() throws Throwable
	{
		// Show what happens when water flows over lava.
		LiquidRegistry.LiquidBlock waterSource = ENV.liquids.test_liquidBlock(WATER_SOURCE, 0);
		LiquidRegistry.LiquidBlock waterWeak = ENV.liquids.test_liquidBlock(WATER_SOURCE, 2);
		LiquidRegistry.LiquidBlock lavaWeak = ENV.liquids.test_liquidBlock(LAVA_SOURCE, 2);
		
		// -we should flow weak over a lava flow.
		Pair<Block, LiquidRegistry.LiquidBlock> target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, waterSource, null, null, null, null, null);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(2, target.two().distance());
		
		// -the lava should update to basalt.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, lavaWeak, null, null, null, null, waterWeak, null);
		Assert.assertEquals(BASALT, target.one());
		
		// -the weak flow above should now be strong.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, waterWeak, waterSource, null, null, null, null, BASALT);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(1, target.two().distance());
	}

	@Test
	public void standalone() throws Throwable
	{
		// We want to see what happens when we update a single location with no sources around it.
		LiquidRegistry.LiquidBlock waterSource = ENV.liquids.test_liquidBlock(WATER_SOURCE, 0);
		LiquidRegistry.LiquidBlock waterWeak = ENV.liquids.test_liquidBlock(WATER_SOURCE, 2);
		
		// -weak flow surrounded by blocks
		Pair<Block, LiquidRegistry.LiquidBlock> target = ENV.liquids.chooseEmptyLiquidBlock(ENV, waterWeak, null, null, null, null, null, STONE);
		Assert.assertEquals(ENV.special.AIR, target.one());
		
		// -weak flow with no blocks around
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, waterWeak, null, null, null, null, null, null);
		Assert.assertEquals(ENV.special.AIR, target.one());
		
		// -source surrounded by blocks
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, waterSource, null, null, null, null, null, STONE);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(0, target.two().distance());
		
		// -source without blocks
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, waterSource, null, null, null, null, null, null);
		Assert.assertEquals(WATER_SOURCE, target.two().sourceType());
		Assert.assertEquals(0, target.two().distance());
	}

	@Test
	public void solidByAdjacentOnly() throws Throwable
	{
		// Show that we will convert a new liquid flowing into a block if adjacent blocks conflict.
		LiquidRegistry.LiquidBlock waterStrong = ENV.liquids.test_liquidBlock(WATER_SOURCE, 1);
		LiquidRegistry.LiquidBlock waterWeak = ENV.liquids.test_liquidBlock(WATER_SOURCE, 2);
		LiquidRegistry.LiquidBlock lavaStrong = ENV.liquids.test_liquidBlock(LAVA_SOURCE, 1);
		LiquidRegistry.LiquidBlock lavaWeak = ENV.liquids.test_liquidBlock(LAVA_SOURCE, 2);
		
		// Convert water.
		Pair<Block, LiquidRegistry.LiquidBlock> target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, null, lavaWeak, null, waterStrong, null, STONE);
		Assert.assertEquals(STONE, target.one());
		
		// Convert lava.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, null, null, waterWeak, null, lavaStrong, null, STONE);
		Assert.assertEquals(BASALT, target.one());
		
		// Convert water late.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, waterWeak, null, lavaWeak, null, waterStrong, null, STONE);
		Assert.assertEquals(STONE, target.one());
		
		// Convert lava late.
		target = ENV.liquids.chooseEmptyLiquidBlock(ENV, lavaWeak, null, waterWeak, null, lavaStrong, null, STONE);
		Assert.assertEquals(BASALT, target.one());
	}
}
