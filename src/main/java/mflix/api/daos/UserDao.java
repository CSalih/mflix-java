package mflix.api.daos;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Session;
import mflix.api.models.User;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.text.MessageFormat;
import java.util.Map;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class UserDao extends AbstractMFlixDao {

    private final MongoCollection<User> usersCollection;
    private final MongoCollection<Session> sessionsCollection;

    private final Logger log;

    @Autowired
    public UserDao(
            MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        CodecRegistry pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        log = LoggerFactory.getLogger(this.getClass());
        usersCollection = db.getCollection("users", User.class)
                .withCodecRegistry(pojoCodecRegistry);
        sessionsCollection = db.getCollection("sessions", Session.class)
                .withCodecRegistry(pojoCodecRegistry);
    }

    /**
     * Inserts the `user` object in the `users` collection.
     *
     * @param user - User object to be added
     * @return True if successful, throw IncorrectDaoOperation otherwise
     */
    public boolean addUser(User user) {
        try {
            usersCollection
                    .withWriteConcern(WriteConcern.MAJORITY)
                    .insertOne(user);

            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new IncorrectDaoOperation("User exists already");
            }
            throw new IncorrectDaoOperation("Adding a user failed! Message: " + e.getMessage());
        }
    }

    /**
     * Creates session using userId and jwt token.
     *
     * @param userId - user string identifier
     * @param jwt    - jwt string token
     * @return true if successful
     */
    public boolean createUserSession(String userId, String jwt) {
        try {
            // workaround to avoid duplicated key error
            sessionsCollection.deleteOne(Filters.eq("user_id", userId));
            sessionsCollection.insertOne(new Session(userId, jwt));
            return true;
        } catch (MongoWriteException e) {
            throw new IncorrectDaoOperation("Creating a user session failed! Message: " + e.getMessage());
        }
    }

    /**
     * Returns the User object matching the an email string value.
     *
     * @param email - email string to be matched.
     * @return User object or null.
     */
    public User getUser(String email) {
        Bson query = Filters.eq("email", email);

        return usersCollection
                .find(query)
                .first();
    }

    /**
     * Given the userId, returns a Session object.
     *
     * @param userId - user string identifier.
     * @return Session object or null.
     */
    public Session getUserSession(String userId) {
        return sessionsCollection
                .find(Filters.eq("user_id", userId))
                .first();
    }

    public boolean deleteUserSessions(String userId) {
        return sessionsCollection
                .deleteOne(Filters.eq("user_id", userId))
                .wasAcknowledged();
    }

    /**
     * Removes the user document that match the provided email.
     *
     * @param email - of the user to be deleted.
     * @return true if user successfully removed
     */
    public boolean deleteUser(String email) {
        try {
            // remove user sessions
            DeleteResult userResult = usersCollection.deleteOne(Filters.eq("email", email));
            DeleteResult sessionResult = sessionsCollection.deleteOne(Filters.eq("user_id", email));

            return userResult.wasAcknowledged() && sessionResult.wasAcknowledged();
        } catch (MongoWriteException e) {
            log.error("Deleting a user failed! Message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates the preferences of an user identified by `email` parameter.
     *
     * @param email           - user to be updated email
     * @param userPreferences - set of preferences that should be stored and replace the existing
     *                        ones. Cannot be set to null value
     * @return User object that just been updated.
     */
    public boolean updateUserPreferences(String email, Map<String, ?> userPreferences) {
        if (null == userPreferences) {
            throw new IncorrectDaoOperation(
                    "userPreferences must not be null! Given userPreferences=null"
            );
        }

        User updatedUser = usersCollection.findOneAndUpdate(
                Filters.eq("email", email),
                Updates.set("preferences", userPreferences)
        );

        return updatedUser != null;
    }
}
