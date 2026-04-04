package demo.k8s.agent.export;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 导出 API 控制器
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * 创建导出任务
     * POST /api/export
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createExport(@RequestBody ExportRequest request) {
        ExportJob job = exportService.createExportJob(request);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getId());
        response.put("status", job.getStatus().name());
        response.put("createdAt", job.getCreatedAt());

        return ResponseEntity.created(URI.create("/api/export/" + job.getId()))
                .body(response);
    }

    /**
     * 获取导出任务状态
     * GET /api/export/{jobId}
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<ExportJob> getExportJob(@PathVariable String jobId) {
        Optional<ExportJob> job = exportService.getExportJob(jobId);
        return job.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 列出所有导出任务
     * GET /api/export/jobs
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<ExportJob>> listExportJobs() {
        return ResponseEntity.ok(exportService.listExportJobs());
    }

    /**
     * 取消导出任务
     * POST /api/export/{jobId}/cancel
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, String>> cancelExportJob(@PathVariable String jobId) {
        boolean cancelled = exportService.cancelExportJob(jobId);

        Map<String, String> response = new HashMap<>();
        if (cancelled) {
            response.put("message", "导出任务已取消");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "无法取消任务，任务可能已完成或不存在");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除导出任务
     * DELETE /api/export/{jobId}
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, String>> deleteExportJob(@PathVariable String jobId) {
        boolean deleted = exportService.deleteExportJob(jobId);

        Map<String, String> response = new HashMap<>();
        if (deleted) {
            response.put("message", "导出任务已删除");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "导出任务不存在");
            return ResponseEntity.notFound().build();
        }
    }
}
