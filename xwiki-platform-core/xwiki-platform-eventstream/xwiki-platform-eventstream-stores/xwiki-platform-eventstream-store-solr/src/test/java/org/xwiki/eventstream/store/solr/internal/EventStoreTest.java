/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.eventstream.store.solr.internal;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xwiki.bridge.event.WikiDeletedEvent;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.environment.Environment;
import org.xwiki.eventstream.Event;
import org.xwiki.eventstream.Event.Importance;
import org.xwiki.eventstream.EventQuery;
import org.xwiki.eventstream.EventSearchResult;
import org.xwiki.eventstream.EventStreamException;
import org.xwiki.eventstream.internal.DefaultEntityEvent;
import org.xwiki.eventstream.internal.DefaultEvent;
import org.xwiki.eventstream.internal.DefaultEventStatus;
import org.xwiki.eventstream.query.SimpleEventQuery;
import org.xwiki.eventstream.query.SortableEventQuery.SortClause.Order;
import org.xwiki.model.internal.reference.converter.EntityReferenceConverter;
import org.xwiki.model.internal.reference.converter.WikiReferenceConverter;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.EventListener;
import org.xwiki.search.solr.test.SolrComponentList;
import org.xwiki.test.annotation.AfterComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.XWikiTempDir;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.internal.model.reference.DocumentReferenceConverter;
import com.xpn.xwiki.internal.model.reference.SpaceReferenceConverter;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Validate {@link SolrEventStore}, {@link EventsSolrCoreInitializer} and {@link WikiDeletedListener}.
 * 
 * @version $Id$
 */
@ComponentTest
@ComponentList({EventsSolrCoreInitializer.class, WikiDeletedListener.class, WikiReferenceConverter.class,
    SpaceReferenceConverter.class, DocumentReferenceConverter.class, EntityReferenceConverter.class})
@ReferenceComponentList
@SolrComponentList
public class EventStoreTest
{
    private static final DefaultEvent EVENT1 = event("id1");

    private static final DefaultEvent EVENT2 = event("id2");

    private static final DefaultEvent EVENT3 = event("id3");

    private static final DefaultEvent EVENT4 = event("id4");

    private static final WikiReference WIKI_REFERENCE = new WikiReference("wiki");

    private static final WikiReference WIKI1_REFERENCE = new WikiReference("wiki1");

    private static final WikiReference WIKI2_REFERENCE = new WikiReference("wiki2");

    private static final SpaceReference SPACE_REFERENCE = new SpaceReference("space", WIKI_REFERENCE);

    private static final SpaceReference SPACE1_REFERENCE = new SpaceReference("space1", WIKI_REFERENCE);

    private static final SpaceReference SPACE2_REFERENCE = new SpaceReference("space2", WIKI_REFERENCE);

    private static final DocumentReference DOCUMENT_REFERENCE = new DocumentReference("document", SPACE_REFERENCE);

    private static final DocumentReference USER_REFERENCE = new DocumentReference("user", SPACE_REFERENCE);

    private static final DocumentReference RELATED_REFERENCE = new DocumentReference("related", SPACE_REFERENCE);

    private static final String SPACE_STRING = "wiki:space";

    private static final String SPACE1_STRING = "wiki1:space1";

    private static final String SPACE2_STRING = "wiki2:space2";

    private static final String DOCUMENT_STRING = "wiki:space.document";

    private static final String USER_STRING = "wiki:space.user";

    private static final String RELATED_STRING = "wiki:space.related";

    @XWikiTempDir
    private File permanentDirectory;

    private ConfigurationSource mockXWikiProperties;

    private Environment mockEnvironment;

    @MockComponent
    private WikiDescriptorManager wikis;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private SolrEventStore eventStore;

    @AfterComponent
    public void afterComponent() throws Exception
    {
        this.mockXWikiProperties =
            this.componentManager.registerMockComponent(ConfigurationSource.class, "xwikiproperties");
        this.mockEnvironment = this.componentManager.registerMockComponent(Environment.class);
        when(this.mockXWikiProperties.getProperty(anyString(), anyString())).then(new Answer<String>()
        {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable
            {
                return invocation.getArgument(1);
            }
        });

        when(this.mockEnvironment.getPermanentDirectory()).thenReturn(this.permanentDirectory);
        FileUtils.deleteDirectory(this.permanentDirectory);
        this.permanentDirectory.mkdirs();
    }

    private static DefaultEvent event(String id)
    {
        return event(id, null);
    }

