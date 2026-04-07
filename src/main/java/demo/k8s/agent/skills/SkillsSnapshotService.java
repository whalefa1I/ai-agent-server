package demo.k8s.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 技能快照版本管理
 *
 * 当技能文件发生变化时，版本号递增，触发重新加载
 */
@Service
public class SkillsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SkillsSnapshotService.class);

    /**
     * 快照版本号 - 每次技能变化时递增
     */
    private final AtomicLong snapshotVersion = new AtomicLong(0);

    /**
     * 最后变更时间
     */
    private volatile long lastChangeTime = System.currentTimeMillis();

    /**
     * 最后变更路径（用于调试）
     */
    private volatile String lastChangePath;

    /**
     * 获取当前快照版本
     */
    public long getSnapshotVersion() {
        return snapshotVersion.get();
    }

    /**
     *  bumped 快照版本（当技能文件变化时调用）
     *
     * @param changedPath 变化的文件路径
     * @return 新的版本号
     */
    public long bumpVersion(String changedPath) {
        long newVersion = snapshotVersion.incrementAndGet();
        lastChangeTime = System.currentTimeMillis();
        lastChangePath = changedPath;
        log.info("技能快照版本已更新：version={}, changedPath={}", newVersion, changedPath);
        return newVersion;
    }

    /**
     * 获取最后变更时间
     */
    public long getLastChangeTime() {
        return lastChangeTime;
    }

    /**
     * 获取最后变更路径
     */
    public String getLastChangePath() {
        return lastChangePath;
    }

    /**
     * 重置快照版本（用于测试）
     */
    public void resetForTest() {
        snapshotVersion.set(0);
        lastChangeTime = System.currentTimeMillis();
        lastChangePath = null;
    }
}
