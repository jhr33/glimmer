package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 举报记录表 Mapper
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /**
     * 获取被举报成立的目标ID列表
     * @param targetType 目标类型
     * @return 被举报成立的目标ID列表
     */
    @Select("SELECT DISTINCT target_id FROM report WHERE target_type = #{targetType} AND status = 'reviewed' AND result = 'approved'")
    List<Long> selectApprovedTargetIds(String targetType);

    /**
     * 查询举报分组统计（按目标资源分组）
     * @param status 状态筛选（可选）
     * @return 分组统计列表
     */
    @Select("SELECT target_type, target_id, target_user_id, " +
            "COUNT(DISTINCT reporter_id) as reporter_count, " +
            "MIN(created_at) as first_reported_at, " +
            "MAX(created_at) as last_reported_at, " +
            "CASE WHEN COUNT(CASE WHEN status = 'pending' THEN 1 END) > 0 THEN 'pending' ELSE 'reviewed' END as group_status, " +
            "MAX(result) as group_result " +
            "FROM report " +
            "WHERE #{status} IS NULL OR status = #{status} " +
            "GROUP BY target_type, target_id, target_user_id " +
            "ORDER BY last_reported_at DESC")
    List<ReportGroupSummary> selectReportGroupSummaries(@Param("status") String status);

    /**
     * 查询指定目标的所有举报记录
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 举报记录列表
     */
    @Select("SELECT * FROM report WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY created_at DESC")
    List<Report> selectByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    /**
     * 获取指定目标的所有举报人ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 举报人ID列表
     */
    @Select("SELECT DISTINCT reporter_id FROM report WHERE target_type = #{targetType} AND target_id = #{targetId}")
    List<Long> selectReporterIdsByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    /**
     * 查询举报分组统计（分页）
     * @param status 状态筛选（可选）
     * @param offset 偏移量
     * @param limit 每页大小
     * @return 分组统计列表
     */
    @Select("SELECT target_type, target_id, target_user_id, " +
            "COUNT(DISTINCT reporter_id) as reporter_count, " +
            "MIN(created_at) as first_reported_at, " +
            "MAX(created_at) as last_reported_at, " +
            "CASE WHEN COUNT(CASE WHEN status = 'pending' THEN 1 END) > 0 THEN 'pending' ELSE 'reviewed' END as group_status, " +
            "MAX(result) as group_result " +
            "FROM report " +
            "WHERE #{status} IS NULL OR status = #{status} " +
            "GROUP BY target_type, target_id, target_user_id " +
            "ORDER BY last_reported_at DESC " +
            "LIMIT #{offset}, #{limit}")
    List<ReportGroupSummary> selectReportGroupSummariesPage(@Param("status") String status, 
                                                            @Param("offset") int offset, 
                                                            @Param("limit") int limit);

    /**
     * 查询举报分组总数
     * @param status 状态筛选（可选）
     * @return 分组总数
     */
    @Select("SELECT COUNT(DISTINCT CONCAT(target_type, '-', target_id)) FROM report WHERE #{status} IS NULL OR status = #{status}")
    Long selectReportGroupCount(@Param("status") String status);

    /**
     * 举报分组统计内部类
     */
    class ReportGroupSummary {
        private String targetType;
        private Long targetId;
        private Long targetUserId;
        private Integer reporterCount;
        private LocalDateTime firstReportedAt;
        private LocalDateTime lastReportedAt;
        private String groupStatus;
        private String groupResult;

        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public Long getTargetId() { return targetId; }
        public void setTargetId(Long targetId) { this.targetId = targetId; }
        public Long getTargetUserId() { return targetUserId; }
        public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
        public Integer getReporterCount() { return reporterCount; }
        public void setReporterCount(Integer reporterCount) { this.reporterCount = reporterCount; }
        public LocalDateTime getFirstReportedAt() { return firstReportedAt; }
        public void setFirstReportedAt(LocalDateTime firstReportedAt) { this.firstReportedAt = firstReportedAt; }
        public LocalDateTime getLastReportedAt() { return lastReportedAt; }
        public void setLastReportedAt(LocalDateTime lastReportedAt) { this.lastReportedAt = lastReportedAt; }
        public String getGroupStatus() { return groupStatus; }
        public void setGroupStatus(String groupStatus) { this.groupStatus = groupStatus; }
        public String getGroupResult() { return groupResult; }
        public void setGroupResult(String groupResult) { this.groupResult = groupResult; }
    }
}
