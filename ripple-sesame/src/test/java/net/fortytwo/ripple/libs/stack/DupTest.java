package net.fortytwo.ripple.libs.stack;

import net.fortytwo.ripple.test.NewRippleTestCase;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.model.vocabulary.RDFS;

/**
 * Author: josh
 * Date: May 3, 2008
 * Time: 1:42:20 PM
 */
public class DupTest extends NewRippleTestCase
{
    public void testAll() throws Exception
    {
        assertReducesTo( "42 dup >>", "42 42" );
    }

    public void testArity() throws Exception
    {
        assertReducesTo( "2 3 add >> 44 2 sub >> dup >>", "2 3 add >> 42 42" );
    }
}