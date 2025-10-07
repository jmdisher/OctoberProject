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
)
{
}
