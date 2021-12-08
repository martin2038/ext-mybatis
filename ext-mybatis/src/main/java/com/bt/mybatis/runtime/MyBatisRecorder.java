package com.bt.mybatis.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Supplier;

import com.bt.mybatis.runtime.bridge.ConfigurationFactory;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.jboss.logging.Logger;

@Recorder
public class MyBatisRecorder {
    private static final Logger LOG = Logger.getLogger(MyBatisRecorder.class);

    public RuntimeValue<SqlSessionFactory> createSqlSessionFactory(ConfigurationFactory factory, List<String> mapperXml) throws IOException, URISyntaxException {
        Configuration cfg  = factory.createConfiguration();
        LOG.info("Setup :: " + factory);
        for(var sqlMap : mapperXml) {

            try (InputStream inputStream = Resources.getResourceAsStream(sqlMap)) {
                XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, cfg, sqlMap, cfg.getSqlFragments());

                LOG.debug("Add SQL XML :: "+sqlMap);
                mapperParser.parse();

            } catch (IOException e) {
                LOG.error("Error add SQLMAP :: "+sqlMap,e);
                e.printStackTrace();
            }
        }

        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(cfg);
        return new RuntimeValue<>(sqlSessionFactory);
    }

    public RuntimeValue<SqlSessionManager> createSqlSessionManager(RuntimeValue<SqlSessionFactory> sqlSessionFactory) {
        SqlSessionManager sqlSessionManager = SqlSessionManager.newInstance(sqlSessionFactory.getValue());
        return new RuntimeValue<>(sqlSessionManager);
    }

    public Supplier<Object> MyBatisMapperSupplier(String name, RuntimeValue<SqlSessionManager> sqlSessionManager) {
        return () -> {
            try {
                var mapper =  sqlSessionManager.getValue().getMapper(Resources.classForName(name));
                LOG.info("Create MyBatisMapper :: " + name  +" -> "+ mapper);
                return  mapper;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                LOG.info("Create MapperError :: " + name ,e);
                return null;
            }
        };
    }

    public Supplier<Object> MyBatisSqlSessionFactorySupplier(RuntimeValue<SqlSessionFactory> sqlSessionFactory) {
        return sqlSessionFactory::getValue;
    }
}
    //
    //public Supplier<Object> MyBatisMappedTypeSupplier(String name, RuntimeValue<SqlSessionManager> sqlSessionManager) {
    //    return () -> {
    //        try {
    //            return sqlSessionManager.getValue().getConfiguration().getTypeHandlerRegistry()
    //                    .getTypeHandler(Resources.classForName(name));
    //        } catch (ClassNotFoundException e) {
    //            return null;
    //        }
    //    };
    //}
    //
    //public Supplier<Object> MyBatisMappedJdbcTypeSupplier(String name, RuntimeValue<SqlSessionManager> sqlSessionManager) {
    //    return () -> {
    //        try {
    //            return sqlSessionManager.getValue().getConfiguration().getTypeHandlerRegistry()
    //                    .getTypeHandler(Resources.classForName(name));
    //        } catch (ClassNotFoundException e) {
    //            return null;
    //        }
    //    };
    //}

    //public Supplier<Object> MyBatisSqlSessionFactorySupplier(RuntimeValue<SqlSessionFactory> sqlSessionFactory) {
    //    return sqlSessionFactory::getValue;
    //}

    //public RuntimeValue<Configuration> createConfiguration() {
    //    return new RuntimeValue<>(new Configuration());
    //}
    //
    //public RuntimeValue<SqlSessionFactory> createSqlSessionFactory(
    //        ConfigurationFactory configurationFactory,
    //        SqlSessionFactoryBuilder builder,
    //        MyBatisRuntimeConfig myBatisRuntimeConfig,
    //        MyBatisDataSourceRuntimeConfig myBatisDataSourceRuntimeConfig,
    //        String dataSourceName,
    //        List<String> mappers,
    //        List<String> mappedTypes,
    //        List<String> mappedJdbcTypes) {
    //    Configuration configuration = configurationFactory.createConfiguration();
    //    setupConfiguration(configuration, myBatisRuntimeConfig, myBatisDataSourceRuntimeConfig, dataSourceName);
    //    addMappers(configuration, mappedTypes, mappedJdbcTypes, mappers);
    //
    //    SqlSessionFactory sqlSessionFactory = builder.build(configuration);
    //    return new RuntimeValue<>(sqlSessionFactory);
    //}
