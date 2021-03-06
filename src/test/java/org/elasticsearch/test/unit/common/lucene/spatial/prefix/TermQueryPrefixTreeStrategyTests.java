package org.elasticsearch.test.unit.common.lucene.spatial.prefix;

import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.context.jts.JtsSpatialContext;
import com.spatial4j.core.distance.DistanceUnits;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.Shape;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;
import org.elasticsearch.common.lucene.spatial.prefix.TermQueryPrefixTreeStrategy;
import org.elasticsearch.common.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.elasticsearch.common.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.elasticsearch.common.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.elasticsearch.index.mapper.FieldMapper;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.elasticsearch.common.geo.ShapeBuilder.*;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link TermQueryPrefixTreeStrategy}
 */
public class TermQueryPrefixTreeStrategyTests {

    private static final SpatialContext SPATIAL_CONTEXT = new JtsSpatialContext(DistanceUnits.KILOMETERS);

    // TODO: Randomize the implementation choice
    private static final SpatialPrefixTree QUAD_PREFIX_TREE =
            new QuadPrefixTree(SPATIAL_CONTEXT, QuadPrefixTree.DEFAULT_MAX_LEVELS);
    private static final SpatialPrefixTree GEOHASH_PREFIX_TREE
            = new GeohashPrefixTree(SPATIAL_CONTEXT, GeohashPrefixTree.getMaxLevelsPossible());

    private static final TermQueryPrefixTreeStrategy STRATEGY = new TermQueryPrefixTreeStrategy(new FieldMapper.Names("shape"), GEOHASH_PREFIX_TREE, 0.025);

    private Directory directory;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;

    @BeforeTest
    public void setUp() throws IOException {
        directory = new RAMDirectory();
        IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(Version.LUCENE_36, new KeywordAnalyzer()));

        writer.addDocument(newDocument("1", newPoint(-30, -30)));
        writer.addDocument(newDocument("2", newPoint(-45, -45)));
        writer.addDocument(newDocument("3", newPoint(-45, 50)));
        writer.addDocument(newDocument("4", newRectangle().topLeft(-50, 50).bottomRight(-38, 38).build()));

        indexReader = IndexReader.open(writer, true);
        indexSearcher = new IndexSearcher(indexReader);
    }

    private Document newDocument(String id, Shape shape) {
        Document document = new Document();
        document.add(new Field("id", id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        document.add(STRATEGY.createField(shape));
        return document;
    }

    private void assertTopDocs(TopDocs topDocs, String... ids) throws IOException {
        assertTrue(ids.length <= topDocs.totalHits, "Query has more hits than expected");

        Set<String> foundIDs = new HashSet<String>();
        for (ScoreDoc doc : topDocs.scoreDocs) {
            Document foundDocument = indexSearcher.doc(doc.doc);
            foundIDs.add(foundDocument.getFieldable("id").stringValue());
        }

        for (String id : ids) {
            assertTrue(foundIDs.contains(id), "ID [" + id + "] was not found in query results");
        }
    }

    @Test
    public void testIntersectionRelation() throws IOException {
        Rectangle rectangle = newRectangle().topLeft(-45, 45).bottomRight(45, -45).build();

        Filter filter = STRATEGY.createIntersectsFilter(rectangle);
        assertTopDocs(indexSearcher.search(new MatchAllDocsQuery(), filter, 10), "1", "2", "4");

        Query query = STRATEGY.createIntersectsQuery(rectangle);
        assertTopDocs(indexSearcher.search(query, 10), "1", "2", "4");

        Shape polygon = newPolygon()
                .point(-45, 45)
                .point(45, 45)
                .point(45, -45)
                .point(-45, -45)
                .point(-45, 45).build();

        filter = STRATEGY.createIntersectsFilter(polygon);
        assertTopDocs(indexSearcher.search(new MatchAllDocsQuery(), filter, 10), "1", "2", "4");

        query = STRATEGY.createIntersectsQuery(polygon);
        assertTopDocs(indexSearcher.search(query, 10), "1", "2", "4");
    }

    @Test
    public void testDisjointRelation() throws IOException {
        Rectangle rectangle = newRectangle().topLeft(-45, 45).bottomRight(45, -45).build();

        Filter filter = STRATEGY.createDisjointFilter(rectangle);
        assertTopDocs(indexSearcher.search(new MatchAllDocsQuery(), filter, 10), "3");

        Query query = STRATEGY.createDisjointQuery(rectangle);
        assertTopDocs(indexSearcher.search(query, 10), "3");

        Shape polygon = newPolygon()
                .point(-45, 45)
                .point(45, 45)
                .point(45, -45)
                .point(-45, -45)
                .point(-45, 45).build();

        filter = STRATEGY.createDisjointFilter(polygon);
        assertTopDocs(indexSearcher.search(new MatchAllDocsQuery(), filter, 10), "3");

        query = STRATEGY.createDisjointQuery(polygon);
        assertTopDocs(indexSearcher.search(query, 10), "3");
    }

    @Test
    public void testContainsRelation() throws IOException {
        Rectangle rectangle = newRectangle().topLeft(-45, 45).bottomRight(45, -45).build();

        Filter filter = STRATEGY.createContainsFilter(rectangle);
        assertTopDocs(indexSearcher.search(new MatchAllDocsQuery(), filter, 10), "1");

        Query query = STRATEGY.createContainsQuery(rectangle);
        assertTopDocs(indexSearcher.search(query, 10), "1");

        Shape polygon = newPolygon()
                .point(-45, 45)
                .point(45, 45)
                .point(45, -45)
                .point(-45, -45)
                .point(-45, 45).build();

        filter = STRATEGY.createContainsFilter(polygon);
        assertTopDocs(indexSearcher.search(new MatchAllDocsQuery(), filter, 10), "1");

        query = STRATEGY.createContainsQuery(polygon);
        assertTopDocs(indexSearcher.search(query, 10), "1");
    }

    @AfterTest
    public void tearDown() throws IOException {
        IOUtils.close(indexSearcher, indexReader, directory);
    }
}
