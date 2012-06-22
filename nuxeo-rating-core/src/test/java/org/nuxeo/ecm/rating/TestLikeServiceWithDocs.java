package org.nuxeo.ecm.rating;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.nuxeo.ecm.activity.ActivityHelper.createActivityObject;
import static org.nuxeo.ecm.activity.ActivityHelper.createDocumentActivityObject;
import static org.nuxeo.ecm.activity.ActivityHelper.createUserActivityObject;
import static org.nuxeo.ecm.rating.LikeServiceImpl.LIKE_RATING;
import static org.nuxeo.ecm.rating.LikesCountActivityStreamFilter.ACTOR_PARAMETER;
import static org.nuxeo.ecm.rating.LikesCountActivityStreamFilter.ASPECT_PARAMETER;
import static org.nuxeo.ecm.rating.LikesCountActivityStreamFilter.CONTEXT_PARAMETER;
import static org.nuxeo.ecm.rating.LikesCountActivityStreamFilter.OBJECT_PARAMETER;
import static org.nuxeo.ecm.rating.LikesCountActivityStreamFilter.QueryType.GET_DOCUMENTS_COUNT;
import static org.nuxeo.ecm.rating.LikesCountActivityStreamFilter.QueryType.GET_MINI_MESSAGE_COUNT;
import static org.nuxeo.ecm.rating.RatingActivityStreamFilter.QUERY_TYPE_PARAMETER;
import static org.nuxeo.ecm.rating.api.Constants.LIKE_ASPECT;
import static org.nuxeo.ecm.rating.api.Constants.RATING_VERB_PREFIX;

import java.io.Serializable;
import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.inject.Inject;
import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.activity.ActivitiesList;
import org.nuxeo.ecm.activity.Activity;
import org.nuxeo.ecm.activity.ActivityBuilder;
import org.nuxeo.ecm.activity.ActivityHelper;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.usermanager.exceptions.UserAlreadyExistsException;
import org.nuxeo.ecm.social.mini.message.MiniMessage;
import org.nuxeo.ecm.social.mini.message.MiniMessageService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @author <a href="mailto:akervern@nuxeo.com">Arnaud Kervern</a>
 * @since 5.6
 */
@RunWith(FeaturesRunner.class)
@Features({ RatingFeature.class })
@RepositoryConfig(repositoryName = "default", cleanup = Granularity.METHOD, init = DefaultRepositoryInit.class)
@Deploy({ "org.nuxeo.ecm.social.mini.message",
        "org.nuxeo.ecm.platform.url.core",
        "org.nuxeo.ecm.platform.ui:OSGI-INF/urlservice-framework.xml",
        "org.nuxeo.ecm.user.center:OSGI-INF/urlservice-contrib.xml" })
@LocalDeploy("org.nuxeo.ecm.rating.core:rating-test.xml")
public class TestLikeServiceWithDocs extends AbstractRatingTest {
    @Inject
    protected UserManager userManager;

    @Inject
    protected MiniMessageService mms;

    @Test
    public void shouldHandleSuperSpaceCount() throws ClientException {
        DocumentModel folder = session.createDocumentModel(
                "/default-domain/workspaces/", "folder", "Folder");
        folder = session.createDocument(folder);
        session.save();

        DocumentModel testDoc = createTestDocument("myDoc",
                "/default-domain/workspaces/folder");
        DocumentModel defaultDomain = session.getDocument(new PathRef(
                "/default-domain"));
        DocumentModel workspaces = session.getDocument(new PathRef(
                "/default-domain/workspaces"));

        likeService.like("robin", testDoc);
        likeService.like("barney", testDoc);
        likeService.dislike("Ted", testDoc);

        assertEquals(3, ratingService.getRatesCount(
                createDocumentActivityObject(testDoc), LIKE_ASPECT));

        // Only on SuperSpace
        assertEquals(
                0,
                ratingService.getRatedChildren(
                        createDocumentActivityObject(folder), LIKE_RATING,
                        LIKE_ASPECT).size());

        assertEquals(
                2,
                ratingService.getRatedChildren(
                        createDocumentActivityObject(defaultDomain),
                        LIKE_RATING, LIKE_ASPECT).size());
        assertEquals(
                2,
                ratingService.getRatedChildren(
                        createDocumentActivityObject(workspaces), LIKE_RATING,
                        LIKE_ASPECT).size());

        // All activities correctly removed
        likeService.cancel("robin", testDoc);
        assertEquals(2, ratingService.getRatesCount(
                createDocumentActivityObject(testDoc), LIKE_ASPECT));
        assertEquals(
                1,
                ratingService.getRatedChildren(
                        createDocumentActivityObject(defaultDomain),
                        LIKE_RATING, LIKE_ASPECT).size());
    }

