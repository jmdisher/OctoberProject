package com.jeffdisher.october.logic;

import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.DropChance;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestMiscHelpers
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
	public void stackableDrops() throws Throwable
	{
		Item sapling = ENV.items.getItemById("op.sapling");
		Item stick = ENV.items.getItemById("op.stick");
		Item apple = ENV.items.getItemById("op.apple");
		
		DropChance[] chances = new DropChance[] {
			new DropChance(sapling, 100),
			new DropChance(stick, 50),
			new DropChance(sapling, 30),
			new DropChance(apple, 10),
		};
		
		ItemSlot[] slot0 = MiscHelpers.convertToDrops(ENV, 0, chances);
		ItemSlot[] slot20 = MiscHelpers.convertToDrops(ENV, 20, chances);
		ItemSlot[] slot50 = MiscHelpers.convertToDrops(ENV, 50, chances);
		ItemSlot[] slot99 = MiscHelpers.convertToDrops(ENV, 99, chances);
			
		Assert.assertEquals(3, slot0.length);
		Assert.assertEquals(sapling, slot0[1].getType());
		Assert.assertEquals(2, slot0[1].getCount());
		
		Assert.assertEquals(2, slot20.length);
		Assert.assertEquals(sapling, slot20[1].getType());
		Assert.assertEquals(2, slot20[1].getCount());
		
		Assert.assertEquals(1, slot50.length);
		Assert.assertEquals(1, slot99.length);
	}

	@Test
	public void nonstackableDrops() throws Throwable
	{
		Item bow = ENV.items.getItemById("op.bow");
		Item bucket = ENV.items.getItemById("op.bucket");
		
		DropChance[] chances = new DropChance[] {
			new DropChance(bow, 100),
			new DropChance(bucket, 50),
			new DropChance(bow, 30),
		};
		
		ItemSlot[] slot0 = MiscHelpers.convertToDrops(ENV, 0, chances);
		ItemSlot[] slot30 = MiscHelpers.convertToDrops(ENV, 30, chances);
		ItemSlot[] slot50 = MiscHelpers.convertToDrops(ENV, 50, chances);
		ItemSlot[] slot99 = MiscHelpers.convertToDrops(ENV, 99, chances);
			
		Assert.assertEquals(3, slot0.length);
		Assert.assertEquals(2, slot30.length);
		Assert.assertEquals(1, slot50.length);
		Assert.assertEquals(1, slot99.length);
	}

	@Test
	public void nearestBlock() throws Throwable
	{
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), stone);
		CuboidData mixedCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 0, 0), ENV.special.AIR);
		mixedCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 0), stone.item().number());
		mixedCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 31, 31), stone.item().number());
		
		Assert.assertEquals(new AbsoluteLocation(5, 6, -1), MiscHelpers.findClosestBlock(Set.of(airCuboid, stoneCuboid, mixedCuboid), new AbsoluteLocation(5, 6, 7), stone));
		Assert.assertEquals(new AbsoluteLocation(5, 6, 7), MiscHelpers.findClosestBlock(Set.of(airCuboid, stoneCuboid, mixedCuboid), new AbsoluteLocation(5, 6, 7), ENV.special.AIR));
		Assert.assertNull(MiscHelpers.findClosestBlock(Set.of(airCuboid), new AbsoluteLocation(5, 6, 7), stone));
		Assert.assertEquals(new AbsoluteLocation(31, 8, -1), MiscHelpers.findClosestBlock(Set.of(airCuboid, stoneCuboid, mixedCuboid), new AbsoluteLocation(37, 8, 9), stone));
		Assert.assertEquals(new AbsoluteLocation(32, 0, 0), MiscHelpers.findClosestBlock(Set.of(mixedCuboid), new AbsoluteLocation(37, 8, 9), stone));
	}
}
