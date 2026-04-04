package demo.k8s.agent.tools.local.memory;

import demo.k8s.agent.memory.search.MemorySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 记忆工具初始化器 - 在应用启动时设置 MemorySearchTool 的服务引用
 */
@Component
public class MemoryToolInitializer {

    private static final Logger log = LoggerFactory.getLogger(MemoryToolInitializer.class);

    private final MemorySearchService searchService;

    public MemoryToolInitializer(MemorySearchService searchService) {
        this.searchService = searchService;
        log.info("MemoryToolInitializer initialized");
    }

    /**
     * 应用启动时设置搜索服务到 MemorySearchTool
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        MemorySearchTool.setSearchService(searchService);
        log.info("MemorySearchTool registered with MemorySearchService");
    }
}
