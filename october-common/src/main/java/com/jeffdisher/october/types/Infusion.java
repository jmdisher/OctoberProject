package com.jeffdisher.october.types;

import java.util.List;


/**
 * Note that the "number" must be a positive integer as "0" reserved in serialization as "null".
 */
public record Infusion(int number
	, Block table
	, long millisToApply
	, Item centralItem
	, List<Item> consumedItems
	, Item outputItem
)
{
}
