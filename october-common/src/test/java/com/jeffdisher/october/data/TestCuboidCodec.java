package com.jeffdisher.october.data;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;


public class TestCuboidCodec
{
	@Test
	public void empty()
	{
		BlockAddress testAddress = new BlockAddress((byte)0, (byte)0, (byte)0);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		OctreeShort blockData = OctreeShort.create((short)0);
		OctreeObject inventoryData = OctreeObject.create();
		CuboidData input = CuboidData.createNew(cuboidAddress, new IOctree[] { blockData, inventoryData });
		
		CuboidData output = _codec(input);
		Assert.assertEquals((short) 0, output.getData15(AspectRegistry.BLOCK, testAddress));
		Assert.assertNull(output.getDataSpecial(AspectRegistry.INVENTORY, testAddress));
	}

	@Test
	public void simple()
	{
		BlockAddress testAddress = new BlockAddress((byte)0, (byte)0, (byte)0);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		OctreeShort blockData = OctreeShort.create((short)0);
		OctreeObject inventoryData = OctreeObject.create();
		CuboidData input = CuboidData.createNew(cuboidAddress, new IOctree[] { blockData, inventoryData });
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).add(ItemRegistry.STONE, 2).finish());
		
		CuboidData output = _codec(input);
		Assert.assertEquals((short) 1, output.getData15(AspectRegistry.BLOCK, testAddress));
		Inventory inv = output.getDataSpecial(AspectRegistry.INVENTORY, testAddress);
		Assert.assertEquals(5, inv.maxEncumbrance);
		Assert.assertEquals(4, inv.currentEncumbrance);
		Assert.assertEquals(1, inv.items.size());
		Assert.assertEquals(2, inv.items.get(ItemRegistry.STONE).count());
	}


	private CuboidData _codec(CuboidData input)
	{
		CuboidCodec.Serializer serializer = new CuboidCodec.Serializer(input);
		Queue<Packet> allData = new LinkedList<>();
		Packet packet = serializer.getNextPacket();
		while (null != packet)
		{
			allData.add(packet);
			packet = serializer.getNextPacket();
		}
		CuboidCodec.Deserializer deserializer = new CuboidCodec.Deserializer((Packet_CuboidStart) allData.poll());
		CuboidData output = deserializer.processPacket((Packet_CuboidFragment) allData.poll());
		while (null == output)
		{
			output = deserializer.processPacket((Packet_CuboidFragment) allData.poll());
		}
		Assert.assertTrue(allData.isEmpty());
		return output;
	}
}