package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;


public class Block
{
	private final Object[] _aspects;

	public Block(Object[] aspects)
	{
		_aspects = aspects;
	}

	public byte getData7(Aspect<Byte> type)
	{
		return (Byte)_aspects[type.index()];
	}

	public short getData15(Aspect<Short> type)
	{
		return (Short)_aspects[type.index()];
	}

	public <T> T getDataSpecial(Aspect<T> type)
	{
		return type.type().cast(_aspects[type.index()]);
	}
}
