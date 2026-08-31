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

	@Test
	public void subBlockSouthRotation()
	{
		// Select the 8 vertices of of the cube and show that they rotate onto each other as expected.
		SubBlock zzz = SubBlock.fromInt(0, 0, 0);
		SubBlock zzp = SubBlock.fromInt(0, 0, 3);
		SubBlock zpz = SubBlock.fromInt(0, 3, 0);
		SubBlock zpp = SubBlock.fromInt(0, 3, 3);
		SubBlock pzz = SubBlock.fromInt(3, 0, 0);
		SubBlock pzp = SubBlock.fromInt(3, 0, 3);
		SubBlock ppz = SubBlock.fromInt(3, 3, 0);
		SubBlock ppp = SubBlock.fromInt(3, 3, 3);
		
		Assert.assertEquals(ppz, FacingDirection.SOUTH.inverseRotateInSubBlock(zzz));
		Assert.assertEquals(ppp, FacingDirection.SOUTH.inverseRotateInSubBlock(zzp));
		Assert.assertEquals(pzz, FacingDirection.SOUTH.inverseRotateInSubBlock(zpz));
		Assert.assertEquals(pzp, FacingDirection.SOUTH.inverseRotateInSubBlock(zpp));
		Assert.assertEquals(zpz, FacingDirection.SOUTH.inverseRotateInSubBlock(pzz));
		Assert.assertEquals(zpp, FacingDirection.SOUTH.inverseRotateInSubBlock(pzp));
		Assert.assertEquals(zzz, FacingDirection.SOUTH.inverseRotateInSubBlock(ppz));
		Assert.assertEquals(zzp, FacingDirection.SOUTH.inverseRotateInSubBlock(ppp));
	}

	@Test
	public void subBlockFlippedWestRotation()
	{
		// Select the 8 vertices of of the cube and show that they rotate onto each other as expected.
		SubBlock zzz = SubBlock.fromInt(0, 0, 0);
		SubBlock zzp = SubBlock.fromInt(0, 0, 3);
		SubBlock zpz = SubBlock.fromInt(0, 3, 0);
		SubBlock zpp = SubBlock.fromInt(0, 3, 3);
		SubBlock pzz = SubBlock.fromInt(3, 0, 0);
		SubBlock pzp = SubBlock.fromInt(3, 0, 3);
		SubBlock ppz = SubBlock.fromInt(3, 3, 0);
		SubBlock ppp = SubBlock.fromInt(3, 3, 3);
		
		// Rotate AbsoluteLocation.
		AbsoluteLocation location = new AbsoluteLocation(1, 2, 3);
		Assert.assertEquals(new AbsoluteLocation(-2, 1, 3), FacingDirection.WEST.rotateAboutZ(location));
		Assert.assertEquals(new AbsoluteLocation(-2, 1, -3), FacingDirection.FLIPPED_WEST.rotateAboutZ(location));
		
		// Rotate triplet.
		float[] triplet = new float[] {1.0f, 2.0f, 3.0f};
		Assert.assertArrayEquals(new float[] {-2.0f, 1.0f, 3.0f }, FacingDirection.WEST.rotateTripletAboutZ(triplet), 0.01f);
		Assert.assertArrayEquals(new float[] {-2.0f, 1.0f, -3.0f }, FacingDirection.FLIPPED_WEST.rotateTripletAboutZ(triplet), 0.01f);
		
		// Output location - flip has no impact.
		AbsoluteLocation blockLocation = new AbsoluteLocation(1, 2, 3);
		Assert.assertEquals(new AbsoluteLocation(0, 2, 3), FacingDirection.WEST.getOutputBlockLocation(blockLocation));
		Assert.assertEquals(new AbsoluteLocation(0, 2, 3), FacingDirection.FLIPPED_WEST.getOutputBlockLocation(blockLocation));
		
		// Orientation.
		Assert.assertEquals(FacingDirection.SOUTH, FacingDirection.WEST.rotateOrientation(FacingDirection.WEST));
		Assert.assertEquals(FacingDirection.SOUTH, FacingDirection.FLIPPED_WEST.rotateOrientation(FacingDirection.WEST));
		
		// Show the basic west orientation, to start.
		Assert.assertEquals(zpz, FacingDirection.WEST.inverseRotateInSubBlock(zzz));
		Assert.assertEquals(zpp, FacingDirection.WEST.inverseRotateInSubBlock(zzp));
		Assert.assertEquals(ppz, FacingDirection.WEST.inverseRotateInSubBlock(zpz));
		Assert.assertEquals(ppp, FacingDirection.WEST.inverseRotateInSubBlock(zpp));
		Assert.assertEquals(zzz, FacingDirection.WEST.inverseRotateInSubBlock(pzz));
		Assert.assertEquals(zzp, FacingDirection.WEST.inverseRotateInSubBlock(pzp));
		Assert.assertEquals(pzz, FacingDirection.WEST.inverseRotateInSubBlock(ppz));
		Assert.assertEquals(pzp, FacingDirection.WEST.inverseRotateInSubBlock(ppp));
		
		// The "flipped" west orientation rotates to the west and then flips the Z-axis so it will be similar.
		Assert.assertEquals(zpp, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(zzz));
		Assert.assertEquals(zpz, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(zzp));
		Assert.assertEquals(ppp, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(zpz));
		Assert.assertEquals(ppz, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(zpp));
		Assert.assertEquals(zzp, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(pzz));
		Assert.assertEquals(zzz, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(pzp));
		Assert.assertEquals(pzp, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(ppz));
		Assert.assertEquals(pzz, FacingDirection.FLIPPED_WEST.inverseRotateInSubBlock(ppp));
	}
}
