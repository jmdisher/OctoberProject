package com.jeffdisher.october.utils;


public class Assert
{
	public static void assertTrue(boolean flag)
	{
		if (!flag)
		{
			throw new AssertionError("Condition expected to be true");
		}
	}

	public static AssertionError unreachable()
	{
		throw new AssertionError("Code path unreachable");
	}

	public static AssertionError unexpected(Throwable t)
	{
		throw new AssertionError("Unexpected exception", t);
	}
}
