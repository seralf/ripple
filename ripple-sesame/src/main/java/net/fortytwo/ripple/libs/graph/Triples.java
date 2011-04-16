/*
 * $URL$
 * $Revision$
 * $Author$
 *
 * Copyright (C) 2007-2011 Joshua Shinavier
 */


package net.fortytwo.ripple.libs.graph;

import net.fortytwo.ripple.RippleException;
import net.fortytwo.flow.NullSink;
import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RDFValue;
import net.fortytwo.ripple.model.RippleList;
import net.fortytwo.ripple.model.StackContext;
import net.fortytwo.flow.rdf.RDFSink;
import net.fortytwo.flow.rdf.SesameInputAdapter;
import net.fortytwo.ripple.util.HTTPUtils;
import net.fortytwo.ripple.util.RDFHTTPUtils;

import org.apache.commons.httpclient.HttpMethod;
import org.openrdf.model.Namespace;
import org.openrdf.model.Statement;

/**
 * A primitive which consumes an information resource and produces a list
 * (subject, predicate, object) for each RDF triple in the corresponding
 * Semantic Web document.
 */
public class Triples extends PrimitiveStackMapping
{
    private static final String[] IDENTIFIERS = {
            GraphLibrary.NS_2011_04 + "triples",
            GraphLibrary.NS_2008_08 + "triples",
            GraphLibrary.NS_2007_08 + "triples"};

    public String[] getIdentifiers()
    {
        return IDENTIFIERS;
    }

	public Triples()
		throws RippleException
	{
		super();
	}

    public Parameter[] getParameters()
    {
        return new Parameter[] {
                new Parameter( "doc", "a Semantic Web document", true )};
    }

    public String getComment()
    {
        return "doc  =>  s p o  -- for each triple (s p o) in document doc";
    }

	public void apply( final StackContext arg,
						 final Sink<StackContext, RippleException> solutions )
		throws RippleException
	{
		final ModelConnection mc = arg.getModelConnection();
		RippleList stack = arg.getStack();

		String uri = stack.getFirst().toString();

		SesameInputAdapter sc = createAdapter( arg, solutions );

		HttpMethod method = HTTPUtils.createGetMethod( uri );
		HTTPUtils.setRdfAcceptHeader( method );
		RDFHTTPUtils.read( method, sc, uri, null );
	}

	static SesameInputAdapter createAdapter( final StackContext arg,
										final Sink<StackContext, RippleException> resultSink )
	{
		final ModelConnection mc = arg.getModelConnection();
		final RippleList rest = arg.getStack().getRest();

		RDFSink rdfSink = new RDFSink()
		{
			// Push statements.
			private Sink<Statement, RippleException> stSink = new Sink<Statement, RippleException>()
            {
                public void put( final Statement st ) throws RippleException
                {
                    resultSink.put( arg.with(
                            rest.push(mc.canonicalValue(new RDFValue(st.getSubject())))
                                    .push(mc.canonicalValue(new RDFValue(st.getPredicate())))
                                    .push(mc.canonicalValue(new RDFValue(st.getObject())))) );
                }
            };

			// Discard namespaces.
			private Sink<Namespace, RippleException> nsSink = new NullSink<Namespace, RippleException>();

			// Discard comments.
			private Sink<String, RippleException> cmtSink = new NullSink<String, RippleException>();

			public Sink<Statement, RippleException> statementSink()
			{
				return stSink;
			}

			public Sink<Namespace, RippleException> namespaceSink()
			{
				return nsSink;
			}

			public Sink<String, RippleException> commentSink()
			{
				return cmtSink;
			}
		};

		SesameInputAdapter sc = new SesameInputAdapter( rdfSink );

		return sc;
	}
}

