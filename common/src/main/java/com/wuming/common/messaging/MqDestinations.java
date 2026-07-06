package com.wuming.common.messaging;

public final class MqDestinations {

    public static final String NOVEL_EVENTS_TOPIC = "novel";

    public static final String JOB_ENDED_TAG = "job_ended";
    public static final String CHAPTER_ANALYSIS_COMPLETED_TAG = "chapter_analysis_completed";

    public static final String JOB_ENDED = destination(NOVEL_EVENTS_TOPIC, JOB_ENDED_TAG);
    public static final String CHAPTER_ANALYSIS_COMPLETED = destination(NOVEL_EVENTS_TOPIC, CHAPTER_ANALYSIS_COMPLETED_TAG);

    private MqDestinations() {
    }

    public static String destination(String topic, String tag) {
        return topic + ":" + tag;
    }
}
