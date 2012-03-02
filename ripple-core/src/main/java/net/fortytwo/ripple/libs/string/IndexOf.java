/*
 * $URL$
 * $Revision$
 * $Author$
 *
 * Copyright (C) 2007-2012 Joshua Shinavier
 */


package net.fortytwo.ripple.libs.string;

import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;

/**
 * A primitive which consumes a string and a substring and produces the index of
 * the first occurrence of the substring.
 */
public class IndexOf extends PrimitiveStackMapping
{
    private static final String[] IDENTIFIERS = {
            StringLibrary.NS_2011_08 + "index-of",
            StringLibrary.NS_2008_08 + "indexOf",
            StringLibrary.NS_2007_08 + "indexOf"};

    public String[] getIdentifiers()
    {
        return IDENTIFIERS;
    }

	public IndexOf()
		throws RippleException
	{
		super();
	}

    public Parameter[] getParameters()
    {
        return new Parameter[] {
                new Parameter( "str", null, true ),
                new Parameter( "substr", null, true )};
    }

    public String getComment()
    {
        return "str substr  =>  i -- where i is the index of the first occurrence of substr in str, or -1 if it does not occur";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection mc) throws RippleException {
		RippleList stack = arg;
		String str, substr;
		int result;

		substr = mc.toString( stack.getFirst() );
		stack = stack.getRest();
		str = mc.toString( stack.getFirst() );
		stack = stack.getRest();

		result = str.indexOf( substr );
		solutions.put(
				stack.push( mc.numericValue(result) ) );
	}
}

