package demo.k8s.agent.toolstate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ToolArtifact  Repository
 *
 * 支持乐观并发控制的更新操作
 */
@Repository
public interface ToolArtifactRepository extends JpaRepository<ToolArtifact, String> {

    List<ToolArtifact> findBySessionIdOrderBySeq(String sessionId);

    List<ToolArtifact> findByAccountIdOrderByUpdatedAtDesc(String accountId);

    Optional<ToolArtifact> findByIdAndAccountId(String id, String accountId);

    /**
     * 乐观并发更新 header
     * @return 受影响的行数（0 表示版本冲突）
     */
    @Modifying
    @Query("UPDATE ToolArtifact a SET a.header = :header, a.headerVersion = a.headerVersion + 1, " +
           "a.updatedAt = FUNCTION('CURRENT_TIMESTAMP'), a.seq = :seq " +
           "WHERE a.id = :id AND a.accountId = :accountId AND a.headerVersion = :expectedVersion")
    int updateHeaderOptimistic(
        @Param("id") String id,
        @Param("accountId") String accountId,
        @Param("header") String header,
        @Param("expectedVersion") int expectedVersion,
        @Param("seq") long seq
    );

    /**
     * 乐观并发更新 body
     * @return 受影响的行数（0 表示版本冲突）
     */
    @Modifying
    @Query("UPDATE ToolArtifact a SET a.body = :body, a.bodyVersion = a.bodyVersion + 1, " +
           "a.updatedAt = FUNCTION('CURRENT_TIMESTAMP'), a.seq = :seq " +
           "WHERE a.id = :id AND a.accountId = :accountId AND a.bodyVersion = :expectedVersion")
    int updateBodyOptimistic(
        @Param("id") String id,
        @Param("accountId") String accountId,
        @Param("body") String body,
        @Param("expectedVersion") int expectedVersion,
        @Param("seq") long seq
    );
}
