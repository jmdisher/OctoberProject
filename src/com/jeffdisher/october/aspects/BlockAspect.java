package com.jeffdisher.october.aspects;


public class BlockAspect
{
	public static final Aspect<Short> BLOCK = new Aspect<>("Block", 0, Short.class);

	public static final short AIR = 0;
	public static final short STONE = 1;
}