    private static DefaultEvent event(String id, Date date)
    {
        DefaultEvent event = new DefaultEvent();

        event.setId(id);
        event.setDate(date);

        return event;
    }

    private static DefaultEventStatus eventstatus(Event event, String entityId, boolean read)
    {
        return new DefaultEventStatus(event, entityId, read);
    }

    private static DefaultEntityEvent entityevent(Event event, String entityId)
    {
        return new DefaultEntityEvent(event, entityId);
    }

    private EventSearchResult assertSearch(Collection<Event> expected, EventQuery query) throws EventStreamException
    {
        EventSearchResult result = this.eventStore.search(query);

        assertEquals(new HashSet<>(expected), result.stream().collect(Collectors.toSet()));

        return result;
    }

    private void assertEqualsResult(Collection<Event> expected, EventSearchResult result) throws EventStreamException
    {
        assertEquals(new HashSet<>(expected), result.stream().collect(Collectors.toSet()));
    }

    // Tests

    @Test
    public void saveGetDeleteEvent() throws Exception
    {
        assertFalse(this.eventStore.getEvent("id").isPresent());

        DefaultEvent event = event("id");

        event.setApplication("application");
        event.setBody("body");
        event.setDate(new Date());
        event.setDocument(DOCUMENT_REFERENCE);
        event.setDocumentTitle("doctitle");
        event.setDocumentVersion("version");
        event.setGroupId("groupid");
        event.setHidden(true);
        event.setImportance(Importance.CRITICAL);
        event.setPrefiltered(true);
        event.setRelatedEntity(RELATED_REFERENCE);
        event.setSpace(SPACE1_REFERENCE);
        event.setStream("stream");
        event.setTarget(SetUtils.hashSet("target1", "target2"));
        event.setTitle("title");
        event.setType("type");
        event.setUrl(new URL("http://path"));
        event.setUser(USER_REFERENCE);
        event.setWiki(WIKI2_REFERENCE);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", "value2");
        event.setParameters(parameters);

        this.eventStore.saveEvent(event).get();

        Optional<Event> storedEvent = this.eventStore.getEvent("id");
        assertTrue(storedEvent.isPresent());

        assertEquals(event, storedEvent.get());

        this.eventStore.deleteEvent(event).get();

        assertFalse(this.eventStore.getEvent("id").isPresent());

        this.eventStore.saveEvent(event).get();

        assertEquals(event, this.eventStore.getEvent("id").get());

        Optional<Event> deleted = this.eventStore.deleteEvent("id").get();

        assertEquals(event, deleted.get());

        assertFalse(this.eventStore.getEvent("id").isPresent());
    }

    @Test
    public void saveDeleteEventStatuses() throws Exception
    {
        Date date0 = new Date(0);
        Date date10 = new Date(10);
        Date date20 = new Date(20);
        Date date30 = new Date(30);
        Date date40 = new Date(40);

        EVENT1.setDate(date10);
        EVENT2.setDate(date20);
        EVENT3.setDate(date30);
        EVENT4.setDate(date40);

        this.eventStore.saveEvent(EVENT1);
        this.eventStore.saveEvent(EVENT2);
        this.eventStore.saveEvent(EVENT3);
        this.eventStore.saveEvent(EVENT4);

        DefaultEventStatus status11 = eventstatus(EVENT1, "entity1", true);
        DefaultEventStatus status12 = eventstatus(EVENT1, "entity2", false);
        DefaultEventStatus status21 = eventstatus(EVENT2, "entity1", false);
        DefaultEventStatus status22 = eventstatus(EVENT2, "entity3", true);
        DefaultEventStatus status31 = eventstatus(EVENT3, "entity1", true);
        DefaultEventStatus status41 = eventstatus(EVENT4, "entity1", true);

        this.eventStore.saveEventStatus(status11);
        this.eventStore.saveEventStatus(status12);
        this.eventStore.saveEventStatus(status21);
        this.eventStore.saveEventStatus(status22);
        this.eventStore.saveEventStatus(status31);
        this.eventStore.saveEventStatus(status41).get();

        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().withStatus("entity2"));

        this.eventStore.deleteEventStatus(status12).get();

        assertSearch(Arrays.asList(), new SimpleEventQuery().withStatus("entity2"));

