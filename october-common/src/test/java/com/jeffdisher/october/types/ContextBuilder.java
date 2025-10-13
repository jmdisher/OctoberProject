package com.jeffdisher.october.types;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import org.junit.Assert;

import com.jeffdisher.october.data.BlockProxy;


/**
 * This is more actually a utility class _for_ unit tests, as opposed to an actual test suite for TickProcessingContext.
 */
public class ContextBuilder
{
	public static final long DEFAULT_MILLIS_PER_TICK = 100L;

	public static ContextBuilder build()
	{
		return new ContextBuilder();
	}

	public static ContextBuilder nextTick(TickProcessingContext previous, long ticksToAdvance)
	{
		ContextBuilder builder = new ContextBuilder()
				.tick(previous.currentTick + ticksToAdvance)
				.lookups(previous.previousBlockLookUp, previous.previousEntityLookUp, previous.previousPassiveLookUp)
				.sinks(previous.mutationSink, previous.newChangeSink)
				.spawner(previous.creatureSpawner)
				.eventSink(previous.eventSink)
		;
		builder.randomInt = previous.randomInt;
		builder.config = previous.config;
		builder.millisPerTick = previous.millisPerTick;
		return builder;
	}


	public long currentTick;
	public Function<AbsoluteLocation, BlockProxy> previousBlockLookUp;
	public Function<Integer, MinimalEntity> previousEntityLookUp;
	public Function<Integer, PartialPassive> previousPassiveLookUp;
	public IByteLookup<AbsoluteLocation> skyLight;
	public TickProcessingContext.IMutationSink mutationSink;
	public TickProcessingContext.IChangeSink newChangeSink;
	public TickProcessingContext.ICreatureSpawner creatureSpawner;
	public TickProcessingContext.IPassiveSpawner passiveSpawner;
	public IntUnaryOperator randomInt;
	public TickProcessingContext.IEventSink eventSink;
	public Consumer<CuboidAddress> keepAliveSink;
	public WorldConfig config;
	public long millisPerTick;

	private ContextBuilder()
	{
		Random random = new Random();
		
		this.currentTick = 1L;
		this.randomInt = (int bound) -> {
			return random.nextInt(bound);
		};
		this.config = new WorldConfig();
		this.millisPerTick = DEFAULT_MILLIS_PER_TICK;
	}

	public ContextBuilder tick(long tickNumber)
	{
		this.currentTick = tickNumber;
		return this;
	}

	public ContextBuilder lookups(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
		, Function<Integer, MinimalEntity> previousEntityLookUp
		, Function<Integer, PartialPassive> previousPassiveLookUp
	)
	{
		this.previousBlockLookUp = previousBlockLookUp;
		this.previousEntityLookUp = previousEntityLookUp;
		this.previousPassiveLookUp = previousPassiveLookUp;
		return this;
	}

	public ContextBuilder skyLight(IByteLookup<AbsoluteLocation> skyLight)
	{
		this.skyLight = skyLight;
		return this;
	}

	public ContextBuilder sinks(TickProcessingContext.IMutationSink mutationSink
			, TickProcessingContext.IChangeSink changeSink)
	{
		this.mutationSink = mutationSink;
		this.newChangeSink = changeSink;
		return this;
	}

	public ContextBuilder spawner(TickProcessingContext.ICreatureSpawner creatureSpawner)
	{
		this.creatureSpawner = creatureSpawner;
		return this;
	}

	public ContextBuilder passive(TickProcessingContext.IPassiveSpawner passiveSpawner)
	{
		this.passiveSpawner = passiveSpawner;
		return this;
	}

	public ContextBuilder fixedRandom(int value)
	{
		this.randomInt = (int limit) -> {
			// We will assert that the limit is greater than our number to verify it isn't unexpected.
			Assert.assertTrue(limit > value);
			return value;
		};
		return this;
	}

	public ContextBuilder eventSink(TickProcessingContext.IEventSink eventSink)
	{
		this.eventSink = eventSink;
		return this;
	}

	public ContextBuilder keepAliveSink(Consumer<CuboidAddress> keepAliveSink)
	{
		this.keepAliveSink = keepAliveSink;
		return this;
	}

	public ContextBuilder boundedRandom(int value)
	{
		this.randomInt = (int bound) -> {
			return (bound > value)
					? value
					: (bound - 1)
			;
		};
		return this;
	}

	public ContextBuilder config(WorldConfig config)
	{
		this.config = config;
		return this;
	}

	public ContextBuilder millisPerTick(long millisPerTick)
	{
		this.millisPerTick = millisPerTick;
		return this;
	}

	public TickProcessingContext finish()
	{
		return new TickProcessingContext(this.currentTick
				, this.previousBlockLookUp
				, this.previousEntityLookUp
				, this.previousPassiveLookUp
				, this.skyLight
				, this.mutationSink
				, this.newChangeSink
				, this.creatureSpawner
				, this.passiveSpawner
				, this.randomInt
				, this.eventSink
				, this.keepAliveSink
				, this.config
				, this.millisPerTick
				, (this.currentTick * this.millisPerTick)
		);
	}
}
