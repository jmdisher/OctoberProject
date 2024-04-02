package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySelectItem;
import com.jeffdisher.october.mutations.MutationEntityStoreToInventory;
import com.jeffdisher.october.mutations.MutationEntityType;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.utils.Assert;


public class MutationEntityCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IMutationEntity>[] _CODEC_TABLE = new Function[MutationEntityType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[EntityChangeMove.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeMove.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeJump.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeJump.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationPlaceSelectedBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationPlaceSelectedBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeCraft.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeCraft.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntitySelectItem.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntitySelectItem.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityPushItems.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityPushItems.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityRequestItemPickUp.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityRequestItemPickUp.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityStoreToInventory.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityStoreToInventory.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeIncrementalBlockBreak.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeIncrementalBlockBreak.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeCraftInBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeCraftInBlock.deserializeFromBuffer(buffer);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IMutationEntity parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IMutationEntity parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			MutationEntityType type = MutationEntityType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
		}
		return parsed;
	}

	public static void serializeToBuffer(ByteBuffer buffer, IMutationEntity entity)
	{
		// Write the type.
		MutationEntityType type = entity.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		entity.serializeToBuffer(buffer);
	}
}
