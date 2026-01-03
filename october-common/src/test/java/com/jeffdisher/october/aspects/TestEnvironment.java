package com.jeffdisher.october.aspects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


public class TestEnvironment
{
	@Test
	public void singleMod() throws Throwable
	{
		byte[] zipBytes = _zipStrings(Map.of(ModLayer.FILE_ITEM_REGISTRY, "test1\tTesting Item\n"
			, ModLayer.FILE_BLOCK_ASPECT, "test1\n"
				+ "\tblock_material	PICKAXE\n"
			, ModLayer.FILE_INVENTORY_ENCUMBRANCE, "test1\t5\n"
		));
		ZipInputStream unzip = new ZipInputStream(new ByteArrayInputStream(zipBytes));
		ModLayer[] layers = new ModLayer[] {
			ModLayer.load(unzip)
		};
		Environment env = Environment.createModdedInstance(layers);
		Item item = env.items.getItemById("test1");
		Assert.assertEquals("Testing Item", item.name());
		Block block = env.blocks.fromItem(item);
		Assert.assertNotNull(block);
		BlockMaterial material = env.blocks.getBlockMaterial(block);
		Assert.assertEquals(BlockMaterial.PICKAXE, material);
		Assert.assertEquals(5, env.encumbrance.getEncumbrance(item));
		
		Environment.clearSharedInstance();
	}


	private static byte[] _zipStrings(Map<String, String> namedStrings) throws IOException
	{
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		ZipOutputStream out = new ZipOutputStream(byteOut);
		for (Map.Entry<String, String> elt : namedStrings.entrySet())
		{
			ZipEntry entry = new ZipEntry(elt.getKey());
			out.putNextEntry(entry);
			out.write(elt.getValue().getBytes());
		}
		out.close();
		return byteOut.toByteArray();
	}
}
