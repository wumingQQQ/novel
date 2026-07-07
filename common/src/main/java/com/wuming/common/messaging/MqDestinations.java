package com.wuming.common.messaging;

public final class MqDestinations {

    public static final String NOVEL_EVENTS_TOPIC = "novel";

    public static final String JOB_ENDED_TAG = "job_ended";
    public static final String CHAPTER_ANALYSIS_COMPLETED_TAG = "chapter_analysis_completed";
    public static final String NOVEL_PASSAGE_INDEX_TAG = "novel_passage_index";
    public static final String ROLE_EXAMPLE_INDEX_TAG = "role_example_index";
    public static final String ROLE_REACTION_RULE_INDEX_TAG = "role_reaction_rule_index";

    public static final String JOB_ENDED = destination(NOVEL_EVENTS_TOPIC, JOB_ENDED_TAG);
    public static final String CHAPTER_ANALYSIS_COMPLETED = destination(NOVEL_EVENTS_TOPIC, CHAPTER_ANALYSIS_COMPLETED_TAG);
    public static final String NOVEL_PASSAGE_INDEX = destination(NOVEL_EVENTS_TOPIC, NOVEL_PASSAGE_INDEX_TAG);
    public static final String ROLE_EXAMPLE_INDEX = destination(NOVEL_EVENTS_TOPIC, ROLE_EXAMPLE_INDEX_TAG);
    public static final String ROLE_REACTION_RULE_INDEX = destination(NOVEL_EVENTS_TOPIC, ROLE_REACTION_RULE_INDEX_TAG);

    private MqDestinations() {
    }

    public static String destination(String topic, String tag) {
        return topic + ":" + tag;
    }
}
