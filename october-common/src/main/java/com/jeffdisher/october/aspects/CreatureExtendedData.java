package com.jeffdisher.october.aspects;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the definitions of creature extended data and associated data.
 * Note that, for historical reasons (storage version 9), all codecs need to handle reading a 1-byte 0x0 value as "null"
 * and then interpreting that as some useful default.
 */
public class CreatureExtendedData
{
	/**
	 * This codec only ever stores null data.
	 */
	public static class NullCodec implements EntityType.IExtendedCodec
	{
		@Override
		public Object buildDefault()
		{
			return null;
		}
		@Override
		public Object read(ByteBuffer buffer, long gameTimeMillis)
		{
			byte header = buffer.get();
			Assert.assertTrue((byte)0 == header);
			return null;
		}
		@Override
		public void write(ByteBuffer buffer, Object extendedData, long gameTimeMillis)
		{
			Assert.assertTrue(null == extendedData);
			byte header = 0;
			buffer.put(header);
		}
	};

	/**
	 * This codec stores information associated with livestock.
	 */
	public static class LivestockCodec implements EntityType.IExtendedCodec
	{
		@Override
		public Object buildDefault()
		{
			return _buildDefault();
		}
		@Override
		public Object read(ByteBuffer buffer, long gameTimeMillis)
		{
			// If the header is 0, this is default, otherwise we will read the fields of LivestockData.
			Object result;
			byte header = buffer.get();
			if ((byte)0 == header)
			{
				// Just use the default structure.
				result = _buildDefault();
			}
			else
			{
				Assert.assertTrue((byte)1 == header);
				
				// All this data was added in storage version 10.
				boolean inLoveMode = CodecHelpers.readBoolean(buffer);
				EntityLocation offspringLocation = CodecHelpers.readNullableEntityLocation(buffer);
				result = new LivestockData(inLoveMode
					, offspringLocation
				);
			}
			return result;
		}
		@Override
		public void write(ByteBuffer buffer, Object extendedData, long gameTimeMillis)
		{
			// We never store the null value (it is mostly just a hold-over from storage version 9) so write a 1 byte.
			byte header = 1;
			buffer.put(header);
			
			// Now, write everything else.
			LivestockData safe = (LivestockData) extendedData;
			CodecHelpers.writeBoolean(buffer, safe.inLoveMode);
			CodecHelpers.writeNullableEntityLocation(buffer, safe.offspringLocation);
		}
		private Object _buildDefault()
		{
			return new LivestockData(false
				, null
			);
		}
	};


	public static record LivestockData(
		// True if this is a breedable creature which should now search for a partner.
		boolean inLoveMode
		// Non-null if this is a breedable creature who is ready to spawn offspring.
		, EntityLocation offspringLocation
	) {}
}
