package net.fortytwo.ripple.libs.stack;

import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;

/**
 * A primitive which consumes a list and produces the first item in the list,
 * followed by the rest of the list.
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class Uncons extends PrimitiveStackMapping {
    private static final String[] IDENTIFIERS = {
            StackLibrary.NS_2013_03 + "uncons",
            StackLibrary.NS_2008_08 + "uncons",
            StackLibrary.NS_2007_08 + "uncons",
            StackLibrary.NS_2007_05 + "uncons"};

    public String[] getIdentifiers() {
        return IDENTIFIERS;
    }

    public Uncons() {
        super();
    }

    public Parameter[] getParameters() {
        return new Parameter[]{
                new Parameter("l", "a list", true)};
    }

    public String getComment() {
        return "l  =>  f r  -- where f is the first member of l and r is the rest of l";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection mc) throws RippleException {

        Object l;

        l = arg.getFirst();
        final RippleList rest = arg.getRest();

        Sink<RippleList> listSink = list -> solutions.accept(
                rest.push(list.getFirst()).push(list.getRest()));

        mc.toList(l, listSink);
    }
}

