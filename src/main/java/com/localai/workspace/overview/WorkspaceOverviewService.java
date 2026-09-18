package com.localai.workspace.overview;

import com.localai.workspace.agent.DashboardGitService;
import com.localai.workspace.discovery.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class WorkspaceOverviewService {
    private final ProjectDiscoveryService discovery;
    private final DashboardGitService git;
    private final JdbcTemplate jdbc;
    public WorkspaceOverviewService(ProjectDiscoveryService discovery, DashboardGitService git, JdbcTemplate jdbc) {
        this.discovery=discovery; this.git=git; this.jdbc=jdbc;
    }
    public record ProjectSummary(DetectedProject project, DashboardGitService.Snapshot git,
            Long indexedDocumentCount, Long indexedChunkCount, String automationStatus,
            Long errorHistoryCount, Long notificationCount, List<String> warnings) {}
    public record Summary(List<ProjectSummary> projects, Instant collectedAt, long durationMillis, String remoteBasis) {}
    public Summary get() {
        long start=System.nanoTime();
        var projects=discovery.discoverProjects();
        List<String> warnings=new ArrayList<>();
        var indexes=load("SELECT project_id, COUNT(DISTINCT source_path) documents, COUNT(*) chunks FROM document_chunk_embedding GROUP BY project_id","Index",warnings);
        var errors=load("SELECT project_id, COUNT(*) total FROM error_history GROUP BY project_id","Error history",warnings);
        var notifications=load("SELECT project_id, COUNT(*) total FROM notification_candidate GROUP BY project_id","Notifications",warnings);
        var configs=load("SELECT project_id, enabled FROM project_automation_config","Automation",warnings);
        List<ProjectSummary> result=new ArrayList<>();
        for(var project:projects) {
            var id=project.projectId();
            List<String> projectWarnings=new ArrayList<>(warnings);
            DashboardGitService.Snapshot snapshot;
            try { snapshot=git.read(project); }
            catch(RuntimeException ex) { snapshot=null;projectWarnings.add("Git metadata unavailable"); }
            String automation=configs==null?"UNKNOWN":Boolean.TRUE.equals(configs.getOrDefault(id,Map.of()).get("enabled"))?"ENABLED":"DISABLED";
            result.add(new ProjectSummary(project,snapshot,count(indexes,id,"documents"),count(indexes,id,"chunks"),
                    automation,count(errors,id,"total"),count(notifications,id,"total"),List.copyOf(projectWarnings)));
        }
        return new Summary(List.copyOf(result),Instant.now(),(System.nanoTime()-start)/1_000_000,
                "Configured upstream, locally cached refs; no automatic fetch");
    }
    private Map<String,Map<String,Object>> load(String sql,String label,List<String> warnings) {
        try {
            Map<String,Map<String,Object>> result=new HashMap<>();
            for(var row:jdbc.queryForList(sql)) result.put((String)row.get("project_id"),row);
            return result;
        } catch(RuntimeException ex) { warnings.add(label+" unavailable");return null; }
    }
    private Long count(Map<String,Map<String,Object>> rows,String id,String key) {
        if(rows==null)return null;
        Object value=rows.getOrDefault(id,Map.of()).get(key);
        return value instanceof Number n?n.longValue():0L;
    }
}
