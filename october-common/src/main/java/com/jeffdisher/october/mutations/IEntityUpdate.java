package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is an entity state update sent from server to client.  This can be a whole entity, partial idempotent updates
 * to an existing entity, or potentially even a partial update filtered for visibility between different clients.
 */
public interface IEntityUpdate
{
	/**
	 * Applies the receiver to the given newEntity.
	 * 
	 * @param context Only provided for some testing resources - not generally useful to these objects.
	 * @param newEntity The entity which should be updated by the receiver.
	 */
	void applyToEntity(TickProcessingContext context, MutableEntity newEntity);

	/**
	 * @return The type for serializing the entity over the network.
	 */
	EntityUpdateType getType();

	/**
	 * Called to serialize the update into the given buffer for network transmission.
	 * 
	 * @param buffer The network buffer where the update should be written.
	 */
	void serializeToNetworkBuffer(ByteBuffer buffer);
}
