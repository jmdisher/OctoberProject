package com.jeffdisher.october.aspects;


/**
 * Contains constants associated with the block aspect.
 * Note that technically, these constants are used for all "items", not just those which can be placed in the world as
 * blocks.  The constants are defined here, however, since it is the block aspect which constrains them and the blocks
 * are a strict subset of the items.
 */
public class BlockAspect
{
	public static final short AIR = 0;
	public static final short STONE = 1;
	public static final short LOG = 2;
	public static final short PLANK = 3;
	public static final short STONE_BRICK = 4;
	public static final short CRAFTING_TABLE = 5;
	public static final short FURNACE = 6;
	public static final short CHARCOAL = 7;

	public static final short TOTAL_BLOCK_TYPES = 8;
}
