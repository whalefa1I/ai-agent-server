package demo.k8s.agent.subagent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 子 Agent 挂起审批仓库。
 */
@Repository
public interface SubagentSuspendRepository extends JpaRepository<SubagentSuspend, Long> {

    Optional<SubagentSuspend> findByRunId(String runId);

    List<SubagentSuspend> findBySessionIdAndStatus(String sessionId, SubagentSuspend.SuspendStatus status);

    List<SubagentSuspend> findByTenantIdAndStatus(String tenantId, SubagentSuspend.SuspendStatus status);

    List<SubagentSuspend> findByStatus(SubagentSuspend.SuspendStatus status);
}
