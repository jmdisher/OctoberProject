package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;


public class TestFacingDirection
{
	@Test
	public void subBlockNorth()
	{
		SubBlock zero = SubBlock.fromInt(0, 0, 0);
		SubBlock one = SubBlock.fromInt(1, 1, 1);
		SubBlock two = SubBlock.fromInt(2, 2, 2);
		SubBlock three = SubBlock.fromInt(3, 3, 3);
		
		SubBlock outZero = FacingDirection.NORTH.inverseRotateInSubBlock(zero);
		SubBlock outOne = FacingDirection.NORTH.inverseRotateInSubBlock(one);
		SubBlock outTwo = FacingDirection.NORTH.inverseRotateInSubBlock(two);
		SubBlock outThree = FacingDirection.NORTH.inverseRotateInSubBlock(three);
		
		Assert.assertEquals(zero, outZero);
		Assert.assertEquals(one, outOne);
		Assert.assertEquals(two, outTwo);
		Assert.assertEquals(three, outThree);
	}

	@Test
	public void subBlockWest()
	{
		SubBlock zero = SubBlock.fromInt(0, 0, 0);
		SubBlock one = SubBlock.fromInt(1, 1, 1);
		SubBlock two = SubBlock.fromInt(2, 2, 2);
		SubBlock three = SubBlock.fromInt(3, 3, 3);
		
		SubBlock outZero = FacingDirection.WEST.inverseRotateInSubBlock(zero);
		SubBlock outOne = FacingDirection.WEST.inverseRotateInSubBlock(one);
		SubBlock outTwo = FacingDirection.WEST.inverseRotateInSubBlock(two);
		SubBlock outThree = FacingDirection.WEST.inverseRotateInSubBlock(three);
		
		Assert.assertEquals(SubBlock.fromInt(0, 3, 0), outZero);
		Assert.assertEquals(SubBlock.fromInt(1, 2, 1), outOne);
		Assert.assertEquals(SubBlock.fromInt(2, 1, 2), outTwo);
		Assert.assertEquals(SubBlock.fromInt(3, 0, 3), outThree);
	}

	@Test
	public void subBlockSouth()
	{
		SubBlock zero = SubBlock.fromInt(0, 0, 0);
		SubBlock one = SubBlock.fromInt(1, 1, 1);
		SubBlock two = SubBlock.fromInt(2, 2, 2);
		SubBlock three = SubBlock.fromInt(3, 3, 3);
		
		SubBlock outZero = FacingDirection.SOUTH.inverseRotateInSubBlock(zero);
		SubBlock outOne = FacingDirection.SOUTH.inverseRotateInSubBlock(one);
		SubBlock outTwo = FacingDirection.SOUTH.inverseRotateInSubBlock(two);
		SubBlock outThree = FacingDirection.SOUTH.inverseRotateInSubBlock(three);
		
		Assert.assertEquals(SubBlock.fromInt(3, 3, 0), outZero);
		Assert.assertEquals(SubBlock.fromInt(2, 2, 1), outOne);
		Assert.assertEquals(SubBlock.fromInt(1, 1, 2), outTwo);
		Assert.assertEquals(SubBlock.fromInt(0, 0, 3), outThree);
	}

	@Test
	public void subBlockEast()
	{
		SubBlock zero = SubBlock.fromInt(0, 0, 0);
		SubBlock one = SubBlock.fromInt(1, 1, 1);
		SubBlock two = SubBlock.fromInt(2, 2, 2);
		SubBlock three = SubBlock.fromInt(3, 3, 3);
		
		SubBlock outZero = FacingDirection.EAST.inverseRotateInSubBlock(zero);
		SubBlock outOne = FacingDirection.EAST.inverseRotateInSubBlock(one);
		SubBlock outTwo = FacingDirection.EAST.inverseRotateInSubBlock(two);
		SubBlock outThree = FacingDirection.EAST.inverseRotateInSubBlock(three);
		
		Assert.assertEquals(SubBlock.fromInt(3, 0, 0), outZero);
		Assert.assertEquals(SubBlock.fromInt(2, 1, 1), outOne);
		Assert.assertEquals(SubBlock.fromInt(1, 2, 2), outTwo);
		Assert.assertEquals(SubBlock.fromInt(0, 3, 3), outThree);
	}

	@Test
	public void subBlockDown()
	{
		SubBlock zero = SubBlock.fromInt(0, 0, 0);
		SubBlock one = SubBlock.fromInt(1, 1, 1);
		SubBlock two = SubBlock.fromInt(2, 2, 2);
		SubBlock three = SubBlock.fromInt(3, 3, 3);
		
		SubBlock outZero = FacingDirection.DOWN.inverseRotateInSubBlock(zero);
		SubBlock outOne = FacingDirection.DOWN.inverseRotateInSubBlock(one);
		SubBlock outTwo = FacingDirection.DOWN.inverseRotateInSubBlock(two);
		SubBlock outThree = FacingDirection.DOWN.inverseRotateInSubBlock(three);
		
		Assert.assertEquals(SubBlock.fromInt(0, 3, 0), outZero);
		Assert.assertEquals(SubBlock.fromInt(1, 2, 1), outOne);
		Assert.assertEquals(SubBlock.fromInt(2, 1, 2), outTwo);
		Assert.assertEquals(SubBlock.fromInt(3, 0, 3), outThree);
	}
}
