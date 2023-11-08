package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.CuboidAddress;


public class TestWorldManager
{
	@Test
	public void basicStart()
	{
		int[] counters = new int[3];
		IReadOnlyCuboidData[] capture = new IReadOnlyCuboidData[1];
		WorldManager manager = new WorldManager(new WorldManager.ICuboidLifecycle() {
			@Override
			public void cuboidDataLoaded(IReadOnlyCuboidData data)
			{
				counters[0] += 1;
				Assert.assertNull(capture[0]);
				capture[0] = data;
			}
		}, new WorldManager.ICuboidLoader() {
			@Override
			public void loadCuboid(CuboidAddress address, Consumer<IReadOnlyCuboidData> loadSuccess, Consumer<CuboidAddress> notFound)
			{
				counters[1] += 1;
				notFound.accept(address);
			}
		}, new WorldManager.ICuboidGenerator() {
			@Override
			public void generateCuboid(CuboidAddress address, Consumer<IReadOnlyCuboidData> generationComplete)
			{
				counters[2] += 1;
				CuboidData data = CuboidData.createNew(address, new IOctree[0]);
				generationComplete.accept(data);
			}
		});
		manager.setCentre(new CuboidAddress((short)0, (short)0, (short)0));
		Assert.assertEquals(1, counters[0]);
		Assert.assertEquals(1, counters[1]);
		Assert.assertEquals(1, counters[1]);
		Assert.assertEquals(new CuboidAddress((short)0, (short)0, (short)0), capture[0].getCuboidAddress());
	}
}
