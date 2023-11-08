package com.jeffdisher.october.data;


public interface IReadOnlyCuboidData
{
	short[] getCuboidAddress();
	byte getData7(Aspect<Byte> type, byte x, byte y, byte z);
	short getData15(Aspect<Short> type, byte x, byte y, byte z);
	<T> T getDataSpecial(Aspect<T> type, byte x, byte y, byte z);
}
