package com.jeffdisher.october.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.actions.IEntityActionFromClient;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A container used by client-side accumulators.  It contains the immutable parts of the client state so that they can
 * be updated independently of whichever accumulator is active.
 */
public class CommonClientWorldCache
{
	public final IProjectionListener listener;
	public final long millisPerTick;
	public final EntityVolume playerVolume;
	public final float playerBlocksPerSecond;

	public Entity thisEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _world;
	private final Map<Integer, PartialEntity> _otherEntities;
	private final Map<Integer, PartialPassive> _passives;
	private final Map<AbsoluteLocation, BlockProxy> _proxyCache;
	private final TickProcessingContext.IBlockFetcher _proxyLookup;
	public final ViscosityReader reader;

	public CommonClientWorldCache(Environment env
		, IProjectionListener listener
		, long millisPerTick
	)
	{
		this.listener = listener;
		this.millisPerTick = millisPerTick;
		this.playerVolume = env.creatures.PLAYER.volume();
		this.playerBlocksPerSecond = env.creatures.PLAYER.blocksPerSecond();
		
		_world = new HashMap<>();
		_otherEntities = new HashMap<>();
		_passives = new HashMap<>();
		
		_proxyCache = new HashMap<>();
		_proxyLookup = new TickProcessingContext.IBlockFetcher() {
			@Override
			public BlockProxy readBlock(AbsoluteLocation location)
			{
				return _readOneBlock(location);
			}
			@Override
			public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
			{
				// TODO:  Make this call a batching mechanism in the lower level.
				Map<AbsoluteLocation, BlockProxy> completed = new HashMap<>();
				for (AbsoluteLocation location : locations)
				{
					BlockProxy proxy = _readOneBlock(location);
					if (null != proxy)
					{
						completed.put(location, proxy);
					}
				}
				return completed;
			}
			private BlockProxy _readOneBlock(AbsoluteLocation location)
			{
				BlockProxy proxy = _proxyCache.get(location);
				if (null == proxy)
				{
					IReadOnlyCuboidData cuboid = _world.get(location.getCuboidAddress());
					if (null != cuboid)
					{
						proxy = BlockProxy.load(location.getBlockAddress(), cuboid);
						_proxyCache.put(location, proxy);
					}
				}
				return proxy;
			}
		};
		this.reader = new ViscosityReader(env, _proxyLookup);
	}

	/**
	 * Sets the underlying "known good" entity state for this entity.  Does not immediately impact accumulated state.
	 * 
	 * @param entity The new entity state.
	 */
	public void setThisEntity(Entity entity)
	{
		this.thisEntity = entity;
	}

	/**
	 * Sets or updates a cuboid in the local reference state.
	 * 
	 * @param cuboid The cuboid.
	 * @param changedBlocks The set of blocks which changed in this cuboid.
	 */
	public void setCuboid(IReadOnlyCuboidData cuboid, Set<BlockAddress> changedBlocks)
	{
		CuboidAddress address = cuboid.getCuboidAddress();
		_world.put(address, cuboid);
		
		// Invalidate any caching of the changed blocks.
		AbsoluteLocation base = address.getBase();
		for (BlockAddress block: changedBlocks)
		{
			AbsoluteLocation loc = base.relativeForBlock(block);
			_proxyCache.remove(loc);
		}
	}

	/**
	 * Removes the cuboid with the given address from local reference state.
	 * 
	 * @param address The address of the cuboid to remove.
	 */
	public void removeCuboid(CuboidAddress address)
	{
		Assert.assertTrue(null != _world.remove(address));
		
		// Clean up the proxy cache.
		Iterator<AbsoluteLocation> iter = _proxyCache.keySet().iterator();
		while (iter.hasNext())
		{
			AbsoluteLocation location = iter.next();
			if (address.equals(location.getCuboidAddress()))
			{
				iter.remove();
			}
		}
	}

	/**
	 * Sets or updates another entity in the local reference state.
	 * 
	 * @param entity The entity to store.
	 */
	public void setOtherEntity(PartialEntity entity)
	{
		int id = entity.id();
		_otherEntities.put(id, entity);
	}

	/**
	 * Removes another entity from the local reference state.
	 * 
	 * @param id The ID of the entity to remove.
	 */
	public void removeOtherEntity(int id)
	{
		Assert.assertTrue(null != _otherEntities.remove(id));
	}

	/**
	 * Adds a passive entity to the accumulator.
	 * 
	 * @param passive The new passive.
	 */
	public void addPassive(PartialPassive passive)
	{
		Object old = _passives.put(passive.id(), passive);
		Assert.assertTrue(null == old);
	}

	/**
	 * Updates a passive already known to the accumulator.
	 * 
	 * @param entity The updated passive state.
	 */
	public void updatePassive(PartialPassive entity)
	{
		PartialPassive old = _passives.get(entity.id());
		Assert.assertTrue(null != old);
		_passives.put(entity.id(), entity);
	}

	/**
	 * Removes an already-known passive from the accumulator.
	 * 
	 * @param entityId The ID of the passive entity to remove.
	 */
	public void removePassive(int entityId)
	{
		Assert.assertTrue(null != _passives.remove(entityId));
	}

	/**
	 * Used to test the action toRun against the receiver's current state (in a read-only way) in order to checks its
	 * validity.
	 * Returns null on failure, thisEntity if no meaningful change to the local entity, or the new entity instance.
	 * 
	 * @param toRun The action to run.
	 * @param millisToApply The millis to apply as the millis per tick.
	 * @param currentTimeMillis The current time to use for the invocation.
	 * @return The changed Entity, thisEntity if unchanged or null if the execution was an error.
	 */
	public Entity localEntityAfterAction(IEntityActionFromClient<IMutablePlayerEntity> toRun, long millisToApply, long currentTimeMillis)
	{
		OneOffRunner.InputState input = new OneOffRunner.InputState(this.thisEntity
			, _world
			, _otherEntities
			, _passives
			, _proxyLookup
		);
		TickProcessingContext.IEventSink eventSink = (EventRecord event) -> {
			// We can probably ignore events in this path since they will either be entity-related (hence sent by the
			// server when it determines things like damage, etc) or were world-related and sent at the beginning of the
			// action tick in the other path.
		};
		OneOffRunner.OutputState output = OneOffRunner.runOneChange(input, eventSink, millisToApply, currentTimeMillis, toRun);
		Entity toReturn;
		if (null != output)
		{
			// This was a success so return either the changed entity or default to the original.
			Entity possible = output.thisEntity();
			if (null != possible)
			{
				// Note that we will further ignore this change if it didn't cause a change to location or velocity, unless it also has a sub-action (since they can change other things).
				if ((null != toRun.getSubAction())
					|| !this.thisEntity.location().equals(possible.location())
					|| !this.thisEntity.velocity().equals(possible.velocity())
					|| (this.thisEntity.yaw() != possible.yaw())
					|| (this.thisEntity.pitch() != possible.pitch())
				)
				{
					toReturn = possible;
				}
				else
				{
					// Nothing meaningful changed so return the original instance.
					toReturn = this.thisEntity;
				}
			}
			else
			{
				// Nothing changed so return the original instance.
				toReturn = this.thisEntity;
			}
		}
		else
		{
			// There was a failure so return null.
			toReturn = null;
		}
		return toReturn;
	}
}
