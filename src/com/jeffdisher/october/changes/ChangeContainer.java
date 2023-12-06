package com.jeffdisher.october.changes;


/**
 * The IEntityChange is intended for a specific entity but carrying around that entity ID is redundant and only required
 * in a few places so this container is used in those cases.
 */
public record ChangeContainer(int entityId, IEntityChange change)
{}
