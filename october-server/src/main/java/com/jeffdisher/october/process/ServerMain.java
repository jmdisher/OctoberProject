package com.jeffdisher.october.process;

import java.io.IOException;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.persistence.CuboidLoader;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class ServerMain
{
	public static void main(String[] args)
	{
		// For now, we will only assume 1 argument:  port number.
		if (1 == args.length)
		{
			int port = Integer.parseInt(args[0]);
			System.out.println("Starting server on port " + port);
			try
			{
				CuboidLoader cuboidLoader = new CuboidLoader();
				_populateDemoWorld(cuboidLoader);
				ServerProcess process = new ServerProcess(port, ServerRunner.DEFAULT_MILLIS_PER_TICK, cuboidLoader, () -> System.currentTimeMillis());
				// We will just wait for input before shutting down.
				System.in.read();
				System.out.println("Shutting down...");
				process.stop();
				System.out.println("Exiting normally");
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else
		{
			System.err.println("Usage:  ServerMain PORT");
			System.exit(1);
		}
	}


	private static void _populateDemoWorld(CuboidLoader cuboidLoader)
	{
		// Since the location is standing in 0.0, we need to load at least the 8 cuboids around the origin.
		// Note that we want them to stand on the ground so we will fill the bottom 4 with stone and the top 4 with air.
		// (in order to better test inventory and crafting interfaces, we will drop a bunch of items on the ground where we start).
		CuboidData cuboid000 = _generateColumnCuboid(new CuboidAddress((short)0, (short)0, (short)0));
		Inventory starting = Inventory.start(InventoryAspect.CAPACITY_AIR)
				.add(ItemRegistry.STONE, 1)
				.add(ItemRegistry.LOG, 1)
				.add(ItemRegistry.PLANK, 1)
				.finish();
		cuboid000.setDataSpecial(AspectRegistry.INVENTORY, new BlockAddress((byte)0, (byte)0, (byte)0), starting);
		cuboidLoader.preload(cuboid000);
		cuboidLoader.preload(_generateColumnCuboid(new CuboidAddress((short)0, (short)-1, (short)0)));
		cuboidLoader.preload(_generateColumnCuboid(new CuboidAddress((short)-1, (short)-1, (short)0)));
		cuboidLoader.preload(_generateColumnCuboid(new CuboidAddress((short)-1, (short)0, (short)0)));
		
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)-1), ItemRegistry.STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)-1, (short)-1), ItemRegistry.STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)-1), ItemRegistry.STONE));
	}

	private static CuboidData _generateColumnCuboid(CuboidAddress address)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		
		// Create some columns.
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte) 1, (byte) 1, (byte)0), ItemRegistry.STONE.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte) 1, (byte)30, (byte)0), ItemRegistry.STONE.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)30, (byte)30, (byte)0), ItemRegistry.STONE.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)30, (byte) 1, (byte)0), ItemRegistry.STONE.number());
		
		return cuboid;
	}
}
