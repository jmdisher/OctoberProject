package com.jeffdisher.october.worldgen;

import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Definitions of special NonStackableItem instances for use in world generation.
 * TODO:  Move these out into a data file and resolve them by name where needed.
 */
public class SpecialItemReferences
{
	public final NonStackableItem northOrb;
	public final NonStackableItem southOrb;
	public final NonStackableItem eastOrb;
	public final NonStackableItem westOrb;
	public final NonStackableItem reverseOrb;

	public final NonStackableItem specialPick;
	public final NonStackableItem specialShovel;
	public final NonStackableItem specialAxe;
	public final NonStackableItem specialSword;

	public SpecialItemReferences(Environment env)
	{
		Item portalOrb = env.items.getItemById("op.portal_orb");
		this.northOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(0, 1000, 0)));
		this.southOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(0, -1000, 0)));
		this.eastOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(1000, 0, 0)));
		this.westOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(-1000, 0, 0)));
		this.reverseOrb = new NonStackableItem(portalOrb, Map.of(PropertyRegistry.LOCATION, new AbsoluteLocation(0, -1000, 0)));
		
		Item diamondPick = env.items.getItemById("op.diamond_pickaxe");
		this.specialPick= new NonStackableItem(diamondPick, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondPick)
			, PropertyRegistry.NAME, "Hewing Pick Axe"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)5
		));
		Item diamondShovel = env.items.getItemById("op.diamond_shovel");
		this.specialShovel= new NonStackableItem(diamondShovel, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondShovel)
			, PropertyRegistry.NAME, "Excavator Shovel"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)5
		));
		Item diamondAxe = env.items.getItemById("op.diamond_axe");
		this.specialAxe= new NonStackableItem(diamondAxe, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondAxe)
			, PropertyRegistry.NAME, "Cleaving Axe"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)5
		));
		Item diamondSword = env.items.getItemById("op.diamond_sword");
		this.specialSword = new NonStackableItem(diamondSword, Map.of(PropertyRegistry.DURABILITY, env.durability.getDurability(diamondSword)
			, PropertyRegistry.NAME, "Vorpal Blade"
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)5
			, PropertyRegistry.ENCHANT_WEAPON_MELEE, (byte)5
		));
	}
}
