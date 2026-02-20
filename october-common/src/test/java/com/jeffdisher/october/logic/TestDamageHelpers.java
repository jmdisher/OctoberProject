package com.jeffdisher.october.logic;

import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestDamageHelpers
{
	private static Environment ENV;
	private static Block LAVA_SOURCE;
	private static Block LOG;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		LAVA_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_source"));
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void noEnvironmentDamage()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return BlockProxy.load(location.getBlockAddress(), cuboid);
		};
		EntityLocation base = new EntityLocation(2.0f, 2.0f, 2.0f);
		EntityVolume volume = new EntityVolume(1.5f, 1.2f);
		
		int damage = DamageHelpers.findEnvironmentalDamageInVolume(ENV, previousBlockLookUp, base, volume);
		Assert.assertEquals(0, damage);
	}

	@Test
	public void fireDamage()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return BlockProxy.load(location.getBlockAddress(), cuboid);
		};
		EntityLocation base = new EntityLocation(2.0f, 2.0f, 2.0f);
		EntityVolume volume = new EntityVolume(1.5f, 1.2f);
		AbsoluteLocation log = base.getBlockLocation().getRelative(0, 0, -1);
		cuboid.setData15(AspectRegistry.BLOCK, log.getBlockAddress(), LOG.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, log.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		
		int damage = DamageHelpers.findEnvironmentalDamageInVolume(ENV, previousBlockLookUp, base, volume);
		Assert.assertEquals(MiscConstants.FIRE_DAMAGE_PER_SECOND, (byte)damage);
	}

	@Test
	public void fireAndLavaDamage()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return BlockProxy.load(location.getBlockAddress(), cuboid);
		};
		EntityLocation base = new EntityLocation(2.0f, 2.0f, 2.0f);
		EntityVolume volume = new EntityVolume(1.5f, 1.2f);
		AbsoluteLocation log = base.getBlockLocation().getRelative(0, 0, -1);
		AbsoluteLocation lava = base.getBlockLocation().getRelative(0, 1, 0);
		cuboid.setData15(AspectRegistry.BLOCK, log.getBlockAddress(), LOG.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, log.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		cuboid.setData15(AspectRegistry.BLOCK, lava.getBlockAddress(), LAVA_SOURCE.item().number());
		
		int damage = DamageHelpers.findEnvironmentalDamageInVolume(ENV, previousBlockLookUp, base, volume);
		Assert.assertEquals(ENV.blocks.getBlockDamage(LAVA_SOURCE), damage);
	}
}
