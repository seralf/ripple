package net.fortytwo.ripple.libs.control;

import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.NullStackMapping;
import net.fortytwo.ripple.model.Operator;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;
import net.fortytwo.ripple.model.StackMapping;

/**
 * A primitive which consumes a Boolean filter b, a filter t, and a filter t,
 * then applies an active copy of b to the stack.  If b yields a value of
 * true, then t is applied the rest of the stack.  Otherwise, f is applied to
 * the rest of the stack.
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class Ifte extends PrimitiveStackMapping {
    private static final String[] IDENTIFIERS = {
            ControlLibrary.NS_2013_03 + "ifte",
            // Note: the previous implementation of ifte had different semantics
            // (rather than the current, Joy semantics).
    };

    public String[] getIdentifiers() {
        return IDENTIFIERS;
    }

    public Ifte()
            throws RippleException {
        super();
    }

    public Parameter[] getParameters() {
        return new Parameter[]{
                new Parameter("b", "a boolean mapping", true),
                new Parameter("ifPart", "this program is executed if the condition is true", true),
                new Parameter("elsePart", "this program is executed if the condition is false", true)};
    }

    public String getComment() {
        return "b t f =>  p!  -- where p is t if the application of b evaluates to true," +
                " and f if it evaluates to false";
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection mc) throws RippleException {
        RippleList stack = arg;

        Object falseProg = stack.getFirst();
        stack = stack.getRest();
        Object trueProg = stack.getFirst();
        stack = stack.getRest();
        Object criterion = stack.getFirst();
        stack = stack.getRest();

        StackMapping inner = new IfteInner(stack, trueProg, falseProg);
        RippleList newStack = stack.push(criterion).push(Operator.OP)
                .push(new Operator(inner));

        solutions.accept(newStack);
    }

    private class IfteInner implements StackMapping {
        private final RippleList originalStack;
        private final Object trueProgram, falseProgram;

        public IfteInner(final RippleList originalStack,
                         final Object trueProgram,
                         final Object falseProgram) {
            this.originalStack = originalStack;
            this.trueProgram = trueProgram;
            this.falseProgram = falseProgram;
        }

        public int arity() {
            return 1;
        }

        public StackMapping getInverse() throws RippleException {
            return new NullStackMapping();
        }

        public boolean isTransparent() {
            return true;
        }

        public void apply(final RippleList arg,
                          final Sink<RippleList> solutions,
                          final ModelConnection mc) throws RippleException {

            Object b = arg.getFirst();

            RippleList stack = mc.toBoolean(b)
                    ? originalStack.push(trueProgram).push(Operator.OP)
                    : originalStack.push(falseProgram).push(Operator.OP);

            solutions.accept(stack);
        }
    }
}

