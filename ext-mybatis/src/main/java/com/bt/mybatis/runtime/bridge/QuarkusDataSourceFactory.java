package com.bt.mybatis.runtime.bridge;

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.DataSourceFactory;
import org.jboss.logging.Logger;

public class QuarkusDataSourceFactory implements DataSourceFactory {

    public static final String DEFAULT_DS_NAME = "<default>";
    private static final    Logger LOG             = Logger.getLogger(QuarkusDataSourceFactory.class);


    private QuarkusDataSource dataSource;

    public QuarkusDataSourceFactory() {
    }

    @Override
    public void setProperties(Properties properties) {
        if (dataSource == null) {
            var dsName = properties.getProperty("db", DEFAULT_DS_NAME);
            LOG.info("Bind QuarkusDataSource :" + dsName +", with properties -> "+properties);
            dataSource = new QuarkusDataSource(dsName);
        }
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }
}