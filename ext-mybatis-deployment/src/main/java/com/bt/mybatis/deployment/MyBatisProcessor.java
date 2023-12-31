package com.bt.mybatis.deployment;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.bt.mybatis.runtime.MyBatisRecorder;
import com.bt.mybatis.runtime.MyConfig;
import com.bt.mybatis.runtime.bridge.QuarkusDataSource;
import com.bt.mybatis.runtime.bridge.XmlConfigurationFactory;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

public class MyBatisProcessor {

    private static final Logger LOG     = Logger.getLogger(MyBatisProcessor.class);
    private static final String FEATURE = "ext-mybatis";
    //private static final DotName MYBATIS_MAPPER = DotName.createSimple(Mapper.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void runtimeInitialzed(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit) {
        runtimeInit.produce(new RuntimeInitializedClassBuildItem(Log4jImpl.class.getName()));
    }

    @BuildStep
    void refProxyClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
                         BuildProducer<NativeImageProxyDefinitionBuildItem> proxyClass) {

        reflectiveClass.produce(ReflectiveClassBuildItem.builder(Executor.class).methods().build());
        var fieldIndo = ReflectiveClassBuildItem.builder(RawSqlSource.class,DynamicSqlSource.class,StaticSqlSource.class).fields().build();
        reflectiveClass.produce(fieldIndo);
        proxyClass.produce(new NativeImageProxyDefinitionBuildItem(Executor.class.getName()));

    }

    @BuildStep
    void addConfigurations(MyConfig config,
                           BuildProducer<ConfigurationMBI> configurations,
                           BuildProducer<MapperMBI> mappers,
                           BuildProducer<NativeImageResourceBuildItem> nativeResources,
                           BuildProducer<ReflectiveClassBuildItem> reflective,
                           BuildProducer<NativeImageProxyDefinitionBuildItem> proxy) throws Exception {

        var files = config.configFiles.split(",");
        //var defaultAlias =  new Configuration().getTypeAliasRegistry().getTypeAliases();
        var clsSet = new HashSet<Class>();


        var handlerSet  = new HashSet<Class>();

        for (var configFile : files) {
            var cfg = new XmlConfigurationFactory(configFile).createConfiguration();
            String xmlFolder = cfg.getVariables().getProperty("SqlXmlFolder");
            var sqlMaps = loadAllXmlFolder(xmlFolder,configFile);
            LOG.info("found sqlMapFiles , xmls "+ sqlMaps);


            var ds = (QuarkusDataSource) cfg.getEnvironment().getDataSource();

            var dsName = ds.getDataSourceName();

            var poSets = new HashSet<Class>();

            //cfg.getTypeHandlerRegistry().getTypeHandlers().forEach(
            //        System.out::println
            //);


            cfg.getTypeAliasRegistry().getTypeAliases().forEach((k,v)->{
                if(v.getName().startsWith("com.") && BaseTypeHandler.class.isAssignableFrom(v)){
                    handlerSet.add(v);
                }
            });


            var mapperListLog = new ArrayList<String>();

            for (var mapCls : cfg.getMapperRegistry().getMappers()) {

                if (!clsSet.add(mapCls)) {
                    LOG.info("=== Skip Exists Mapper Class :: " + mapCls.getName());
                    continue;
                }

                reflective.produce(ReflectiveClassBuildItem.builder(mapCls).methods().build());
                proxy.produce(new NativeImageProxyDefinitionBuildItem(mapCls.getName()));


                mappers.produce(new MapperMBI(DotName.createSimple(mapCls.getName()), dsName));
                for (var m : mapCls.getDeclaredMethods()) {

                    recursionParameterizedType(poSets, m.getGenericReturnType());
                    for (var param : m.getGenericParameterTypes()) {
                        recursionParameterizedType(poSets, param);
                    }
                }
                var poList = addSqlParamReflectiveClass(clsSet, reflective, poSets);
                mapperListLog.add(mapCls.getSimpleName()+"("+
                        poList.stream().map(full->full.substring(full.lastIndexOf('.')+1))
                                .collect(Collectors.joining(","))
                        +")");
            }
            LOG.info("=== [ "+mapperListLog.size()+" Mapper ] for "+configFile+" :: " + mapperListLog);

            configurations.produce(new ConfigurationMBI(configFile, dsName, sqlMaps));
        }

        if (handlerSet.size() > 0) {
            LOG.info("=== [ "+ handlerSet.size()+" CustomerHandler ] : " +
                    handlerSet.stream().map(Class::getSimpleName).collect(Collectors.joining(",")));
            reflective.produce(ReflectiveClassBuildItem.builder(handlerSet.toArray(new Class[] {})).methods().build());
        }

    }

    static List<String> loadAllXmlFolder(String folders,String configFile) throws IOException, URISyntaxException {
        List<String> maps = new ArrayList<>();
        for(var xmlFolder: folders.split(",")) {

            if (xmlFolder.charAt(0) == '/') {
                xmlFolder = xmlFolder.substring(1);
            }
            var folder = xmlFolder;

            URL resource = Resources.getResourceURL(folder);

            // jar:file:///Users/martin/Garden/wangyue/tech/shared/build/libs/shared-1.0.0.jar!/mapper-shared
            LOG.info("=== addConfigurations : " + configFile + " -> " + folder + ", url : " + resource);

            var uri = resource.toURI();
            if ("jar".equals(resource.getProtocol())) {
                try (var fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                    maps.addAll(walkXml(folder, fs.getPath(".")));
                }
            } else {
                maps.addAll(walkXml(folder, Paths.get(uri)));
            }
        }
        return maps;
    }
    static List<String> walkXml(String folder,Path path) throws IOException {
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .map(x -> folder + "/" + x.getName(x.getNameCount() - 1))
                .filter(it -> it.endsWith(".xml"))
                .collect(Collectors.toList());
    }

    static void recursionParameterizedType(Set<Class> total, Type t) {
        if (t instanceof Class) {
            total.add((Class) t);
        } else if (t instanceof ParameterizedType) {
            var pt = (ParameterizedType) t;
            recursionParameterizedType(total, pt.getRawType());
            for (var s : pt.getActualTypeArguments()) {
                recursionParameterizedType(total, s);
            }
        }
    }

    private static List<String> addSqlParamReflectiveClass(Set<Class> set, BuildProducer<ReflectiveClassBuildItem> reflective, Set<Class> poSet) {
        var list = new ArrayList<String>();
        for (var c : poSet) {
            if (set.add(c)
                    && !c.isPrimitive()
                    && !c.isArray()
                    && !c.isEnum()
                    && Object.class != c
                    && Boolean.class != c
                    && !CharSequence.class.isAssignableFrom(c)
                    && !Number.class.isAssignableFrom(c)
                    && !Iterable.class.isAssignableFrom(c)
                    && !Map.class.isAssignableFrom(c)
                    && !RowBounds.class.isAssignableFrom(c)
            ) {
                list.add(c.getName());
                reflective.produce(ReflectiveClassBuildItem.builder(c).methods().build());
            }
        }
        return list;
    }

    @BuildStep
    NativeImageResourceBuildItem nativeImageResourceBuildItem(List<ConfigurationMBI> configurationMBIS) {
        List<String> resources = new ArrayList<>();
        configurationMBIS.forEach(it -> resources.addAll(it.getMapperXml()));
        LOG.info("=== [ "+resources.size()+" NativeImageResource ] : " + resources);
        return new NativeImageResourceBuildItem(resources);
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void generateSqlSessionFactorys(List<ConfigurationMBI> configurationMBIS,
                                    BuildProducer<SqlSessionMBI> sqlSessionMBIBuildProducer,
                                    BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
                                    MyBatisRecorder recorder) throws Exception {
        for (var cbi : configurationMBIS) {
            var factoryRuntime = recorder.createSqlSessionFactory(
                    new XmlConfigurationFactory(cbi.getMybatisConfigFile()), cbi.getMapperXml());
            var sqlSessionMBI = new SqlSessionMBI(factoryRuntime
                    , recorder.createSqlSessionManager(factoryRuntime)
                    , cbi.getDataSourceName(), cbi.isDefaultDs());
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(SqlSessionFactory.class)
                    .scope(ApplicationScoped.class)//.scope(Singleton.class)
                    .unremovable()
                    .supplier(recorder.MyBatisSqlSessionFactorySupplier(sqlSessionMBI.getSqlSessionFactory()));
            String dataSourceName = sqlSessionMBI.getDataSourceName();
            if (!sqlSessionMBI.isDefaultDataSource()) {
                configurator.defaultBean();
                configurator.addQualifier().annotation(Named.class).addValue("value", dataSourceName).done();
            }

            LOG.info("=== STATIC_INIT CDI SqlSessionFactory :" + sqlSessionMBI);
            sqlSessionMBIBuildProducer.produce(sqlSessionMBI);
            syntheticBeanBuildItemBuildProducer.produce(configurator.done());
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void generateMapperBeans(MyBatisRecorder recorder,
                             List<MapperMBI> mapperMBIS,
                             //List<MyBatisMappedTypeBuildItem> myBatisMappedTypesBuildItems,
                             //List<MyBatisMappedJdbcTypeBuildItem> myBatisMappedJdbcTypesBuildItems,
                             List<SqlSessionMBI> sqlSessionFacItems,
                             BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        var dataSourceToSessionManagers = sqlSessionFacItems.stream()
                .collect(Collectors.toMap(SqlSessionMBI::getDataSourceName, SqlSessionMBI::getSqlSessionManager));


        var ds = "";
        for (MapperMBI i : mapperMBIS) {
            var sqlSessionManager = dataSourceToSessionManagers.get(i.getDataSourceName());
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(i.getMapperName())
                    .scope(ApplicationScoped.class)//.scope(Singleton.class)
                    .setRuntimeInit()
                    .unremovable()
                    .supplier(recorder.MyBatisMapperSupplier(i.getMapperName().toString(),
                            sqlSessionManager));
            syntheticBeanBuildItemBuildProducer.produce(configurator.done());
            ds = i.getDataSourceName();
        }
        LOG.debug("=== [ "+mapperMBIS.size()+" CDI Mapper -> "+ ds +" ] : " + mapperMBIS.stream()
                .map(mbi->mbi.getMapperName().withoutPackagePrefix()).collect(Collectors.joining(",")));
    }

    public static void main(String[] args) throws MalformedURLException {
        var url = new URL("jar:file:///Users/martin/Garden/wangyue/tech/shared/build/libs/shared-1.0.0.jar!/mapper-shared");
        System.out.println(url.getProtocol());
    }
}
