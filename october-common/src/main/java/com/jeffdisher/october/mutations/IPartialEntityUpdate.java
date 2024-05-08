package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.MutablePartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is a partial entity state update sent from server to client.  This can be a the entire PartialEntity or just
 * the changes, applied in some special way.
 */
public interface IPartialEntityUpdate
{
	/**
	 * Applies the receiver to the given newEntity.
	 * 
	 * @param context Only provided for some testing resources - not generally useful to these objects.
	 * @param newEntity The partial entity which should be updated by the receiver.
	 */
	void applyToEntity(TickProcessingContext context, MutablePartialEntity newEntity);

	/**
	 * @return The type for serializing the entity over the network.
	 */
	PartialEntityUpdateType getType();

	/**
	 * Called to serialize the update into the given buffer for network transmission.
	 * 
	 * @param buffer The network buffer where the update should be written.
	 */
	void serializeToNetworkBuffer(ByteBuffer buffer);
}
