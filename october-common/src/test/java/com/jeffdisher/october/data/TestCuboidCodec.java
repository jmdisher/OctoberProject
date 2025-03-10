package com.jeffdisher.october.data;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCuboidCodec
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void empty()
	{
		BlockAddress testAddress = BlockAddress.fromInt(0, 0, 0);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		
		CuboidData output = _codec(input);
		Assert.assertEquals((short) 0, output.getData15(AspectRegistry.BLOCK, testAddress));
		Assert.assertNull(output.getDataSpecial(AspectRegistry.INVENTORY, testAddress));
	}

	@Test
	public void simple()
	{
		Item stoneItem = ENV.items.getItemById("op.stone");
		BlockAddress testAddress = BlockAddress.fromInt(0, 0, 0);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).addStackable(stoneItem, 2).finish());
		
		CuboidData output = _codec(input);
		Assert.assertEquals((short) 1, output.getData15(AspectRegistry.BLOCK, testAddress));
		Inventory inv = output.getDataSpecial(AspectRegistry.INVENTORY, testAddress);
		Assert.assertEquals(5, inv.maxEncumbrance);
		Assert.assertEquals(8, inv.currentEncumbrance);
		Assert.assertEquals(1, inv.sortedKeys().size());
		Assert.assertEquals(2, inv.getCount(stoneItem));
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
