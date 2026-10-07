package com.fc.v2.service.surg;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.service.ITMhSurgApplyService;
import com.fc.v2.service.impl.TMhSurgApplyServiceImpl;

/**
 * 术前三方核查的测试容器：H2(MySQL 模式) + MyBatis-Plus + 真实声明式事务，
 * 不启 shiro/web，跑的是真 SQL、真唯一键、真回滚。
 */
@Configuration
@EnableTransactionManagement
public class SurgTestConfig {

    private static final java.util.concurrent.atomic.AtomicInteger DB_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        // 每个测试上下文一个独立内存库，测试之间数据完全隔离
        ds.setUrl("jdbc:h2:mem:surg" + DB_SEQ.incrementAndGet()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000");
        ds.setUsername("sa");
        ds.setPassword("");
        try {
            ScriptUtils.executeSqlScript(ds.getConnection(),
                    new ClassPathResource("surg/surg-h2.sql"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ds;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        GlobalConfig gc = new GlobalConfig();
        // 测试环境不接 shiro，用空填充器替代线上取登录名的自动填充
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(IdType.ASSIGN_ID);
        gc.setDbConfig(dbConfig);
        gc.setMetaObjectHandler(new com.baomidou.mybatisplus.core.handlers.MetaObjectHandler() {
            @Override
            public void insertFill(org.apache.ibatis.reflection.MetaObject metaObject) {
            }

            @Override
            public void updateFill(org.apache.ibatis.reflection.MetaObject metaObject) {
            }
        });
        factory.setGlobalConfig(gc);
        return factory.getObject();
    }

    @Bean
    public static MapperScannerConfigurer mapperScanner() {
        MapperScannerConfigurer scanner = new MapperScannerConfigurer();
        scanner.setBasePackage("com.fc.v2.mapper.auto");
        scanner.setSqlSessionFactoryBeanName("sqlSessionFactory");
        return scanner;
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public ITMhSurgApplyService surgApplyService() {
        return new TMhSurgApplyServiceImpl();
    }
}
