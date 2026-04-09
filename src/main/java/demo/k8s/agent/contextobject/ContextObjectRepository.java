package demo.k8s.agent.contextobject;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContextObjectRepository extends JpaRepository<ContextObject, String> {

    /**
     * 隐式鉴权：必须同时匹配 id、会话与租户，避免跨会话读。
     */
    Optional<ContextObject> findByIdAndConversationIdAndTenantId(String id, String conversationId, String tenantId);
}
