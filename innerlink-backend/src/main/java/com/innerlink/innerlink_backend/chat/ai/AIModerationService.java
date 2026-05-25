package com.innerlink.innerlink_backend.chat.ai;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class AIModerationService {
    public Future<JsonObject> analyzeMessage(JsonObject msg, Vertx vertx){

        return vertx.executeBlocking(()->{
            String content = msg.getString("content","").toLowerCase();
            int score = calculateHeavinessScore(content);
            String label = classify(score);
            boolean danger = score >= 80;
            return new JsonObject()
            .put("heaviness_score",score)
            .put("condition_label",label)
            .put("is_danger",danger);
          
        });
     
    }
    private int calculateHeavinessScore(String text){
        int score = 0;
    if (text.contains("suicide")) score += 90;
    if (text.contains("kill myself")) score += 95;
    if (text.contains("i want to die")) score += 95;
    if (text.contains("depressed")) score += 40;
    if (text.contains("worthless")) score += 50;
    if (text.contains("hopeless")) score += 45;

    if (text.contains("sad")) score += 20;
    if (text.contains("tired")) score += 10;
    if (text.contains("anxious")) score += 35;

    return Math.min(score, 100);
    }

    private String classify(int score){
        if(score >= 80) return "crisis";
        if(score >= 50) return "high stress";
        if(score >= 20 ) return "stressed";
        return "normal";
    }
}