    @Test
    public void shouldCorrectlyReturnedMostLikedDocumentList()
            throws ClientException {
        DocumentModel workspaces = session.getDocument(new PathRef(
                "/default-domain/workspaces"));

        DocumentModel doc1 = createTestDocument("tmp",
                "/default-domain/workspaces/test");
        DocumentModel doc2 = createTestDocument("tmp1",
                "/default-domain/workspaces/test");
        DocumentModel doc3 = createTestDocument("tmp2",
                "/default-domain/workspaces/test");
        DocumentModel doc4 = createTestDocument("tmp3",
                "/default-domain/workspaces/test");

        likeService.like("robin", doc3); // 3-1
        likeService.like("robin", doc4); // 4-1

        likeService.like("barney", doc1); // 1-1
        likeService.like("barney", doc3); // 3-2
        likeService.like("barney", doc4); // 4-2

        likeService.like("ted", doc2); // 2-1

        likeService.like("marshal", doc3); // 3-3
        likeService.like("marshal", doc4); // 4-3

        likeService.like("lily", doc3); // 3-4

        session.save();

        // order should be: doc3 - doc4 - doc1 - doc2
        ActivitiesList mostLikedDocuments = likeService.getMostLikedActivities(
                session, 10, workspaces);
        assertEquals(4, mostLikedDocuments.size());

        assertEquals(
                doc3.getId(),
                ActivityHelper.getDocumentId(mostLikedDocuments.get(0).getTarget()));
        assertEquals("4", mostLikedDocuments.get(0).getObject());
        assertEquals(
                doc4.getId(),
                ActivityHelper.getDocumentId(mostLikedDocuments.get(1).getTarget()));
        assertEquals("3", mostLikedDocuments.get(1).getObject());

        // doc1 and doc2 may be inverted
        assertEquals("1", mostLikedDocuments.get(2).getObject());
        assertEquals("1", mostLikedDocuments.get(3).getObject());

        mostLikedDocuments = likeService.getMostLikedActivities(session, 2,
                workspaces);
        assertEquals(2, mostLikedDocuments.size());
    }

    @Test
    public void shouldCountDocumentsAndMiniMessages() throws ClientException {
        // Create some docs
        DocumentModel context = session.getDocument(new PathRef(
                "/default-domain/workspaces/test"));
        String activityContext = createDocumentActivityObject(context);
        DocumentModel lovelyDoc = createTestDocument("myLovelyDoc",
                context.getPathAsString()); // 3 likes

        assertNotNull(mms);

        Principal jeannot = createUser("jeannot");
        Principal steve = createUser("steve");

        // Create some minimessages
        MiniMessage miniMessage1 = mms.addMiniMessage(steve,
                "This is a revolution.", new Date(), activityContext);
        MiniMessage miniMessage2 = mms.addMiniMessage(steve, "One more thing.",
                new Date(), activityContext);
        MiniMessage miniMessage3 = mms.addMiniMessage(jeannot,
                "My daddy is awesome.", new Date(), activityContext);

        String actMiniMessage1 = createActivityObject(miniMessage1.getId()); // 4
                                                                             // likes
        String actMiniMessage2 = createActivityObject(miniMessage2.getId()); // 2
                                                                             // likes
        String actMiniMessage3 = createActivityObject(miniMessage3.getId()); // 1
                                                                             // like

        // like them all.
        likeService.like("Tim", actMiniMessage1);
        likeService.like("Steve", actMiniMessage1);
        likeService.like("Bill", actMiniMessage1);
        likeService.like("Woj.", actMiniMessage1);

        likeService.like("Thomas", lovelyDoc);
        likeService.like("Li", lovelyDoc);
        likeService.like("Steve", lovelyDoc);

        likeService.like("Vlad", actMiniMessage2);
        likeService.like("Sun", actMiniMessage2);

        likeService.like("Nico", actMiniMessage3);

        // Check them all!
        ActivitiesList mostLikedActivities = likeService.getMostLikedActivities(
                session, 3, context);
        assertEquals(3, mostLikedActivities.size());
        Activity firstOne = mostLikedActivities.get(0);
        assertEquals(actMiniMessage1, firstOne.getTarget());
        assertEquals("4", firstOne.getObject());

        Activity secondOne = mostLikedActivities.get(1);
        assertEquals(createDocumentActivityObject(lovelyDoc),
                secondOne.getTarget());
        assertEquals("3", secondOne.getObject());

        Activity last = mostLikedActivities.get(2);
        assertEquals(actMiniMessage2, last.getTarget());
        assertEquals("2", last.getObject());
    }

