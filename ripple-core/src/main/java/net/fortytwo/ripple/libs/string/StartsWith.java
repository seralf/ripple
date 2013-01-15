package net.fortytwo.ripple.libs.string;

import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;
import net.fortytwo.ripple.model.RippleValue;

/**
 * A primitive which consumes a string and prefix, producing a Boolean value of
 * true if the given string starts with the given prefix, otherwise false.
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class StartsWith extends PrimitiveStackMapping
{
    private static final String[] IDENTIFIERS = {
            StringLibrary.NS_2011_08 + "starts-with",
            StringLibrary.NS_2008_08 + "startsWith",
            StringLibrary.NS_2007_08 + "startsWith"};

    public String[] getIdentifiers()
    {
        return IDENTIFIERS;
    }

	public StartsWith()
		throws RippleException
	{
		super();
	}

    public Parameter[] getParameters()
    {
        return new Parameter[] {
                new Parameter( "s", null, true ),
                new Parameter( "prefix", null, true )};
    }

    public String getComment()
    {
        return "s prefix  =>  b -- where b is true if the given string begins with the given prefix, otherwise false";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection mc) throws RippleException {
		RippleList stack = arg;

		String affix, s;
		RippleValue result;

		affix = mc.toString( stack.getFirst() );
		stack = stack.getRest();
		s = mc.toString( stack.getFirst() );
		stack = stack.getRest();

		result = mc.booleanValue(s.startsWith(affix));
        
        solutions.put(
			stack.push( result ) );
	}
}

