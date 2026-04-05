package demo.k8s.agent.skills;

import demo.k8s.agent.skills.SkillService.SkillManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能 REST API 控制器
 */
@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = "*")
public class SkillController {

    private static final Logger log = LoggerFactory.getLogger(SkillController.class);

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 获取所有技能
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSkills() {
        List<SkillManifest> skills = skillService.getAllSkills();

        Map<String, Object> response = new HashMap<>();
        response.put("skills", skills.stream().map(s -> {
            Map<String, Object> skillMap = new HashMap<>();
            skillMap.put("name", s.getName());
            skillMap.put("description", s.getDescription());
            skillMap.put("directory", s.getDirectory());
            if (s.getMetadata() != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("emoji", s.getMetadata().emoji);
                metadata.put("homepage", s.getMetadata().homepage);
                metadata.put("always", s.getMetadata().always);
                skillMap.put("metadata", metadata);
            }
            return skillMap;
        }).toList());
        response.put("count", skills.size());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取技能详情
     */
    @GetMapping("/{skillName}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable String skillName) {
        SkillManifest skill = skillService.getSkill(skillName);

        if (skill == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("name", skill.getName());
        response.put("description", skill.getDescription());
        response.put("directory", skill.getDirectory());
        response.put("metadata", skill.getMetadata());

        return ResponseEntity.ok(response);
    }

    /**
     * 搜索技能
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchSkills(@RequestParam(required = false) String q) {
        List<SkillManifest> skills = skillService.searchSkills(q);

        Map<String, Object> response = new HashMap<>();
        response.put("skills", skills.stream().map(s -> {
            Map<String, Object> skillMap = new HashMap<>();
            skillMap.put("name", s.getName());
            skillMap.put("description", s.getDescription());
            skillMap.put("directory", s.getDirectory());
            return skillMap;
        }).toList());
        response.put("count", skills.size());
        response.put("query", q);

        return ResponseEntity.ok(response);
    }

    /**
     * 安装技能
     */
    @PostMapping("/{skillId}/install")
    public ResponseEntity<Map<String, Object>> installSkill(
            @PathVariable String skillId,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String workdir) {

        boolean success = skillService.installSkill(skillId, version, workdir);

        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("success", true);
            response.put("message", "Skill installed: " + skillId);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", "Failed to install skill: " + skillId);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 卸载技能
     */
    @PostMapping("/{skillId}/uninstall")
    public ResponseEntity<Map<String, Object>> uninstallSkill(@PathVariable String skillId) {
        boolean success = skillService.uninstallSkill(skillId);

        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("success", true);
            response.put("message", "Skill uninstalled: " + skillId);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", "Skill not found: " + skillId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 重新加载技能
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadSkills() {
        skillService.loadAllSkills();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", skillService.getAllSkills().size());
        response.put("message", "Skills reloaded");

        return ResponseEntity.ok(response);
    }
}
