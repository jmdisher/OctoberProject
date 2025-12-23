package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;


/**
 * An object which contains what is required to decode serialized data.
 * The main reason for this is to pass along information used to describe special version-specific notes used in
 * decoding (in case some low-level piece of data is encoded differently in older versions and that information needs to
 * be plumbed in to the actual point of reading/decoding).
 */
public record DeserializationContext(Environment env
	, ByteBuffer buffer
	, long currentGameMillis
	, boolean usePreV8NonStackableDecoding
	, boolean usePreV11DamageDecoding
)
{
	/**
	 * A helper to handle the common case of creating a context with no special options.  This is often used in tests or
	 * in places related to the network where there are no special options based on versioning.
	 * 
	 * @param env The environment.
	 * @param buffer The buffer to deserialize.
	 * @return A context with no special deserialization options.
	 */
	public static DeserializationContext empty(Environment env, ByteBuffer buffer)
	{
		return new DeserializationContext(env
			, buffer
			, 0L
			, false
			, false
		);
	}
}