    @Test
    public void shouldHandleMostLikedBetweenTwoDates() throws ClientException {
        DocumentModel context = session.getDocument(new PathRef(
                "/default-domain/workspaces/test"));
        DocumentModel lovelyDoc = createTestDocument("lovelyDoc",
                context.getPathAsString());
        DocumentModel anotherLovelyDoc = createTestDocument("anotherLovelyDoc",
                context.getPathAsString());

        Principal jeannot = createUser("Tim");
        MiniMessage miniMessage = mms.addMiniMessage(session.getPrincipal(),
                "My first miniMessage", new Date(),
                createDocumentActivityObject(context));
        MiniMessage miniMessage1 = mms.addMiniMessage(session.getPrincipal(),
                "My second miniMessage", new Date(),
                createDocumentActivityObject(context));

        final DateTime YESTERDAY = new DateTime().minusDays(1);
        final DateTime TODAY = new DateTime();
        final DateTime TOMORROW = new DateTime().plusDays(1);

        String contextObject = createDocumentActivityObject(context);
        // Likes now
        String currentActivity = createActivityObject(miniMessage.getId());
        manuallyLike("user0", currentActivity, YESTERDAY, null);
        manuallyLike("user1", currentActivity, TODAY, null);

        currentActivity = createActivityObject(miniMessage1.getId());
        manuallyLike("user0", currentActivity, YESTERDAY, null);
        manuallyLike("user1", currentActivity, TODAY, null);
        manuallyLike("user2", currentActivity, TOMORROW, null);

        currentActivity = createDocumentActivityObject(lovelyDoc);
        manuallyLike("user0", currentActivity, YESTERDAY, contextObject);
        manuallyLike("user1", currentActivity, YESTERDAY, contextObject);

        currentActivity = createDocumentActivityObject(anotherLovelyDoc);
        manuallyLike("user0", currentActivity, TOMORROW, contextObject);
        manuallyLike("user1", currentActivity, TOMORROW, contextObject);

        // Tests now !
        ActivitiesList mostLikedActivities = likeService.getMostLikedActivities(
                session, 10, context, YESTERDAY.minusHours(2).toDate(),
                YESTERDAY.plusHours(2).toDate());
        assertEquals(3, mostLikedActivities.size());
        assertEquals(createDocumentActivityObject(lovelyDoc),
                mostLikedActivities.get(0).getTarget());

        mostLikedActivities = likeService.getMostLikedActivities(session, 10,
                context, TOMORROW.minusHours(2).toDate(),
                TOMORROW.plusHours(2).toDate());
        assertEquals(2, mostLikedActivities.size());
        assertEquals(createDocumentActivityObject(anotherLovelyDoc),
                mostLikedActivities.get(0).getTarget());

        mostLikedActivities = likeService.getMostLikedActivities(session, 10,
                context, TODAY.minusHours(2).toDate(),
                TOMORROW.plusHours(2).toDate());
        assertEquals(3, mostLikedActivities.size());

        mostLikedActivities = likeService.getMostLikedActivities(session, 10,
                context, YESTERDAY.minusHours(2).toDate(),
                TOMORROW.plusHours(2).toDate());
        assertEquals(4, mostLikedActivities.size());
    }

    protected void manuallyLike(String username, String activityObject,
            DateTime publishedDate, String contextObject) {
        ActivityBuilder activityBuilder = new ActivityBuilder().verb(
                RATING_VERB_PREFIX + LIKE_ASPECT).actor(
                ActivityHelper.createUserActivityObject(username)).target(
                activityObject).object(String.valueOf(1)).publishedDate(
                publishedDate.toDate());
        if (contextObject != null) {
            activityBuilder.context(contextObject);
        }

        activityStreamService.addActivity(activityBuilder.build());
    }

