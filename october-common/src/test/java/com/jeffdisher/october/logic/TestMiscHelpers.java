package com.jeffdisher.october.logic;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.DropChance;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;


public class TestMiscHelpers
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
}
