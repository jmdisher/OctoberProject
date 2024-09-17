package com.jeffdisher.october.types;

import java.util.Random;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import org.junit.Assert;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.types.TickProcessingContext.IChangeSink;
import com.jeffdisher.october.types.TickProcessingContext.IMutationSink;


/**
 * This is more actually a utility class _for_ unit tests, as opposed to an actual test suite for TickProcessingContext.
 */
public class ContextBuilder
{
	public static ContextBuilder build()
	{
		return new ContextBuilder();
	}

	public static ContextBuilder nextTick(TickProcessingContext previous, long ticksToAdvance)
	{
		ContextBuilder builder = new ContextBuilder()
				.tick(previous.currentTick + ticksToAdvance)
				.lookups(previous.previousBlockLookUp, previous.previousEntityLookUp)
				.sinks(previous.mutationSink, previous.newChangeSink)
				.assigner(previous.idAssigner)
		;
		builder.randomInt = previous.randomInt;
		builder.config = previous.config;
		builder.millisPerTick = previous.millisPerTick;
		return builder;
	}


	public long currentTick;
	public Function<AbsoluteLocation, BlockProxy> previousBlockLookUp;
	public Function<Integer, MinimalEntity> previousEntityLookUp;
	public IByteLookup<AbsoluteLocation> skyLight;
	public IMutationSink mutationSink;
	public IChangeSink newChangeSink;
	public CreatureIdAssigner idAssigner;
	public IntUnaryOperator randomInt;
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
		this.millisPerTick = 100L;
	}

	public ContextBuilder tick(long tickNumber)
	{
		this.currentTick = tickNumber;
		return this;
	}

	public ContextBuilder lookups(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, Function<Integer, MinimalEntity> previousEntityLookUp)
	{
		this.previousBlockLookUp = previousBlockLookUp;
		this.previousEntityLookUp = previousEntityLookUp;
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

	public ContextBuilder assigner(CreatureIdAssigner idAssigner)
	{
		this.idAssigner = idAssigner;
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
				, this.skyLight
				, this.mutationSink
				, this.newChangeSink
				, this.idAssigner
				, this.randomInt
				, this.config
				, this.millisPerTick
		);
	}
}
