package com.xjtu.iron.reliable.task.provider.mybatis.repository;

import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.provider.mybatis.mapper.ReliableTaskMapper;
import com.xjtu.iron.reliable.task.provider.testkit.ReliableTaskRepositoryContract;
import com.xjtu.iron.reliable.task.provider.testkit.ReliableTaskTestSchema;
import com.xjtu.iron.relational.mybatis.MyBatisAccessListener;
import com.xjtu.iron.relational.mybatis.SpringMyBatisAccess;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;

/** 验证 MyBatis Provider 遵守统一的 ReliableTaskRepository 契约。 */
class MyBatisReliableTaskRepositoryTest extends ReliableTaskRepositoryContract {

    /** 每个测试用例使用的 MyBatis 可靠任务仓储。 */
    private MyBatisReliableTaskRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reliable_task_mybatis_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ReliableTaskTestSchema.create(dataSource);

        Configuration configuration = new Configuration();
        configuration.addMapper(ReliableTaskMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory);
        repository = new MyBatisReliableTaskRepository(new SpringMyBatisAccess(
                dataSource, template, null, MyBatisAccessListener.noop()));
    }

    @Override
    protected ReliableTaskRepository repository() {
        return repository;
    }
}
