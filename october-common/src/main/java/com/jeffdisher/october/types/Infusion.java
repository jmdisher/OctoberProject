package com.jeffdisher.october.types;

import java.util.Map;


/**
 * Note that the "number" must be a positive integer as "0" reserved in serialization as "null".
 */
public record Infusion(int number
	, Block table
	, long millisToApply
	, Map<Item, Integer> consumedItems
	, Item outputItem
)
{
}
