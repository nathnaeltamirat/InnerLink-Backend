package com.innerlink.innerlink_backend.chat.service;

import java.util.Set;

import com.innerlink.innerlink_backend.chat.matching.*;
import com.innerlink.innerlink_backend.chat.verticle.ChatVerticle;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ChatSessionService {

    private final MoodMatchingService peerMood = new MoodMatchingService();
    private final GroupMatchingService group = new GroupMatchingService();
    private final VolunteerTalkingService volunteer = new VolunteerTalkingService();

    private final ChatVerticle chatVerticle;

    public ChatSessionService(ChatVerticle chatVerticle) {
        this.chatVerticle = chatVerticle;
    }

    public Future<JsonObject> startVolunteer(String userId, String volunteerId) {

        return volunteer.talkToVolunteer(volunteerId, userId)
                .map(session -> {
                    chatVerticle.registerConversation(
                            session.getString("conversationId"),
                            Set.of(userId, volunteerId));
                    return session;
                });
    }

    public Future<JsonObject> startMood(String userId, String mood) {

        return peerMood.findPeerMatch(userId, mood)
                .map(session -> {

                    chatVerticle.registerConversation(
                            session.getString("conversationId"),
                            Set.of(userId));

                    return session;
                });
    }

    public Future<JsonObject> startGroup(String userId, String mood) {

        return group.joinMoodGroup(userId, mood)
                .map(session -> {
                    chatVerticle.registerConversation(
                            session.getString("conversationId"),
                            Set.of(userId));
                    return session;
                });
    }
}