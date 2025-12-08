package com.bootsandcats.profileui.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.bootsandcats.profileui.model.UserProfile;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.DeleteResult;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

/** MongoDB implementation of the ProfileRepository. */
@Singleton
public class MongoProfileRepository implements ProfileRepository {

    private final MongoClient mongoClient;
    private final String databaseName;
    private final String collectionName;

    public MongoProfileRepository(
            MongoClient mongoClient,
            @Value("${mongodb.database:profile-db}") String databaseName,
            @Value("${mongodb.collection.profiles:profiles}") String collectionName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    @PostConstruct
    void createIndexes() {
        MongoCollection<UserProfile> collection = getCollection();

        // Create unique index on oauth_subject
        collection.createIndex(
                Indexes.ascending("oauth_subject"), new IndexOptions().unique(true).sparse(true));

        // Create index on oauth_user_id
        collection.createIndex(Indexes.ascending("oauth_user_id"));

        // Create text index for search
        collection.createIndex(
                Indexes.compoundIndex(
                        Indexes.text("first_name"),
                        Indexes.text("last_name"),
                        Indexes.text("preferred_name"),
                        Indexes.text("email")));
    }

    private MongoCollection<UserProfile> getCollection() {
        return mongoClient
                .getDatabase(databaseName)
                .getCollection(collectionName, UserProfile.class);
    }

    @Override
    public Optional<UserProfile> findByOauthSubject(String oauthSubject) {
        return Optional.ofNullable(
                getCollection().find(Filters.eq("oauth_subject", oauthSubject)).first());
    }

    @Override
    public Optional<UserProfile> findByOauthUserId(Long oauthUserId) {
        return Optional.ofNullable(
                getCollection().find(Filters.eq("oauth_user_id", oauthUserId)).first());
    }

    @Override
    public Optional<UserProfile> findById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            return Optional.ofNullable(getCollection().find(Filters.eq("_id", objectId)).first());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<UserProfile> findAll(int page, int pageSize) {
        List<UserProfile> results = new ArrayList<>();
        getCollection().find().skip(page * pageSize).limit(pageSize).into(results);
        return results;
    }

    @Override
    public long count() {
        return getCollection().countDocuments();
    }

    @Override
    public List<UserProfile> search(String query, int page, int pageSize) {
        List<UserProfile> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return findAll(page, pageSize);
        }

        // Use regex for case-insensitive partial matching
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        Document regexFilter =
                new Document(
                        "$or",
                        List.of(
                                new Document("first_name", new Document("$regex", pattern)),
                                new Document("last_name", new Document("$regex", pattern)),
                                new Document("preferred_name", new Document("$regex", pattern)),
                                new Document("email", new Document("$regex", pattern))));

        getCollection().find(regexFilter).skip(page * pageSize).limit(pageSize).into(results);
        return results;
    }

    @Override
    public long countSearch(String query) {
        if (query == null || query.isBlank()) {
            return count();
        }

        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        Document regexFilter =
                new Document(
                        "$or",
                        List.of(
                                new Document("first_name", new Document("$regex", pattern)),
                                new Document("last_name", new Document("$regex", pattern)),
                                new Document("preferred_name", new Document("$regex", pattern)),
                                new Document("email", new Document("$regex", pattern))));

        return getCollection().countDocuments(regexFilter);
    }

    @Override
    public UserProfile save(UserProfile profile) {
        if (profile.getId() == null) {
            // Insert new profile
            getCollection().insertOne(profile);
        } else {
            // Update existing profile
            getCollection().replaceOne(Filters.eq("_id", profile.getId()), profile);
        }
        return profile;
    }

    @Override
    public boolean deleteById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            DeleteResult result = getCollection().deleteOne(Filters.eq("_id", objectId));
            return result.getDeletedCount() > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean deleteByOauthSubject(String oauthSubject) {
        DeleteResult result = getCollection().deleteOne(Filters.eq("oauth_subject", oauthSubject));
        return result.getDeletedCount() > 0;
    }

    @Override
    public boolean existsByOauthSubject(String oauthSubject) {
        return getCollection().countDocuments(Filters.eq("oauth_subject", oauthSubject)) > 0;
    }
}