    @Test
    public void shouldCountCorrectlyLikesOnMiniMessage() throws ClientException {
        DocumentModel context = session.getDocument(new PathRef(
                "/default-domain/workspaces/test"));
        DocumentModel lovelyDoc = createTestDocument("lovelyDoc",
                context.getPathAsString());
        assertNotNull(mms);

        Principal jeannot = createUser("jeannot");
        MiniMessage miniMessage = mms.addMiniMessage(session.getPrincipal(),
                "My first miniMessage", new Date(),
                createDocumentActivityObject(context));
        MiniMessage miniMessage1 = mms.addMiniMessage(jeannot,
                "My Second miniMessage", new Date(),
                createDocumentActivityObject(context));
        MiniMessage miniMessage2 = mms.addMiniMessage(session.getPrincipal(),
                "My third miniMessage", new Date(),
                createDocumentActivityObject(context));

        String miniMessageActivity = createActivityObject(miniMessage.getId());
        likeService.like(session.getPrincipal().getName(), miniMessageActivity);

        String miniMessage1Activity = createActivityObject(miniMessage1.getId());
        likeService.like(session.getPrincipal().getName(), miniMessage1Activity);
        likeService.like("Emir", miniMessage1Activity);

        assertEquals(1, likeService.getLikesCount(miniMessageActivity));

        // Query a first time with all principals
        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        parameters.put(ACTOR_PARAMETER, createUserActivityObject("Emir"));
        parameters.put(CONTEXT_PARAMETER, createDocumentActivityObject(context));
        parameters.put(QUERY_TYPE_PARAMETER, GET_MINI_MESSAGE_COUNT);

        ActivitiesList activitiesList = activityStreamService.query(
                LikesCountActivityStreamFilter.ID, parameters);
        assertEquals(2, activitiesList.size());
        assertEquals("2", activitiesList.get(0).getObject());
        assertEquals(miniMessage1Activity, activitiesList.get(0).getTarget());

        assertEquals("1", activitiesList.get(0).getContext());
        // Emir do not vote minimessage
        assertNotSame("1", activitiesList.get(1).getContext());
    }

    protected Principal createUser(String username) throws ClientException {
        DocumentModel user = userManager.getBareUserModel();
        user.setPropertyValue("user:username", username);
        try {
            userManager.createUser(user);
        } catch (UserAlreadyExistsException e) {
            // do nothing
        } finally {
            session.save();
        }
        return userManager.getPrincipal(username);
    }

    @Test
    public void shouldCorrectlyCountWithRatingCountFilter()
            throws ClientException {
        DocumentModel test = session.getDocument(new PathRef(
                "/default-domain/workspaces/test"));

        DocumentModel doc1 = createTestDocument("tmp",
                "/default-domain/workspaces/test");
        DocumentModel doc2 = createTestDocument("tmp1",
                "/default-domain/workspaces/test");

        likeService.like("ted", doc1);
        likeService.like("Robin", doc1);
        likeService.like("Lily", doc1);

        likeService.like("ted", doc2);
        likeService.like("Barney", doc2);
        likeService.like("Marshal", doc2);
        likeService.like("Ranjit", doc2);

        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        parameters.put(CONTEXT_PARAMETER,
                ActivityHelper.createDocumentActivityObject(test));
        parameters.put(ASPECT_PARAMETER, "like");
        parameters.put(OBJECT_PARAMETER, LIKE_RATING);
        parameters.put(ACTOR_PARAMETER, createUserActivityObject("Robin"));
        parameters.put(QUERY_TYPE_PARAMETER, GET_DOCUMENTS_COUNT);

        ActivitiesList activitiesList = activityStreamService.query(
                LikesCountActivityStreamFilter.ID, parameters);
        assertEquals(2, activitiesList.size());
        assertEquals("4", activitiesList.get(0).getObject());
        assertEquals("3", activitiesList.get(1).getObject());

        assertEquals("0", activitiesList.get(0).getContext());
        assertEquals("1", activitiesList.get(1).getContext());
    }

    protected void addDummyActivities(ActivitiesList activities, String docId,
            int nb) {
        for (int i = 0; i < nb; i++) {
            ActivityBuilder ab = new ActivityBuilder().target(
                    ActivityHelper.createDocumentActivityObject("default",
                            docId)).object("1");
            activities.add(ab.build());
        }
    }

    @Test
    @Ignore
    public void shouldEnsureOrderIsRight() throws ClientException {
        int expected = 75;
        int limit = 30;

        for (int i = 0; i < expected; i++) {
            createAndLikeDocs(i);
        }
        PathRef testRef = new PathRef("/default-domain/workspaces/test");
        assertEquals(expected, session.getChildren(testRef).size());

        ActivitiesList docs = likeService.getMostLikedActivities(session,
                limit, session.getDocument(testRef));
        assertEquals(limit, docs.size());

        for (int i = 0; i < limit; i++) {
            int id = expected - (i + 1);
            Activity activity = docs.get(i);
            IdRef docRef = new IdRef(
                    ActivityHelper.getDocumentId(activity.getTarget()));
            assertEquals("doc" + id, session.getDocument(docRef).getName());
            assertEquals(String.valueOf(id), activity.getObject());
        }
    }

    protected void createAndLikeDocs(int nb) throws ClientException {
        DocumentModel doc = createTestDocument("doc" + nb,
                "/default-domain/workspaces/test");
        for (int i = 0; i < nb; i++) {
            likeService.like("user" + i, doc);
        }
    }
}