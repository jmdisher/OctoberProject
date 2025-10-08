package com.jeffdisher.october.types;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;


/**
 * This type is used to describe PassiveEntity instances.
 * TODO:  Move this into a data file, later, but it will start as static.
 */
public record PassiveType(byte number
	, EntityVolume volume
	, IExtendedCodec extendedCodec
)
{
	/**
	 * Item slot passive entities will despawn after 5 minutes.
	 */
	public static final long ITEM_SLOT_DESPAWN_MILLIS = 5L * 60L * 1000L;

	public static final PassiveType ITEM_SLOT = new PassiveType((byte)1
		, new EntityVolume(0.1f, 0.1f)
		, new IExtendedCodec() {
			@Override
			public Object read(DeserializationContext context)
			{
				return CodecHelpers.readSlot(context);
			}
			@Override
			public void write(ByteBuffer buffer, Object extendedData)
			{
				CodecHelpers.writeSlot(buffer, (ItemSlot)extendedData);
			}
		}
	);


	/**
	 * The interface which defines PassiveEntity.extendedData.
	 */
	public static interface IExtendedCodec
	{
		public Object read(DeserializationContext context);
		public void write(ByteBuffer buffer, Object extendedData);
	}
}
