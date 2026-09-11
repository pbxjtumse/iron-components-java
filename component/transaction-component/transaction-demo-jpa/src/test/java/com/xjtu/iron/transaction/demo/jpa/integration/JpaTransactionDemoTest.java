package com.xjtu.iron.transaction.demo.jpa.integration;

import com.xjtu.iron.transaction.demo.jpa.JpaTransactionDemoApplication;
import com.xjtu.iron.transaction.demo.jpa.repository.DemoRecordRepository;
import com.xjtu.iron.transaction.demo.jpa.service.JpaTransactionDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JpaTransactionDemoApplication.class)
@ActiveProfiles("test")
class JpaTransactionDemoTest {

    @Autowired
    private JpaTransactionDemoService service;

    @Autowired
    private DemoRecordRepository repository;

    @BeforeEach
    void cleanTable() {
        // 每个场景前清空数据，保证结果只反映当前事务行为。
        repository.deleteAll();
    }

    @Test
    void successfulCallbackShouldCommit() {
        // 正常保存并提交一条实体。
        service.commit(1L);

        // 事务完成后实体应能重新查询到。
        assertEquals(1, repository.count());
        assertTrue(repository.findById(1L).isPresent());
    }

    @Test
    void businessExceptionShouldRollback() {
        // save 后抛业务异常，事务应整体回滚。
        assertThrows(IllegalStateException.class, () -> service.rollback(2L));
        assertEquals(0, repository.count());
    }

    @Test
    void requiresNewShouldCommitInnerIndependently() {
        // outer 失败，inner REQUIRES_NEW 仍应独立提交。
        assertThrows(IllegalStateException.class, service::outerRollbackInnerRequiresNew);

        // 最终只留下 inner 事务写入的 2002。
        assertEquals(1, repository.count());
        assertTrue(repository.findById(2002L).isPresent());
    }
}
