package com.wuming.common.messaging;

public final class MqDestinations {

    public static final String NOVEL_EVENTS_TOPIC = "novel";

    public static final String JOB_ENDED_TAG = "job_ended";

    public static final String JOB_ENDED = destination(NOVEL_EVENTS_TOPIC, JOB_ENDED_TAG);

    private MqDestinations() {
    }

    public static String destination(String topic, String tag) {
        return topic + ":" + tag;
    }
}
