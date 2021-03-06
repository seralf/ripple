package net.fortytwo.ripple.libs.stack;

import net.fortytwo.ripple.test.RippleTestCase;
import org.junit.Test;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class DupdTest extends RippleTestCase {
    @Test
    public void testAll() throws Exception {
        assertReducesTo("42 1331 dupd.", "42 42 1331");
    }

    @Test
    public void testArity() throws Exception {
        assertReducesTo("2 3 add. 44 2 sub. dup.", "2 3 add. 42 42");
    }
}