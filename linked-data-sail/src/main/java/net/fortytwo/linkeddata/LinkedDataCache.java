package net.fortytwo.linkeddata;

import net.fortytwo.linkeddata.dereferencers.FileURIDereferencer;
import net.fortytwo.linkeddata.dereferencers.HTTPURIDereferencer;
import net.fortytwo.linkeddata.dereferencers.JarURIDereferencer;
import net.fortytwo.linkeddata.rdfizers.ImageRdfizer;
import net.fortytwo.linkeddata.rdfizers.VerbatimRdfizer;
import net.fortytwo.linkeddata.sail.LinkedDataSail;
import net.fortytwo.linkeddata.util.RDFUtils;
import org.openrdf.model.IRI;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.SimpleValueFactory;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.RDFParserRegistry;
import org.openrdf.sail.Sail;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A manager for a dynamic set of RDF graphs collected from the Web.
 * The cache uses configurable IRI dereferencers and RDFizers to fetch and translate documents,
 * and connects to an RDF triple store which provides a unified view of the Web of Data.
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class LinkedDataCache {
    private static final Logger logger = LoggerFactory.getLogger(LinkedDataCache.class);

    private static final ValueFactory constValueFactory = SimpleValueFactory.getInstance();

    private static final String
            CACHE_NS = "http://fortytwo.net/2012/02/linkeddata#";
    public static final IRI
            CACHE_MEMO = constValueFactory.createIRI(CACHE_NS + "memo"),
            CACHE_REDIRECTSTO = constValueFactory.createIRI(CACHE_NS + "redirectsTo"),
            CACHE_GRAPH = null;  // the default context is used for caching metadata

    private static final String[] NON_RDF_EXTENSIONS = {
            "123", "3dm", "3dmf", "3gp", "8bi", "aac", "ai", "aif", "app", "asf",
            "asp", "asx", "avi", "bat", "bin", "bmp", "c", "cab", "cfg", "cgi",
            "com", "cpl", "cpp", "css", "csv", "dat", "db", "dll", "dmg", "dmp",
            "doc", "drv", "drw", "dxf", "eps", "exe", "fnt", "fon", "gif", "gz",
            "h", "hqx", /*"htm", "html",*/ "iff", "indd", "ini", "iso", "java", /*"jpeg",*/
            /*"jpg",*/ "js", "jsp", "key", "log", "m3u", "mdb", "mid", "midi", "mim",
            "mng", "mov", "mp3", "mp4", "mpa", "mpg", "msg", "msi", "otf", "pct",
            "pdf", "php", "pif", "pkg", "pl", "plugin", "png", "pps", "ppt", "ps",
            "psd", "psp", "qt", "qxd", "qxp", "ra", "ram", "rar", "reg", "rm",
            "rtf", "sea", "sit", "sitx", "sql", "svg", "swf", "sys", "tar", "tif",
            "ttf", "uue", "vb", "vcd", "wav", "wks", "wma", "wmv", "wpd", "wps",
            "ws", /*"xhtml",*/ "xll", "xls", "yps", "zip"};

    private static final int MINIMUM_CAPACITY = 100;

    private final CachingMetadata metadata;
    private final ValueFactory valueFactory;
    private final boolean useBlankNodes;

    private boolean autoCommit = true;

    private boolean derefSubjects = true;
    private boolean derefPredicates = false;
    private boolean derefObjects = true;
    private boolean derefContexts = false;

    private String acceptHeader = null;

    private CacheExpirationPolicy expirationPolicy;

    // Maps media types to Rdfizers
    private final Map<String, MediaTypeInfo> rdfizers
            = new HashMap<>();

    // Maps IRI schemes to Dereferencers
    private final Map<String, Dereferencer> dereferencers = new HashMap<>();

    private DataStore dataStore;

    // single connection shared among all accessing threads
    private final SailConnection sailConnection;

    /**
     * Constructs a cache with the default settings, dereferencers, and rdfizers.
     *
     * @param sail the underlying triple store for the cache
     * @return the default cache
     */
    public static LinkedDataCache createDefault(final Sail sail) {
        LinkedDataCache cache = new LinkedDataCache(sail);

        cache.addDefaultDereferencers();
        cache.addVerbatimRdfizers();
        cache.addMediaRdfizers();

        return cache;
    }

    private RDFParser.DatatypeHandling getDatatypeHandling() {
        RDFParser.DatatypeHandling datatypeHandling;

        String p = LinkedDataSail.getProperty(LinkedDataSail.DATATYPE_HANDLING_POLICY, "ignore");
        datatypeHandling
                = p.equals("ignore")
                ? RDFParser.DatatypeHandling.IGNORE
                : p.equals("verify")
                ? RDFParser.DatatypeHandling.VERIFY
                : p.equals("normalize")
                ? RDFParser.DatatypeHandling.NORMALIZE
                : null;
        if (null == datatypeHandling) {
            throw new IllegalStateException("no such datatype handling policy: " + p);
        }

        return datatypeHandling;
    }

    private void addDefaultDereferencers() {
        RedirectManager redirectManager = new RedirectManager(getSailConnection());

        // Add IRI dereferencers.
        HTTPURIDereferencer hdref = new HTTPURIDereferencer(this, redirectManager);
        for (String x : NON_RDF_EXTENSIONS) {
            hdref.blackListExtension(x);
        }
        addDereferencer("http", hdref);

        addDereferencer("file", new FileURIDereferencer());
        addDereferencer("jar", new JarURIDereferencer());
    }

    private void addVerbatimRdfizers() {
        RDFParser.DatatypeHandling datatypeHandling = getDatatypeHandling();

        Map<RDFFormat, Rdfizer> rdfizerMap = new HashMap<>();

        for (RDFFormat format : RDFParserRegistry.getInstance().getKeys()) {
            Rdfizer rdfizer = new VerbatimRdfizer(format, datatypeHandling);
            rdfizerMap.put(format, rdfizer);
            for (String type : format.getMIMETypes()) {
                double qualityFactor = type.equals("application/rdf+xml") ? 1.0 : 0.5;
                addRdfizer(type, rdfizer, qualityFactor);
            }
        }

        addRdfizer("text/xml", rdfizerMap.get(RDFFormat.RDFXML), 0.25);
    }

    private void addMediaRdfizers() {
        // Additional rdfizers
        Rdfizer imageRdfizer = new ImageRdfizer();
        // Mainstream EXIF-compatible image types: JPEG, TIFF
        addRdfizer("image/jpeg", imageRdfizer, 0.4);
        addRdfizer("image/tiff", imageRdfizer, 0.4);
        addRdfizer("image/tiff-fx", imageRdfizer, 0.4);

        // TODO: add an EXIF-based Rdfizer for RIFF WAV audio files
    }

    /**
     * @param sail underlying triple store for the cache
     */
    private LinkedDataCache(final Sail sail) {
        sailConnection = sail.getConnection();
        sailConnection.begin();

        int capacity = Integer.valueOf(LinkedDataSail.getProperty(LinkedDataSail.MEMORY_CACHE_CAPACITY, "10000"));
        if (capacity < MINIMUM_CAPACITY) {
            logger.warn("LinkedDataSail.MEMORY_CACHE_CAPACITY is suspiciously low. Using "
                    + MINIMUM_CAPACITY);
        }

        this.metadata = new CachingMetadata(capacity, sail.getValueFactory());

        this.valueFactory = sail.getValueFactory();
        useBlankNodes = Boolean.valueOf(LinkedDataSail.getProperty(LinkedDataSail.USE_BLANK_NODES, "false"));

        this.expirationPolicy = new DefaultCacheExpirationPolicy();

        dataStore = sc -> new SesameOutputAdapter(new SailInserter(sc));
    }

    public synchronized void clear() {
        metadata.clear();

        SailConnection sc = getSailConnection();
        sc.clear();
        sc.commit();
        sc.begin();
    }

    // note: only closes in one thread
    public synchronized void close() {
        SailConnection sc = getSailConnection();
        if (null != sc) {
            sc.close();
        }
    }

    public synchronized SailConnection getSailConnection() {
        return sailConnection;
    }

    public void setDataStore(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * @return an HTTP "Accept" header which matches the cache's collection of rdfizers
     */
    public String getAcceptHeader() {
        if (null == acceptHeader) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;

            // Order from highest quality to lowest.
            Comparator<MediaTypeInfo> comparator
                    = (first1, second)
                    -> first1.quality < second.quality ? 1 : first1.quality > second.quality ? -1 : 0;

            MediaTypeInfo[] array = new MediaTypeInfo[rdfizers.size()];
            rdfizers.values().toArray(array);
            Arrays.sort(array, comparator);

            for (MediaTypeInfo m : array) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }

                sb.append(m.mediaType);
                double quality = m.quality;
                if (1.0 != quality) {
                    sb.append(";q=").append(quality);
                }
            }

            acceptHeader = sb.toString();
        }

        return acceptHeader;
    }

    /**
     * Associates an rdfizer with a given media type.
     *
     * @param mediaType     a MIME type, e.g. "application/rdf+xml", "image/tiff"
     * @param rdfizer       the associated rdfizer
     * @param qualityFactor a quality value ranging from 0 to 1 which expresses
     *                      the client's preference for the given media type.
     *                      This value is used for HTTP content negotiation.
     */
    private void addRdfizer(final String mediaType,
                            final Rdfizer rdfizer,
                            final double qualityFactor) {
        logger.info("adding RDFizer for media type " + mediaType + ": " + rdfizer);

        if (qualityFactor <= 0 || qualityFactor > 1) {
            throw new IllegalArgumentException("quality factor must be between 0 and 1");
        }

        if (null != rdfizers.get(mediaType)) {
            logger.warn("overriding already-registered RDFizer for media type " + mediaType);
        }

        MediaTypeInfo rq = new MediaTypeInfo();
        rq.mediaType = mediaType;
        rq.quality = qualityFactor;
        rq.rdfizer = rdfizer;
        rdfizers.put(mediaType, rq);

        acceptHeader = null;
    }

    /**
     * Associates a dereferencer with a given IRI scheme.
     *
     * @param scheme the name of the IRI scheme (e.g. "http", "ftp", "file", "jar")
     * @param dref   the associated dereferencer
     */
    private void addDereferencer(final String scheme, final Dereferencer dref) {
        logger.info("adding dereferencer for for IRI scheme " + scheme + ": " + dref);

        dereferencers.put(scheme, dref);
    }

    private void setDereferencer(final CacheEntry memo, final Dereferencer dref) {

    }

    /**
     * Retrieves caching metadata for a URI, possibly dereferencing a document from the Web first.
     *
     * @param iri            the IRI to look up and possibly dereference
     * @param sailConnection a connection to a Sail
     */
    public void retrieve(final IRI iri,
                         final SailConnection sailConnection) throws IOException {
        // Find the named graph which stores all information associated with this URI
        String graphUri = RDFUtils.findGraphUri(iri.toString());

        CacheEntry memo = getSetMemo(iri, graphUri, sailConnection, true);
        CacheEntry.Status status = null == memo ? null : memo.getStatus();
        if (null == status || status != CacheEntry.Status.CacheLookup) {
            return;
        }
        memo.setStatus(CacheEntry.Status.Undetermined);

        // This IRI should be treated as a "black box" once created;
        // it need not resemble the IRI it was created from.
        String retrievalUri;

        retrievalUri = RDFUtils.removeFragmentIdentifier(iri.toString());

        Dereferencer dref = chooseDereferencer(retrievalUri);
        if (null == dref) return;

        logger.info("dereferencing <" + iri.toString() + ">");

        memo.setDereferencer(dref.getClass().getName());

        // Note: from this point on, we are committed to actually dereferencing the URI,
        // and failures are explicitly stored as caching metadata.
        try {
            retrieveInternal(memo, sailConnection, iri, graphUri, retrievalUri, dref);
        } finally {
            metadata.setMemo(graphUri, memo, sailConnection);

            // an autocommit happens independently of a call to LinkedDataSail#commit
            if (autoCommit) {
                sailConnection.commit();
                sailConnection.begin();
            }

            logStatus(iri, memo);
        }
    }

    // Look up and create the memo for a IRI in one atomic operation, avoiding races between threads
    // The status of a IRI in the cache is Undetermined until the retrieval operation is completed.
    private synchronized CacheEntry getSetMemo(final IRI uri,
                                               final String graphUri,
                                               final SailConnection sc,
                                               final boolean doRetrieve) {
        CacheEntry memo = metadata.getMemo(graphUri, sc);

        // If there is already a (non-expired) entry for this URI, just return its status.
        if (null != memo && !expirationPolicy.isExpired(memo)) {
            return memo;
        }

        if (!doRetrieve) {
            return null;
        }

        memo = new CacheEntry(CacheEntry.Status.Undetermined);
        metadata.setMemo(graphUri, memo, null);
        memo.setStatus(CacheEntry.Status.CacheLookup);
        return memo;
    }

    private void retrieveInternal(final CacheEntry memo,
                                  final SailConnection sailConnection,
                                  final IRI uri,
                                  final String graphUri,
                                  final String retrievalUri,
                                  final Dereferencer dref) throws IOException {
        memo.setStatus(CacheEntry.Status.DereferencerError);

        Representation rep = dref.dereference(retrievalUri);

        // a null representation indicates that dereferencing the IRI would be redundant; exit early
        if (null == rep) {
            memo.setStatus(CacheEntry.Status.RedirectsToCached);
            return;
        }

        // We have the representation, now try to rdfize it.

        memo.setMediaType(rep.getMediaType());

        Rdfizer rdfizer = chooseRdfizer(memo.getMediaType());
        if (null == rdfizer) {
            memo.setStatus(CacheEntry.Status.BadMediaType);
            return;
        }

        memo.setRdfizer(rdfizer.getClass().getName());

        Consumer<Statement> adder = dataStore.createConsumer(sailConnection);
        Buffer<Statement> buffer = new Buffer<>(adder);

        // Note: any context information in the source document is discarded.
        Consumer<Statement> pipe = new SingleContextPipe(buffer, valueFactory.createIRI(graphUri));

        RDFHandler handler = new SesameInputAdapter(useBlankNodes
                ? pipe
                : new BNodeToURIFilter(pipe, valueFactory));

        // Use the namespace portion of the original IRI as the base IRI for the retrieved RDF document.
        String baseUri = uri.getNamespace();

        memo.setStatus(rdfizer.rdfize(rep.getStream(), handler, baseUri));

        // Only update the graph in the triple store if the operation was successful.
        if (CacheEntry.Status.Success == memo.getStatus()) {
            sailConnection.removeStatements(null, null, null, valueFactory.createIRI(graphUri));

            buffer.flush();
        }
    }

    /**
     * @return whether the cache commits to the triple store after each Web request
     * (true by default)
     */
    public boolean isAutoCommit() {
        return autoCommit;
    }

    /**
     * @param autoCommit whether the cache should commit to the triple store after each Web request
     *                   (true by default)
     */
    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    /**
     * @param expirationPolicy the rule by which the cache determines whether a cache entry has expired.
     *                         If an entry has expired, the cache will issue a new request in order to refresh it.
     */
    public void setExpirationPolicy(CacheExpirationPolicy expirationPolicy) {
        this.expirationPolicy = expirationPolicy;
    }

    public boolean getDereferenceSubjects() {
        return derefSubjects;
    }

    public void setDereferenceSubjects(final boolean flag) {
        this.derefSubjects = flag;
    }

    public boolean getDereferencePredicates() {
        return derefPredicates;
    }

    public void setDereferencePredicates(final boolean flag) {
        this.derefPredicates = flag;
    }

    public boolean getDereferenceObjects() {
        return derefObjects;
    }

    public void setDereferenceObjects(final boolean flag) {
        this.derefObjects = flag;
    }

    public boolean getDereferenceContexts() {
        return derefContexts;
    }

    public void setDereferenceContexts(final boolean flag) {
        this.derefContexts = flag;
    }

    private CacheEntry.Status logStatus(final IRI uri,
                                        final CacheEntry memo) {
        CacheEntry.Status status = memo.getStatus();

        if (CacheEntry.Status.Success != status && CacheEntry.Status.RedirectsToCached != status) {
            StringBuilder msg = new StringBuilder("Failed to dereference IRI <"
                    + uri.toString() + "> (");

            msg.append("dereferencer: ").append(memo.getDereferencer());
            msg.append(", media type: ").append(memo.getMediaType());
            msg.append(", rdfizer: ").append(memo.getRdfizer());
            msg.append("): ").append(status);

            logger.info(msg.toString());
        }

        return status;
    }

    private Dereferencer chooseDereferencer(final String uri) {
        String scheme;
        try {
            scheme = new java.net.URI(uri).getScheme();
        } catch (URISyntaxException e) {
            logger.warn("invalid URI: " + uri);
            return null;
        }

        Dereferencer dref = dereferencers.get(scheme);
        if (null == dref) {
            logger.warn("no dereferencer for " + uri);
        }
        return dref;
    }

    private Rdfizer chooseRdfizer(final String mediaType) {
        MediaTypeInfo rq = rdfizers.get(mediaType);
        return (null == rq) ? null : rq.rdfizer;
    }

    private class DefaultCacheExpirationPolicy implements CacheExpirationPolicy {
        private final long cacheLifetime;

        public DefaultCacheExpirationPolicy() {
            cacheLifetime = Long.valueOf(LinkedDataSail.getProperty(LinkedDataSail.CACHE_LIFETIME, "604800")) * 1000L;
        }

        public boolean isExpired(final CacheEntry entry) {
            Date last = entry.getTimestamp();
            return null != last
                    && System.currentTimeMillis() - last.getTime() >= cacheLifetime;
        }
    }

    private class MediaTypeInfo {
        String mediaType;
        public double quality;
        public Rdfizer rdfizer;
    }

    public interface DataStore {
        Consumer<Statement> createConsumer(SailConnection sc);
    }

    private class SingleContextPipe implements Consumer<Statement> {
        private final Consumer<Statement> stSink;
        private final Resource context;

        public SingleContextPipe(final Consumer<Statement> stSink,
                                 final Resource context) {
            this.stSink = stSink;
            this.context = context;
        }

        @Override
        public void accept(Statement st) {
            Statement newSt = valueFactory.createStatement(
                    st.getSubject(), st.getPredicate(), st.getObject(), context);

            stSink.accept(newSt);
        }
    }

    private class Buffer<T> implements Consumer<T> {
        private final Consumer<T> wrapped;
        private final List<T> buffer = new LinkedList<>();

        public Buffer(final Consumer<T> wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void accept(T t) {
            buffer.add(t);
        }

        public void flush() {
            buffer.forEach(wrapped::accept);
            buffer.clear();
        }
    }

    private abstract class StatementConsumerRDFHandler implements RDFHandler {
        @Override
        public void handleComment(final String comment) {
        }

        @Override
        public void handleNamespace(final String prefix, final String uri) {
        }

        @Override
        public void startRDF() {
        }

        @Override
        public void endRDF() {
        }
    }

    private class SesameInputAdapter extends StatementConsumerRDFHandler {
        private final Consumer<Statement> stSink;

        public SesameInputAdapter(final Consumer<Statement> stSink) {
            this.stSink = stSink;
        }

        @Override
        public void handleStatement(final Statement st) {
            stSink.accept(st);
        }
    }

    public class SailInserter extends StatementConsumerRDFHandler {
        private final SailConnection sailConnection;

        public SailInserter(final SailConnection sailConnection) {
            this.sailConnection = sailConnection;
        }

        @Override
        public void handleStatement(final Statement st) throws RDFHandlerException {
            try {
                sailConnection.addStatement(st.getSubject(), st.getPredicate(), st.getObject(), st.getContext());
            } catch (SailException e) {
                throw new RDFHandlerException(e);
            }
        }
    }

    private class SesameOutputAdapter implements Consumer<Statement> {
        private final RDFHandler handler;

        public SesameOutputAdapter(final RDFHandler handler) {
            this.handler = handler;
        }

        @Override
        public void accept(Statement st) {
            handler.handleStatement(st);
        }
    }

    public static abstract class Representation {
        private String mediaType;

        protected Representation(String mediaType) {
            this.mediaType = mediaType;
        }

        public String getMediaType() {
            return mediaType;
        }

        public void setMediaType(final String mediaType) {
            this.mediaType = mediaType;
        }

        public abstract InputStream getStream();
    }
}
