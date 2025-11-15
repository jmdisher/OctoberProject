package com.jeffdisher.october.types;


/**
 * This type is used to define the change of a given item to drop from a block or mob, based on some probability.
 * Note that the Item could be stackable or not.
 */
public record DropChance(Item item, int chance1to100)
{
}
