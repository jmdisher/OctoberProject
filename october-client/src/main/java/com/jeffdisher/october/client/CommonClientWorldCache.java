package com.jeffdisher.october.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityVolume;
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
	public final Environment env;
	public final IProjectionListener listener;
	public final long millisPerTick;
	public final EntityVolume playerVolume;

	public Entity thisEntity;
	public final Map<CuboidAddress, IReadOnlyCuboidData> world;
	public final Map<Integer, PartialEntity> otherEntities;
	public final Map<Integer, PartialPassive> passives;
	public final Map<AbsoluteLocation, BlockProxy> proxyCache;
	public final TickProcessingContext.IBlockFetcher proxyLookup;
	public final ViscosityReader reader;

	public CommonClientWorldCache(Environment env
		, IProjectionListener listener
		, long millisPerTick
	)
	{
		this.env = env;
		this.listener = listener;
		this.millisPerTick = millisPerTick;
		this.playerVolume = env.creatures.PLAYER.volume();
		
		this.world = new HashMap<>();
		this.otherEntities = new HashMap<>();
		this.passives = new HashMap<>();
		
		this.proxyCache = new HashMap<>();
		this.proxyLookup = new TickProcessingContext.IBlockFetcher() {
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
				BlockProxy proxy = CommonClientWorldCache.this.proxyCache.get(location);
				if (null == proxy)
				{
					IReadOnlyCuboidData cuboid = CommonClientWorldCache.this.world.get(location.getCuboidAddress());
					if (null != cuboid)
					{
						proxy = BlockProxy.load(location.getBlockAddress(), cuboid);
						CommonClientWorldCache.this.proxyCache.put(location, proxy);
					}
				}
				return proxy;
			}
		};
		this.reader = new ViscosityReader(this.env, this.proxyLookup);
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
		this.world.put(address, cuboid);
		
		// Invalidate any caching of the changed blocks.
		AbsoluteLocation base = address.getBase();
		for (BlockAddress block: changedBlocks)
		{
			AbsoluteLocation loc = base.relativeForBlock(block);
			this.proxyCache.remove(loc);
		}
	}

	/**
	 * Removes the cuboid with the given address from local reference state.
	 * 
	 * @param address The address of the cuboid to remove.
	 */
	public void removeCuboid(CuboidAddress address)
	{
		Assert.assertTrue(null != this.world.remove(address));
		
		// Clean up the proxy cache.
		Iterator<AbsoluteLocation> iter = this.proxyCache.keySet().iterator();
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
		this.otherEntities.put(id, entity);
	}

	/**
	 * Removes another entity from the local reference state.
	 * 
	 * @param id The ID of the entity to remove.
	 */
	public void removeOtherEntity(int id)
	{
		Assert.assertTrue(null != this.otherEntities.remove(id));
	}

	/**
	 * Adds a passive entity to the accumulator.
	 * 
	 * @param passive The new passive.
	 */
	public void addPassive(PartialPassive passive)
	{
		Object old = this.passives.put(passive.id(), passive);
		Assert.assertTrue(null == old);
	}

	/**
	 * Updates a passive already known to the accumulator.
	 * 
	 * @param entity The updated passive state.
	 */
	public void updatePassive(PartialPassive entity)
	{
		PartialPassive old = this.passives.get(entity.id());
		Assert.assertTrue(null != old);
		this.passives.put(entity.id(), entity);
	}

	/**
	 * Removes an already-known passive from the accumulator.
	 * 
	 * @param entityId The ID of the passive entity to remove.
	 */
	public void removePassive(int entityId)
	{
		Assert.assertTrue(null != this.passives.remove(entityId));
	}
}