        assertSearch(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), new SimpleEventQuery().withStatus("entity1"));

        this.eventStore.deleteEventStatuses("entity1", date0).get();

        assertSearch(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), new SimpleEventQuery().withStatus("entity1"));

        this.eventStore.deleteEventStatuses("entity1", date20).get();

        assertSearch(Arrays.asList(EVENT3, EVENT4), new SimpleEventQuery().withStatus("entity1"));
    }

    @Test
    public void allSearch()
        throws EventStreamException, InterruptedException, ExecutionException, SolrServerException, IOException
    {
        // Misc

        searchMisc();

        // Status

        searchStatus();

        // Mail

        searchMail();

        // Reference

        searchReference();

        // Fields

        searchFields();
    }

    public void searchMisc() throws EventStreamException, InterruptedException, ExecutionException
    {
        EVENT1.setHidden(true);
        EVENT2.setHidden(true);
        EVENT3.setHidden(false);
        EVENT4.setHidden(false);

        EVENT1.setUser(new DocumentReference("wiki", "space", "user1"));
        EVENT2.setUser(new DocumentReference("wiki", "space", "user2"));
        EVENT3.setUser(new DocumentReference("wiki", "space", "user3"));
        EVENT4.setUser(new DocumentReference("wiki", "space", "user4"));

        this.eventStore.saveEvent(EVENT1);
        this.eventStore.saveEvent(EVENT2);
        this.eventStore.saveEvent(EVENT3);
        this.eventStore.saveEvent(EVENT4);
        this.eventStore.prefilterEvent(EVENT1).get();

        assertSearch(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), new SimpleEventQuery());
        assertSearch(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), new SimpleEventQuery().setLimit(-1));
        assertSearch(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), new SimpleEventQuery().setOffset(-1));
        assertSearch(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), new SimpleEventQuery().setOffset(0));

        assertSearch(Arrays.asList(), new SimpleEventQuery().setLimit(0));
        assertSearch(Arrays.asList(EVENT2, EVENT3, EVENT4),
            new SimpleEventQuery().setOffset(1).addSort(Event.FIELD_ID, Order.ASC));

        SimpleEventQuery query = new SimpleEventQuery();
        query.eq(Event.FIELD_ID, "id1");
        assertSearch(Arrays.asList(EVENT1), query);

        query.eq(Event.FIELD_ID, "id2");
        assertSearch(Arrays.asList(), query);

        query = new SimpleEventQuery();
        query.not().eq(Event.FIELD_ID, "id2");
        assertSearch(Arrays.asList(EVENT1, EVENT3, EVENT4), query);

        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().eq(Event.FIELD_HIDDEN, true));

        assertSearch(Arrays.asList(EVENT4), new SimpleEventQuery().eq(Event.FIELD_USER, EVENT4.getUser()));

        assertSearch(Arrays.asList(EVENT1, EVENT2),
            new SimpleEventQuery().eq(Event.FIELD_ID, EVENT1.getId()).or().eq(Event.FIELD_ID, EVENT2.getId()));

        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().eq(Event.FIELD_ID, EVENT1.getId()).open()
            .eq(Event.FIELD_ID, EVENT1.getId()).or().eq(Event.FIELD_ID, EVENT2.getId()).close());

        assertSearch(Arrays.asList(EVENT1, EVENT2),
            new SimpleEventQuery().in(Event.FIELD_ID, EVENT1.getId(), EVENT2.getId()));

        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().eq(Event.FIELD_PREFILTERED, true));
        assertSearch(Arrays.asList(EVENT2, EVENT3, EVENT4), new SimpleEventQuery().eq(Event.FIELD_PREFILTERED, false));
    }

    @Test
    public void searchDate() throws EventStreamException, InterruptedException, ExecutionException
    {
        Date date0 = new Date(0);
        Date date10 = new Date(10);
        Date date20 = new Date(20);
        Date date30 = new Date(30);
        Date date40 = new Date(40);

        DefaultEvent event10 = event("id10", date10);
        DefaultEvent event30 = event("id30", date30);

        this.eventStore.saveEvent(event10);
        this.eventStore.saveEvent(event30).get();

        SimpleEventQuery query = new SimpleEventQuery();
        query.after(date0);
        assertSearch(Arrays.asList(event10, event30), query);

        query = new SimpleEventQuery();
        query.after(date10);
        assertSearch(Arrays.asList(event30), query);

        query = new SimpleEventQuery();
        query.after(date20);
        assertSearch(Arrays.asList(event30), query);

        query = new SimpleEventQuery();
        query.after(date30);
        assertSearch(Arrays.asList(), query);

        query = new SimpleEventQuery();
        query.after(date40);
        assertSearch(Arrays.asList(), query);

        query = new SimpleEventQuery();
        query.before(date0);
        assertSearch(Arrays.asList(), query);

        query = new SimpleEventQuery();
        query.before(date10);
        assertSearch(Arrays.asList(), query);

        query = new SimpleEventQuery();
        query.before(date20);
        assertSearch(Arrays.asList(event10), query);

        query = new SimpleEventQuery();
        query.before(date30);
        assertSearch(Arrays.asList(event10), query);

        query = new SimpleEventQuery();
        query.before(date40);
        assertSearch(Arrays.asList(event10, event30), query);

        query = new SimpleEventQuery();
        query.lessOrEq(Event.FIELD_DATE, date30);
        assertSearch(Arrays.asList(event10, event30), query);

        query = new SimpleEventQuery();
        query.greaterOrEq(Event.FIELD_DATE, date10);
        assertSearch(Arrays.asList(event10, event30), query);

        query = new SimpleEventQuery();
        query.addSort(Event.FIELD_DATE, Order.ASC);
        assertSearch(Arrays.asList(event10, event30), query);

        query = new SimpleEventQuery();
        query.addSort(Event.FIELD_DATE, Order.DESC);
        assertSearch(Arrays.asList(event30, event10), query);

        query = new SimpleEventQuery();
        query.setOffset(0);
        query.setLimit(1);
        EventSearchResult result = assertSearch(Arrays.asList(event10), query);
        assertEquals(0, result.getOffset());
        assertEquals(1, result.getSize());
        assertEquals(2, result.getTotalHits());

        query = new SimpleEventQuery();
        query.setOffset(1);
        query.setLimit(42);
        result = assertSearch(Arrays.asList(event30), query);
        assertEquals(1, result.getOffset());
        assertEquals(1, result.getSize());
        assertEquals(2, result.getTotalHits());
    }

    public void searchReference() throws EventStreamException, InterruptedException, ExecutionException
    {
        DocumentReference document1 = new DocumentReference("document1", SPACE_REFERENCE);
        DocumentReference document2 = new DocumentReference("document2", SPACE_REFERENCE);
        String documentString1 = "wiki:space.document1";
        String documentString2 = "wiki:space.document2";

        EVENT1.setWiki(WIKI_REFERENCE);
        EVENT1.setSpace(SPACE_REFERENCE);
        EVENT1.setDocument(document1);
        EVENT2.setWiki(WIKI_REFERENCE);
        EVENT2.setSpace(SPACE_REFERENCE);
        EVENT2.setDocument(document2);

        this.eventStore.saveEvent(EVENT1);
        this.eventStore.saveEvent(EVENT2).get();

        assertSearch(Arrays.asList(EVENT1, EVENT2),
            new SimpleEventQuery().eq(Event.FIELD_WIKI, WIKI_REFERENCE.getName()));
        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().eq(Event.FIELD_SPACE, SPACE_STRING));
        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().eq(Event.FIELD_SPACE, SPACE_REFERENCE));
        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().eq(Event.FIELD_SPACE, "space"));
        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().eq(Event.FIELD_DOCUMENT, "document1"));
        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().eq(Event.FIELD_DOCUMENT, "space.document1"));
        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().eq(Event.FIELD_DOCUMENT, documentString1));
        assertSearch(Arrays.asList(EVENT2), new SimpleEventQuery().eq(Event.FIELD_DOCUMENT, "document2"));
        assertSearch(Arrays.asList(EVENT2), new SimpleEventQuery().eq(Event.FIELD_DOCUMENT, "space.document2"));
        assertSearch(Arrays.asList(EVENT2), new SimpleEventQuery().eq(Event.FIELD_DOCUMENT, documentString2));
    }

    public void searchStatus()
        throws EventStreamException, InterruptedException, ExecutionException, SolrServerException, IOException
    {
        DefaultEventStatus status11 = eventstatus(EVENT1, "entity1", true);
        DefaultEventStatus status12 = eventstatus(EVENT1, "entity2", false);
        DefaultEventStatus status21 = eventstatus(EVENT2, "entity1", false);
        DefaultEventStatus status22 = eventstatus(EVENT2, "entity3", true);

        this.eventStore.saveEventStatus(status11);
        this.eventStore.saveEventStatus(status12);
        this.eventStore.saveEventStatus(status21);
        this.eventStore.saveEventStatus(status22).get();

        SolrDocument document1 = this.eventStore.getEventDocument(EVENT1.getId());
        assertEquals(Arrays.asList("entity1"), document1.get(EventsSolrCoreInitializer.SOLR_FIELD_READLISTENERS));
        assertEquals(Arrays.asList("entity2"), document1.get(EventsSolrCoreInitializer.SOLR_FIELD_UNREADLISTENERS));

        SolrDocument document2 = this.eventStore.getEventDocument(EVENT2.getId());
        assertEquals(Arrays.asList("entity3"), document2.get(EventsSolrCoreInitializer.SOLR_FIELD_READLISTENERS));
        assertEquals(Arrays.asList("entity1"), document2.get(EventsSolrCoreInitializer.SOLR_FIELD_UNREADLISTENERS));

        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().withStatus(true));

        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().withStatus(false));

        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().withStatus("entity1"));

        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().withStatus("entity2"));

        assertSearch(Arrays.asList(EVENT2), new SimpleEventQuery().withStatus("entity3"));

        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().withStatus("entity1", true));

        assertSearch(Arrays.asList(EVENT2), new SimpleEventQuery().withStatus("entity1", false));
    }

    public void searchMail()
        throws EventStreamException, InterruptedException, ExecutionException, SolrServerException, IOException
    {
        DefaultEntityEvent mail11 = entityevent(EVENT1, "entity1");
        DefaultEntityEvent mail12 = entityevent(EVENT1, "entity2");
        DefaultEntityEvent mail21 = entityevent(EVENT2, "entity1");
        DefaultEntityEvent mail22 = entityevent(EVENT2, "entity3");

        this.eventStore.saveMailEntityEvent(mail11);
        this.eventStore.saveMailEntityEvent(mail12);
        this.eventStore.saveMailEntityEvent(mail21);
        this.eventStore.saveMailEntityEvent(mail22).get();

        SolrDocument document1 = this.eventStore.getEventDocument(EVENT1.getId());
        assertEquals(Arrays.asList("entity1"), document1.get(EventsSolrCoreInitializer.SOLR_FIELD_READLISTENERS));
        assertEquals(Arrays.asList("entity2"), document1.get(EventsSolrCoreInitializer.SOLR_FIELD_UNREADLISTENERS));

        SolrDocument document2 = this.eventStore.getEventDocument(EVENT2.getId());
        assertEquals(Arrays.asList("entity3"), document2.get(EventsSolrCoreInitializer.SOLR_FIELD_READLISTENERS));
        assertEquals(Arrays.asList("entity1"), document2.get(EventsSolrCoreInitializer.SOLR_FIELD_UNREADLISTENERS));

        assertSearch(Arrays.asList(EVENT1, EVENT2), new SimpleEventQuery().withMail("entity1"));

        assertSearch(Arrays.asList(EVENT1), new SimpleEventQuery().withMail("entity2"));

        assertSearch(Arrays.asList(EVENT2), new SimpleEventQuery().withMail("entity3"));
    }

    private void searchFields() throws EventStreamException
    {
        SimpleEventQuery query = new SimpleEventQuery();

        assertEqualsResult(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), this.eventStore.search(query, null));
        assertEqualsResult(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4),
            this.eventStore.search(query, Collections.emptySet()));

        Map<String, Event> events = this.eventStore.search(query, Collections.singleton(Event.FIELD_ID)).stream()
            .collect(Collectors.toMap(Event::getId, Function.identity()));

        assertEquals(SetUtils.hashSet(EVENT1.getId(), EVENT2.getId(), EVENT3.getId(), EVENT4.getId()), events.keySet());

        Event event1 = events.get(EVENT1.getId());

        assertNotEquals(EVENT1, event1);
        assertFalse(event1.isPrefiltered());
        assertFalse(event1.getHidden());
    }

    @Test
    public void deleteWiki()
        throws EventStreamException, InterruptedException, ExecutionException, ComponentLookupException
    {
        EVENT1.setWiki(new WikiReference("wikia"));
        EVENT2.setWiki(new WikiReference("wikia"));
        EVENT3.setWiki(new WikiReference("wikib"));
        EVENT4.setWiki(new WikiReference("wikic"));

        this.eventStore.saveEvent(EVENT1);
        this.eventStore.saveEvent(EVENT2);
        this.eventStore.saveEvent(EVENT3);
        this.eventStore.saveEvent(EVENT4).get();

        assertSearch(Arrays.asList(EVENT1, EVENT2, EVENT3, EVENT4), new SimpleEventQuery());

        WikiDeletedListener listener = this.componentManager.getInstance(EventListener.class, WikiDeletedListener.NAME);

        listener.onEvent(new WikiDeletedEvent("wikia"), null, null);

        assertSearch(Arrays.asList(EVENT3, EVENT4), new SimpleEventQuery());
    }
}