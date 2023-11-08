package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * The world manager decides what cuboids should be loaded/unloaded and schedules their background load/store/generation.
 */
public class WorldManager
{
	// Will load a cubic selection of cuboids with a radius this far from the centre.
	// Note that "0" means just the cuboid, itself, so "1" would mean a cube of edge size 3.
	public static final int CUBE_RADIUS = 0;

	private final ICuboidLifecycle _lifecycle;
	private final ICuboidLoader _loader;
	private final ICuboidGenerator _generator;

	private final LoadSuccess _loadSuccess;
	private final NotFound _notFound;
	private final GenerationComplete _generationComplete;

	private CuboidAddress _centre;

	public WorldManager(ICuboidLifecycle lifecycle, ICuboidLoader loader, ICuboidGenerator generator)
	{
		_lifecycle = lifecycle;
		_loader = loader;
		_generator = generator;
		
		_loadSuccess = new LoadSuccess();
		_notFound = new NotFound();
		_generationComplete = new GenerationComplete();
	}

	public void setCentre(CuboidAddress centre)
	{
		// TODO:  Make this more versatile once we expand the radius.
		if (null != _centre)
		{
			// TODO:  Implement
			Assert.unreachable();
			// We will need to compare these to see what should be unloaded/loaded.
			if (_centre.equals(centre))
			{
				// This is a degenerate case so do nothing.
			}
			else
			{
				// Unload the one cuboid and load the other.
			}
		}
		else
		{
			// This is the start-up case so we just load the cuboid.
			_loader.loadCuboid(centre, _loadSuccess, _notFound);
		}
	}


	public class LoadSuccess implements Consumer<IReadOnlyCuboidData>
	{
		@Override
		public void accept(IReadOnlyCuboidData arg0)
		{
			// For now, we just pass this in.
			_lifecycle.cuboidDataLoaded(arg0);
		}
	}


	public class NotFound implements Consumer<CuboidAddress>
	{
		@Override
		public void accept(CuboidAddress arg0)
		{
			// For now, just request that this be generated.
			_generator.generateCuboid(arg0, _generationComplete);
		}
	}


	public class GenerationComplete implements Consumer<IReadOnlyCuboidData>
	{
		@Override
		public void accept(IReadOnlyCuboidData arg0)
		{
			_lifecycle.cuboidDataLoaded(arg0);
		}
	}


	public static interface ICuboidLifecycle
	{
		void cuboidDataLoaded(IReadOnlyCuboidData data);
	}


	public static interface ICuboidLoader
	{
		void loadCuboid(CuboidAddress address, Consumer<IReadOnlyCuboidData> loadSuccess, Consumer<CuboidAddress> notFound);
	}


	public static interface ICuboidGenerator
	{
		void generateCuboid(CuboidAddress address, Consumer<IReadOnlyCuboidData> generationComplete);
	}
}
