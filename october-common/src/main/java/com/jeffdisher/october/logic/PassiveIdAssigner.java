package com.jeffdisher.october.logic;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Just a wrapper over an atomic to assign passuve entity IDs.
 * PassiveEntity instances are tracked with an ID (int) like all other entities.  However, these IDs have no serialized
 * meaning and they are re-assigned every time the entity is loaded from disk.  This is to avoid keeping a long-lived
 * counter and dealing with the limits of the counter.
 * Since all PassiveEntity instances have a positive ID, they are limited to 2^31 instances.  Making this limit
 * per-session, as opposed to per-world, this limit is less of a theoretical concern.
 */
public class PassiveIdAssigner
{
	// We start this at 0 since we increment THEN get.
	private AtomicInteger _next = new AtomicInteger(0);

	public int next()
	{
		return _next.incrementAndGet();
	}
}
