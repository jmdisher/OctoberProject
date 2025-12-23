package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockChargeEnchantment;
import com.jeffdisher.october.mutations.MutationBlockCleanEnchantment;
import com.jeffdisher.october.mutations.MutationBlockFetchSpecialForEnchantment;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EnchantingOperation;
import com.jeffdisher.october.types.Enchantment;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestEnchantingBlockSupport
{
	private static final long MILLIS_PER_TICK = 50L;
	private static final AbsoluteLocation TABLE_LOCATION = new AbsoluteLocation(5, 5, 5);
	private static Environment ENV;
	private static Item IRON_PICKAXE;
	private static Item STONE;
	private static Item STONE_BRICK;
	private static Item IRON_INGOT;
	private static Item PORTAL_STONE;
	private static Block ENCHATING_TABLE;
	private static Block PEDESTAL;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		IRON_PICKAXE = ENV.items.getItemById("op.iron_pickaxe");
		STONE = ENV.items.getItemById("op.stone");
		STONE_BRICK = ENV.items.getItemById("op.stone_brick");
		IRON_INGOT = ENV.items.getItemById("op.iron_ingot");
		PORTAL_STONE = ENV.items.getItemById("op.portal_stone");
		ENCHATING_TABLE = ENV.blocks.fromItem(ENV.items.getItemById("op.enchanting_table"));
		PEDESTAL = ENV.blocks.fromItem(ENV.items.getItemById("op.pedestal"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void failStartEnchant()
	{
		// Load in some of the parts needed, but not all, and show that we don't start.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		TickProcessingContext context = _createContext(cuboid);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.tryStartEnchantingOperation(ENV, context, TABLE_LOCATION, proxy);
		Assert.assertNull(proxy.getEnchantingOperation());
	}

	@Test
	public void startEnchant()
	{
		// Load in all required parts and show that it starts.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, 2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		
		MutationBlockChargeEnchantment[] out = new MutationBlockChargeEnchantment[1];
		TickProcessingContext.IMutationSink mutationSink = new TickProcessingContext.IMutationSink() {
			@Override
			public boolean next(IMutationBlock mutation)
			{
				Assert.assertNull(out[0]);
				out[0] = (MutationBlockChargeEnchantment) mutation;
				return true;
			}
			@Override
			public boolean future(IMutationBlock mutation, long millisToDelay)
			{
				throw new AssertionError("Not in test");
			}
		};
		TickProcessingContext context = _createContextWithSink(cuboid, mutationSink);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.tryStartEnchantingOperation(ENV, context, TABLE_LOCATION, proxy);
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(MILLIS_PER_TICK, operation.chargedMillis());
		Assert.assertEquals(0, operation.consumedItems().size());
		Assert.assertEquals(PropertyRegistry.ENCHANT_DURABILITY, operation.enchantment().enchantmentToApply());
		Assert.assertNotNull(out[0]);
	}

	@Test
	public void startInfuseAfterChangeEnchant()
	{
		// Load a started enchantment but show that trying to start again will cancel it, realizing the items have changed and are now infusion.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromStack(new Items(STONE_BRICK, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, 2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(100L, enchant, null, List.of()));
		
		MutationBlockChargeEnchantment[] out = new MutationBlockChargeEnchantment[1];
		TickProcessingContext.IMutationSink mutationSink = new TickProcessingContext.IMutationSink() {
			@Override
			public boolean next(IMutationBlock mutation)
			{
				Assert.assertNull(out[0]);
				out[0] = (MutationBlockChargeEnchantment) mutation;
				return true;
			}
			@Override
			public boolean future(IMutationBlock mutation, long millisToDelay)
			{
				throw new AssertionError("Not in test");
			}
		};
		TickProcessingContext context = _createContextWithSink(cuboid, mutationSink);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.tryStartEnchantingOperation(ENV, context, TABLE_LOCATION, proxy);
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(MILLIS_PER_TICK, operation.chargedMillis());
		Assert.assertEquals(0, operation.consumedItems().size());
		Assert.assertEquals(PORTAL_STONE, operation.infusion().outputItem());
		Assert.assertNotNull(out[0]);
	}

	@Test
	public void chargeOnce()
	{
		// Show charge change on valid config.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, 2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		long previousChargeMillis = 100L;
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(previousChargeMillis, enchant, null, List.of()));
		
		MutationBlockChargeEnchantment[] out = new MutationBlockChargeEnchantment[1];
		TickProcessingContext.IMutationSink mutationSink = new TickProcessingContext.IMutationSink() {
			@Override
			public boolean next(IMutationBlock mutation)
			{
				Assert.assertNull(out[0]);
				out[0] = (MutationBlockChargeEnchantment) mutation;
				return true;
			}
			@Override
			public boolean future(IMutationBlock mutation, long millisToDelay)
			{
				throw new AssertionError("Not in test");
			}
		};
		TickProcessingContext context = _createContextWithSink(cuboid, mutationSink);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.chargeEnchantingOperation(ENV, context, TABLE_LOCATION, proxy, previousChargeMillis);
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(previousChargeMillis + MILLIS_PER_TICK, operation.chargedMillis());
		Assert.assertNotNull(out[0]);
	}

	@Test
	public void chargeNearCompletion()
	{
		// Show charge saturates near completion.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, 2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		long previousChargeMillis = enchant.millisToApply() - 1L;
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(previousChargeMillis, enchant, null, List.of()));
		
		List<MutationBlockFetchSpecialForEnchantment> out = new ArrayList<>();
		MutationBlockCleanEnchantment[] cleanOut = new MutationBlockCleanEnchantment[1];
		TickProcessingContext.IMutationSink mutationSink = new TickProcessingContext.IMutationSink() {
			@Override
			public boolean next(IMutationBlock mutation)
			{
				out.add((MutationBlockFetchSpecialForEnchantment)mutation);
				return true;
			}
			@Override
			public boolean future(IMutationBlock mutation, long millisToDelay)
			{
				Assert.assertNull(cleanOut[0]);
				cleanOut[0] = (MutationBlockCleanEnchantment) mutation;
				return true;
			}
		};
		TickProcessingContext context = _createContextWithSink(cuboid, mutationSink);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.chargeEnchantingOperation(ENV, context, TABLE_LOCATION, proxy, previousChargeMillis);
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(enchant.millisToApply(), operation.chargedMillis());
		Assert.assertEquals(4, out.size());
		Assert.assertTrue(out.get(0) instanceof MutationBlockFetchSpecialForEnchantment);
		Assert.assertTrue(out.get(1) instanceof MutationBlockFetchSpecialForEnchantment);
		Assert.assertTrue(out.get(2) instanceof MutationBlockFetchSpecialForEnchantment);
		Assert.assertTrue(out.get(3) instanceof MutationBlockFetchSpecialForEnchantment);
		Assert.assertNotNull(cleanOut[0]);
	}

	@Test
	public void receiveEarlyInput()
	{
		// Show that we ignore an input item which appears early, dropping it as a passive.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, 2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		long previousChargeMillis = 100L;
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(previousChargeMillis, enchant, null, List.of()));
		
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext.IPassiveSpawner spawner = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			Assert.assertNull(out[0]);
			out[0] = new PassiveEntity(1
				, type
				, location
				, velocity
				, extendedData
				, 1000L
			);
		};
		TickProcessingContext context = _createContextWithSinks(cuboid, null, spawner);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.receiveConsumedInputItem(ENV, context, TABLE_LOCATION, proxy, ItemSlot.fromStack(new Items(STONE, 1)));
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(previousChargeMillis, operation.chargedMillis());
		Assert.assertEquals(0, operation.consumedItems().size());
		Assert.assertEquals( ItemSlot.fromStack(new Items(STONE, 1)), out[0].extendedData());
	}

	@Test
	public void receiveWrongInput()
	{
		// Show that we ignore an input item which is not expected, dropping it as a passive.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, 2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(enchant.millisToApply(), enchant, null, List.of()));
		
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext.IPassiveSpawner spawner = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			Assert.assertNull(out[0]);
			out[0] = new PassiveEntity(1
				, type
				, location
				, velocity
				, extendedData
				, 1000L
			);
		};
		TickProcessingContext context = _createContextWithSinks(cuboid, null, spawner);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.receiveConsumedInputItem(ENV, context, TABLE_LOCATION, proxy, ItemSlot.fromStack(new Items(STONE_BRICK, 1)));
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(enchant.millisToApply(), operation.chargedMillis());
		Assert.assertEquals(0, operation.consumedItems().size());
		Assert.assertEquals( ItemSlot.fromStack(new Items(STONE_BRICK, 1)), out[0].extendedData());
	}

	@Test
	public void receiveCorrectInput()
	{
		// Show that we consume an expected input item.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(-2, 0, 0), ItemSlot.fromStack(new Items(STONE, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, 2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		_swapSpecialSlot(cuboid, TABLE_LOCATION.getRelative(0, -2, 0), ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(enchant.millisToApply(), enchant, null, List.of()));
		
		TickProcessingContext context = _createContext(cuboid);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.receiveConsumedInputItem(ENV, context, TABLE_LOCATION, proxy, ItemSlot.fromStack(new Items(STONE, 1)));
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(enchant.millisToApply(), operation.chargedMillis());
		Assert.assertEquals(List.of(ItemSlot.fromStack(new Items(STONE, 1))), operation.consumedItems());
	}

	@Test
	public void failInputAtEnd()
	{
		// Show that we will fail in the final input if the target item is no longer correct (we will still wait for post-pass to clean up).
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromStack(new Items(STONE_BRICK, 1)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(enchant.millisToApply(), enchant, null, List.of(ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(IRON_INGOT, 1)))));
		
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext.IPassiveSpawner spawner = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			Assert.assertNull(out[0]);
			out[0] = new PassiveEntity(1
				, type
				, location
				, velocity
				, extendedData
				, 1000L
			);
		};
		TickProcessingContext context = _createContextWithSinks(cuboid, null, spawner);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.receiveConsumedInputItem(ENV, context, TABLE_LOCATION, proxy, ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		EnchantingOperation operation = proxy.getEnchantingOperation();
		
		Assert.assertEquals(enchant.millisToApply(), operation.chargedMillis());
		Assert.assertEquals(List.of(ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(IRON_INGOT, 1))), operation.consumedItems());
		Assert.assertEquals(ItemSlot.fromStack(new Items(IRON_INGOT, 1)), out[0].extendedData());
	}

	@Test
	public void finalizeInputAtEnd()
	{
		// Show that we complete the enchantment on final input if the target is still correct.
		CuboidData cuboid = _testCuboid();
		_swapSpecialSlot(cuboid, TABLE_LOCATION, ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE)));
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(enchant.millisToApply(), enchant, null, List.of(ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(IRON_INGOT, 1)))));
		
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext.IPassiveSpawner spawner = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			Assert.assertNull(out[0]);
			out[0] = new PassiveEntity(1
				, type
				, location
				, velocity
				, extendedData
				, 1000L
			);
		};
		TickProcessingContext context = _createContextWithSinks(cuboid, null, spawner);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.receiveConsumedInputItem(ENV, context, TABLE_LOCATION, proxy, ItemSlot.fromStack(new Items(IRON_INGOT, 1)));
		EnchantingOperation operation = proxy.getEnchantingOperation();
		Assert.assertNull(operation);
		
		NonStackableItem finishedItem = ((ItemSlot)out[0].extendedData()).nonStackable;
		Assert.assertEquals(IRON_PICKAXE, finishedItem.type());
		Assert.assertEquals(500000, finishedItem.properties().get(PropertyRegistry.DURABILITY));
		Assert.assertEquals((byte)1, finishedItem.properties().get(PropertyRegistry.ENCHANT_DURABILITY));
	}

	@Test
	public void cleanupPostPass()
	{
		// Show that we spawn any items "stuck" in this invalid operation as passives in the post-pass.
		CuboidData cuboid = _testCuboid();
		
		Enchantment enchant = ENV.enchantments.getBlindEnchantment(ENCHATING_TABLE, IRON_PICKAXE, PropertyRegistry.ENCHANT_DURABILITY);
		cuboid.setDataSpecial(AspectRegistry.ENCHANTING, TABLE_LOCATION.getBlockAddress(), new EnchantingOperation(enchant.millisToApply(), enchant, null, List.of(ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(STONE, 1)), ItemSlot.fromStack(new Items(IRON_INGOT, 1)))));
		
		List<PassiveEntity> out = new ArrayList<>();
		TickProcessingContext.IPassiveSpawner spawner = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			out.add(new PassiveEntity(1
				, type
				, location
				, velocity
				, extendedData
				, 1000L
			));
		};
		TickProcessingContext context = _createContextWithSinks(cuboid, null, spawner);
		MutableBlockProxy proxy = new MutableBlockProxy(TABLE_LOCATION, cuboid);
		EnchantingBlockSupport.cleanUpOrphanedOperations(ENV, context, TABLE_LOCATION, proxy);
		EnchantingOperation operation = proxy.getEnchantingOperation();
		Assert.assertNull(operation);
		
		Assert.assertEquals(3, out.size());
		Assert.assertEquals(ItemSlot.fromStack(new Items(STONE, 1)), out.get(0).extendedData());
		Assert.assertEquals(ItemSlot.fromStack(new Items(STONE, 1)), out.get(1).extendedData());
		Assert.assertEquals(ItemSlot.fromStack(new Items(IRON_INGOT, 1)), out.get(2).extendedData());
	}


	private static TickProcessingContext _createContext(CuboidData cuboid)
	{
		return _createContextWithSinks(cuboid, null, null);
	}

	private static TickProcessingContext _createContextWithSink(CuboidData cuboid
		, TickProcessingContext.IMutationSink mutationSink
	)
	{
		return _createContextWithSinks(cuboid, mutationSink, null);
	}

	private static TickProcessingContext _createContextWithSinks(CuboidData cuboid
		, TickProcessingContext.IMutationSink mutationSink
		, TickProcessingContext.IPassiveSpawner passiveSpawner
	)
	{
		WorldConfig config = new WorldConfig();
		return new TickProcessingContext(1L
			, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
			, null
			, null
			, null
			, mutationSink
			, null
			, null
			, passiveSpawner
			, null
			, null
			, null
			, config
			, MILLIS_PER_TICK
			, 1000L
		);
	}

	private static CuboidData _testCuboid()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, TABLE_LOCATION.getBlockAddress(), ENCHATING_TABLE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, TABLE_LOCATION.getRelative(0, -2, 0).getBlockAddress(), PEDESTAL.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, TABLE_LOCATION.getRelative(0, 2, 0).getBlockAddress(), PEDESTAL.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, TABLE_LOCATION.getRelative(-2, 0, 0).getBlockAddress(), PEDESTAL.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, TABLE_LOCATION.getRelative(2, 0, 0).getBlockAddress(), PEDESTAL.item().number());
		return cuboid;
	}

	private static ItemSlot _swapSpecialSlot(CuboidData cuboid, AbsoluteLocation location, ItemSlot slot)
	{
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		ItemSlot old = proxy.getSpecialSlot();
		proxy.setSpecialSlot(slot);
		proxy.writeBack(cuboid);
		return old;
	}
}
