package com.wuming.novel.infrastructure.mapper;

import com.wuming.novel.domain.entity.Scene;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RecallMapper {
    List<Scene> recallScenesByLayerAndPool(@Param("novelId") Long novelId,
                                           @Param("jobId") Long jobId,
                                           @Param("poolType") String poolType,
                                           @Param("threshold") double threshold,
                                           @Param("startSeq") int startSeq,
                                           @Param("endSeq") int endSeq,
                                           @Param("limit") int limit);

    List<Scene> recallTopScenesByPool(@Param("novelId") Long novelId,
                                      @Param("jobId") Long jobId,
                                      @Param("poolType") String poolType,
                                      @Param("threshold") double threshold,
                                      @Param("limit") int limit);
}
