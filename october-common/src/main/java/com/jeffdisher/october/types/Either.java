package com.jeffdisher.october.types;


/**
 * Just a container for cases where the input can be 1 of 2 possible types, but is only ever exactly 1 of them.
 * The implementation doesn't do anything too clever callers passing in callbacks based on type or any sort of class
 * cluster, assuming that the caller is fine making those decisions for itself.
 * 
 * @param <A> The first type.
 * @param <B> The second type.
 */
public final class Either<A, B>
{
	public static <A, B> Either<A, B> first(A object)
	{
		return new Either<>(object, null);
	}

	public static <A, B> Either<A, B> second(B object)
	{
		return new Either<>(null, object);
	}


	public final A first;
	public final B second;

	private Either(A first, B second)
	{
		this.first = first;
		this.second = second;
	}
}
