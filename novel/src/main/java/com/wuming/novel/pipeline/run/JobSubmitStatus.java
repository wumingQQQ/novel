package com.wuming.novel.pipeline.run;

/**
 * 表示一次流程提交请求的结果，而不是任务最终执行结果。
 */
public enum JobSubmitStatus {
    STARTED("started"),
    ALREADY_RUNNING("running"),
    NOT_RESTARTABLE("not_restartable");

    private final String responseValue;

    JobSubmitStatus(String responseValue) {
        this.responseValue = responseValue;
    }

    public String responseValue() {
        return responseValue;
    }
}
