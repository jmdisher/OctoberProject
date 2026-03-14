package com.jeffdisher.october.types;


/**
 * Just a container for cases where a 2-element tuple needs to be passed around.
 * 
 * @param <A> The type of field one.
 * @param <B> The type of field two.
 */
public record Pair<A, B>(A one, B two)
{
}