/*
    private void addMappers(Configuration configuration,
            List<String> mappedTypes, List<String> mappedJdbcTypes, List<String> mappers) {
        for (String mappedType : mappedTypes) {
            try {
                configuration.getTypeHandlerRegistry().register(Resources.classForName(mappedType));
            } catch (ClassNotFoundException e) {
                LOG.debug("Can not find the mapped type class " + mappedType);
            }
        }

        for (String mappedJdbcType : mappedJdbcTypes) {
            try {
                configuration.getTypeHandlerRegistry().register(Resources.classForName(mappedJdbcType));
            } catch (ClassNotFoundException e) {
                LOG.debug("Can not find the mapped jdbc type class " + mappedJdbcType);
            }
        }

        for (String mapper : mappers) {
            try {
                configuration.addMapper(Resources.classForName(mapper));
            } catch (ClassNotFoundException e) {
                LOG.debug("Can not find the mapper class " + mapper);
            }
        }
    }

    private Configuration setupConfiguration(Configuration configuration,
            MyBatisRuntimeConfig runtimeConfig,
            MyBatisDataSourceRuntimeConfig dataSourceRuntimeConfig,
            String dataSourceName) {
        TransactionFactory factory;
        String transactionFactory = dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.transactionFactory.isPresent()
                ? dataSourceRuntimeConfig.transactionFactory.get()
                : runtimeConfig.transactionFactory;
        if (transactionFactory.equals("MANAGED")) {
            factory = new ManagedTransactionFactory();
        } else {
            factory = new JdbcTransactionFactory();
        }

        configuration.setCacheEnabled(dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.cacheEnabled.isPresent()
                ? dataSourceRuntimeConfig.cacheEnabled.get()
                : runtimeConfig.cacheEnabled);
        configuration.setLazyLoadingEnabled(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.lazyLoadingEnabled.isPresent() ? dataSourceRuntimeConfig.lazyLoadingEnabled.get()
                        : runtimeConfig.lazyLoadingEnabled);
        configuration.setAggressiveLazyLoading(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.aggressiveLazyLoading.isPresent() ? dataSourceRuntimeConfig.aggressiveLazyLoading.get()
                        : runtimeConfig.aggressiveLazyLoading);
        configuration.setMultipleResultSetsEnabled(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.multipleResultSetsEnabled.isPresent()
                        ? dataSourceRuntimeConfig.multipleResultSetsEnabled.get()
                        : runtimeConfig.multipleResultSetsEnabled);
        configuration.setUseColumnLabel(dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.useColumnLabel.isPresent()
                ? dataSourceRuntimeConfig.useColumnLabel.get()
                : runtimeConfig.useColumnLabel);
        configuration.setUseGeneratedKeys(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.useGeneratedKeys.isPresent() ? dataSourceRuntimeConfig.useGeneratedKeys.get()
                        : runtimeConfig.useGeneratedKeys);
        configuration.setAutoMappingBehavior(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.autoMappingBehavior.isPresent() ? dataSourceRuntimeConfig.autoMappingBehavior.get()
                        : runtimeConfig.autoMappingBehavior);
        configuration.setAutoMappingUnknownColumnBehavior(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.autoMappingUnknownColumnBehavior.isPresent()
                        ? dataSourceRuntimeConfig.autoMappingUnknownColumnBehavior.get()
                        : runtimeConfig.autoMappingUnknownColumnBehavior);
        configuration.setDefaultExecutorType(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.defaultExecutorType.isPresent() ? dataSourceRuntimeConfig.defaultExecutorType.get()
                        : runtimeConfig.defaultExecutorType);
        if (dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.defaultStatementTimeout.isPresent()) {
            configuration.setDefaultStatementTimeout(dataSourceRuntimeConfig.defaultStatementTimeout.get());
        } else {
            runtimeConfig.defaultStatementTimeout.ifPresent(configuration::setDefaultStatementTimeout);
        }
        if (dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.defaultFetchSize.isPresent()) {
            configuration.setDefaultFetchSize(dataSourceRuntimeConfig.defaultFetchSize.get());
        } else {
            runtimeConfig.defaultFetchSize.ifPresent(configuration::setDefaultFetchSize);
        }
        if (dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.defaultResultSetType.isPresent()) {
            configuration.setDefaultResultSetType(dataSourceRuntimeConfig.defaultResultSetType.get());
        } else {
            runtimeConfig.defaultResultSetType.ifPresent(configuration::setDefaultResultSetType);
        }
        configuration.setSafeRowBoundsEnabled(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.safeRowBoundsEnabled.isPresent() ? dataSourceRuntimeConfig.safeRowBoundsEnabled.get()
                        : runtimeConfig.safeRowBoundsEnabled);
        configuration.setSafeResultHandlerEnabled(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.safeResultHandlerEnabled.isPresent()
                        ? dataSourceRuntimeConfig.safeResultHandlerEnabled.get()
                        : runtimeConfig.safeResultHandlerEnabled);
        configuration.setMapUnderscoreToCamelCase(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.mapUnderscoreToCamelCase.isPresent()
                        ? dataSourceRuntimeConfig.mapUnderscoreToCamelCase.get()
                        : runtimeConfig.mapUnderscoreToCamelCase);
        configuration.setLocalCacheScope(dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.localCacheScope.isPresent()
                ? dataSourceRuntimeConfig.localCacheScope.get()
                : runtimeConfig.localCacheScope);
        configuration.setJdbcTypeForNull(dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.jdbcTypeForNull.isPresent()
                ? dataSourceRuntimeConfig.jdbcTypeForNull.get()
                : runtimeConfig.jdbcTypeForNull);
        configuration.setLazyLoadTriggerMethods(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.lazyLoadTriggerMethods.isPresent()
                        ? dataSourceRuntimeConfig.lazyLoadTriggerMethods.get()
                        : runtimeConfig.lazyLoadTriggerMethods);
        try {
            String defaultScriptingLanguage = dataSourceRuntimeConfig != null &&
                    dataSourceRuntimeConfig.defaultScriptingLanguage.isPresent()
                            ? dataSourceRuntimeConfig.defaultScriptingLanguage.get()
                            : runtimeConfig.defaultScriptingLanguage;
            configuration.setDefaultScriptingLanguage(
                    (Class<? extends LanguageDriver>) Class.forName(defaultScriptingLanguage));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            String defaultEnumTypeHandler = dataSourceRuntimeConfig != null &&
                    dataSourceRuntimeConfig.defaultEnumTypeHandler.isPresent()
                            ? dataSourceRuntimeConfig.defaultEnumTypeHandler.get()
                            : runtimeConfig.defaultEnumTypeHandler;
            configuration.setDefaultEnumTypeHandler(
                    (Class<? extends TypeHandler>) Class.forName(defaultEnumTypeHandler));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        configuration.setCallSettersOnNulls(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.callSettersOnNulls.isPresent() ? dataSourceRuntimeConfig.callSettersOnNulls.get()
                        : runtimeConfig.callSettersOnNulls);
        configuration.setReturnInstanceForEmptyRow(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.returnInstanceForEmptyRow.isPresent()
                        ? dataSourceRuntimeConfig.returnInstanceForEmptyRow.get()
                        : runtimeConfig.returnInstanceForEmptyRow);
        if (dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.logPrefix.isPresent()) {
            configuration.setLogPrefix(dataSourceRuntimeConfig.logPrefix.get());
        } else {
            runtimeConfig.logPrefix.ifPresent(configuration::setLogPrefix);
        }

        Optional<String> optionalLogImpl = dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.logImpl.isPresent()
                ? dataSourceRuntimeConfig.logImpl
                : runtimeConfig.logImpl;
        optionalLogImpl.ifPresent(logImpl -> {
            try {
                configuration.setLogImpl((Class<? extends Log>) Class.forName(logImpl));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        String proxyFactory = dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.proxyFactory.isPresent()
                ? dataSourceRuntimeConfig.proxyFactory.get()
                : runtimeConfig.proxyFactory;
        if ("JAVASSIST".equals(proxyFactory)) {
            configuration.setProxyFactory(new JavassistProxyFactory());
        } else if ("CGLIB".equals(proxyFactory)) {
            configuration.setProxyFactory(new CglibProxyFactory());
        }

        Optional<String> optionalVfsImpl = dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.vfsImpl.isPresent()
                ? dataSourceRuntimeConfig.vfsImpl
                : runtimeConfig.vfsImpl;
        optionalVfsImpl.ifPresent(vfsImpl -> {
            try {
                configuration.setVfsImpl((Class<? extends VFS>) Class.forName(vfsImpl));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        configuration.setUseActualParamName(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.useActualParamName.isPresent() ? dataSourceRuntimeConfig.useActualParamName.get()
                        : runtimeConfig.useActualParamName);

        Optional<String> optionalConfigurationFactory = dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.configurationFactory.isPresent() ? dataSourceRuntimeConfig.configurationFactory
                        : runtimeConfig.configurationFactory;
        optionalConfigurationFactory.ifPresent(configurationFactory -> {
            try {
                configuration.setConfigurationFactory(Class.forName(configurationFactory));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        configuration.setShrinkWhitespacesInSql(dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.shrinkWhitespacesInSql.isPresent()
                        ? dataSourceRuntimeConfig.shrinkWhitespacesInSql.get()
                        : runtimeConfig.shrinkWhitespacesInSql);

        Optional<String> optionalDefaultSqlProviderType = dataSourceRuntimeConfig != null &&
                dataSourceRuntimeConfig.defaultSqlProviderType.isPresent() ? dataSourceRuntimeConfig.defaultSqlProviderType
                        : runtimeConfig.defaultSqlProviderType;
        optionalDefaultSqlProviderType.ifPresent(defaultSqlProviderType -> {
            try {
                configuration.setDefaultSqlProviderType(Class.forName(defaultSqlProviderType));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        String environment = dataSourceRuntimeConfig != null && dataSourceRuntimeConfig.environment.isPresent()
                ? dataSourceRuntimeConfig.environment.get()
                : runtimeConfig.environment;
        Environment.Builder environmentBuilder = new Environment.Builder(environment)
                .transactionFactory(factory)
                .dataSource(new QuarkusDataSource(dataSourceName));
        configuration.setEnvironment(environmentBuilder.build());

        return configuration;
    }



    public void runInitialSql(RuntimeValue<SqlSessionFactory> sqlSessionFactory, String sql) {
        try (SqlSession session = sqlSessionFactory.getValue().openSession()) {
            Connection conn = session.getConnection();
            Reader reader = Resources.getResourceAsReader(sql);
            ScriptRunner runner = new ScriptRunner(conn);
            runner.setLogWriter(null);
            runner.runScript(reader);
            reader.close();
        } catch (Exception e) {
            LOG.warn("Error executing SQL Script " + sql, e);
        }
    }
    */




