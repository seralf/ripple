/*
 * $URL$
 * $Revision$
 * $Author$
 *
 * Copyright (C) 2007-2012 Joshua Shinavier
 */


package net.fortytwo.ripple.libs.stack;

import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;
import net.fortytwo.ripple.model.RippleValue;

/**
 * A primitive which swaps the second- and third-to-topmost items on the stack.
 */
public class Swapd extends PrimitiveStackMapping
{
    private static final String[] IDENTIFIERS = {
            StackLibrary.NS_2011_08 + "swapd",
            StackLibrary.NS_2008_08 + "swapd",
            StackLibrary.NS_2007_08 + "swapd",
            StackLibrary.NS_2007_05 + "swapd"};

    public String[] getIdentifiers()
    {
        return IDENTIFIERS;
    }

	public Swapd()
		throws RippleException
	{
		super();
	}

    public Parameter[] getParameters()
    {
        return new Parameter[] {
                new Parameter( "x", null, true ),
                new Parameter( "y", null, true ),
                new Parameter( "z", null, true )};
    }

    public String getComment()
    {
        return "x y z  =>  y x z";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection mc) throws RippleException {
		RippleList stack = arg;
		RippleValue z, y, x;

		z = stack.getFirst();
		stack = stack.getRest();
		y = stack.getFirst();
		stack = stack.getRest();
		x = stack.getFirst();
		stack = stack.getRest();

		solutions.put(
				stack.push( y ).push( x ).push( z ) );
	}
}

