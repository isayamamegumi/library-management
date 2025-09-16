package com.library.management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchParameterService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static class JobParameter {
        private Long id;
        private String jobName;
        private String parameterName;
        private String parameterValue;
        private String parameterType;
        private String description;
        private Boolean isActive;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getJobName() { return jobName; }
        public void setJobName(String jobName) { this.jobName = jobName; }

        public String getParameterName() { return parameterName; }
        public void setParameterName(String parameterName) { this.parameterName = parameterName; }

        public String getParameterValue() { return parameterValue; }
        public void setParameterValue(String parameterValue) { this.parameterValue = parameterValue; }

        public String getParameterType() { return parameterType; }
        public void setParameterType(String parameterType) { this.parameterType = parameterType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    private static class JobParameterRowMapper implements RowMapper<JobParameter> {
        @Override
        public JobParameter mapRow(ResultSet rs, int rowNum) throws SQLException {
            JobParameter param = new JobParameter();
            param.setId(rs.getLong("id"));
            param.setJobName(rs.getString("job_name"));
            param.setParameterName(rs.getString("parameter_name"));
            param.setParameterValue(rs.getString("parameter_value"));
            param.setParameterType(rs.getString("parameter_type"));
            param.setDescription(rs.getString("description"));
            param.setIsActive(rs.getBoolean("is_active"));
            param.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            param.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return param;
        }
    }

    public List<JobParameter> getJobParameters(String jobName) {
        String sql = "SELECT * FROM batch_job_parameters WHERE job_name = ? ORDER BY parameter_name";
        return jdbcTemplate.query(sql, new JobParameterRowMapper(), jobName);
    }

    public List<JobParameter> getAllJobParameters() {
        String sql = "SELECT * FROM batch_job_parameters ORDER BY job_name, parameter_name";
        return jdbcTemplate.query(sql, new JobParameterRowMapper());
    }

    public JobParameter getJobParameter(String jobName, String parameterName) {
        String sql = "SELECT * FROM batch_job_parameters WHERE job_name = ? AND parameter_name = ?";
        List<JobParameter> results = jdbcTemplate.query(sql, new JobParameterRowMapper(), jobName, parameterName);
        return results.isEmpty() ? null : results.get(0);
    }

    public JobParameter saveJobParameter(JobParameter parameter) {
        if (parameter.getId() == null) {
            // 新規作成
            String sql = """
                INSERT INTO batch_job_parameters
                (job_name, parameter_name, parameter_value, parameter_type, description, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;

            jdbcTemplate.update(sql,
                parameter.getJobName(),
                parameter.getParameterName(),
                parameter.getParameterValue(),
                parameter.getParameterType() != null ? parameter.getParameterType() : "STRING",
                parameter.getDescription(),
                parameter.getIsActive() != null ? parameter.getIsActive() : true
            );

            // 作成されたパラメータを取得
            return getJobParameter(parameter.getJobName(), parameter.getParameterName());
        } else {
            // 更新
            String sql = """
                UPDATE batch_job_parameters SET
                parameter_value = ?, parameter_type = ?, description = ?, is_active = ?, updated_at = NOW()
                WHERE id = ?
                """;

            jdbcTemplate.update(sql,
                parameter.getParameterValue(),
                parameter.getParameterType(),
                parameter.getDescription(),
                parameter.getIsActive(),
                parameter.getId()
            );

            return getJobParameterById(parameter.getId());
        }
    }

    public JobParameter getJobParameterById(Long id) {
        String sql = "SELECT * FROM batch_job_parameters WHERE id = ?";
        List<JobParameter> results = jdbcTemplate.query(sql, new JobParameterRowMapper(), id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void deleteJobParameter(Long id) {
        String sql = "DELETE FROM batch_job_parameters WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void deleteJobParameters(String jobName) {
        String sql = "DELETE FROM batch_job_parameters WHERE job_name = ?";
        jdbcTemplate.update(sql, jobName);
    }

    public Map<String, String> getActiveParametersAsMap(String jobName) {
        String sql = "SELECT parameter_name, parameter_value FROM batch_job_parameters WHERE job_name = ? AND is_active = true";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, jobName);
        Map<String, String> parameters = new HashMap<>();

        for (Map<String, Object> row : results) {
            parameters.put((String) row.get("parameter_name"), (String) row.get("parameter_value"));
        }

        return parameters;
    }

    public void toggleParameterStatus(Long id) {
        String sql = "UPDATE batch_job_parameters SET is_active = NOT is_active, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<String> getDistinctJobNames() {
        String sql = "SELECT DISTINCT job_name FROM batch_job_parameters ORDER BY job_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public int countParametersByJob(String jobName) {
        String sql = "SELECT COUNT(*) FROM batch_job_parameters WHERE job_name = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, jobName);
    }

    public int countActiveParametersByJob(String jobName) {
        String sql = "SELECT COUNT(*) FROM batch_job_parameters WHERE job_name = ? AND is_active = true";
        return jdbcTemplate.queryForObject(sql, Integer.class, jobName);
    }
}